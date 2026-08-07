package com.mine.geometry_node.client.ui.editor.asset.image;

import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.BitmapFactory;
import icyllis.modernui.view.View;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ImageThumbnailCache {
    private static final int THUMBNAIL_SIZE = 128;
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final long MAX_SOURCE_BYTES = 32L * 1024L * 1024L;
    private static final long MAX_SOURCE_PIXELS = 16_777_216L;
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "GeometryNode-ImageThumbnail");
        thread.setDaemon(true);
        return thread;
    });

    private ImageThumbnailCache() {
    }

    static Bitmap get(File file, View observer) {
        if (file == null || !file.isFile()) return null;
        String key = key(file);
        Entry entry;
        synchronized (CACHE) {
            entry = CACHE.get(key);
            if (entry == null) {
                entry = new Entry(file);
                CACHE.put(key, entry);
                trimLocked();
            }
        }
        entry.observe(observer);
        entry.start();
        return entry.thumbnail;
    }

    static void preload(File file, View observer) {
        get(file, observer);
    }

    static void unobserve(File file, View observer) {
        if (file == null || observer == null) return;
        Entry entry;
        synchronized (CACHE) {
            entry = CACHE.get(key(file));
        }
        if (entry != null) entry.unobserve(observer);
    }

    private static Bitmap decodeThumbnail(File file) throws IOException {
        long fileSize = file.length();
        if (fileSize <= 0 || fileSize > MAX_SOURCE_BYTES) {
            throw new IOException("unsupported image file size: " + fileSize);
        }

        BitmapFactory.Options info = new BitmapFactory.Options();
        BitmapFactory.decodeFileInfo(file, info);
        if (info.outWidth <= 0 || info.outHeight <= 0
                || (long) info.outWidth * info.outHeight > MAX_SOURCE_PIXELS) {
            throw new IOException("unsupported image dimensions: " + info.outWidth + "x" + info.outHeight);
        }

        try (Bitmap source = BitmapFactory.decodeFile(file)) {
            int sourceWidth = source.getWidth();
            int sourceHeight = source.getHeight();
            float scale = Math.min(1.0f, THUMBNAIL_SIZE / (float) Math.max(sourceWidth, sourceHeight));
            int width = Math.max(1, Math.round(sourceWidth * scale));
            int height = Math.max(1, Math.round(sourceHeight * scale));
            Bitmap thumbnail = Bitmap.createBitmap(width, height, Bitmap.Format.RGBA_8888);
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                int sourceY = Math.min(sourceHeight - 1, (int) ((long) y * sourceHeight / height));
                for (int x = 0; x < width; x++) {
                    int sourceX = Math.min(sourceWidth - 1, (int) ((long) x * sourceWidth / width));
                    pixels[y * width + x] = source.getPixelARGB(sourceX, sourceY);
                }
            }
            thumbnail.setPixels(pixels, 0, width, 0, 0, width, height);
            thumbnail.setImmutable();
            return thumbnail;
        }
    }

    private static String key(File file) {
        try {
            return file.getCanonicalPath() + '|' + file.lastModified() + '|' + file.length();
        } catch (IOException ignored) {
            return file.getAbsolutePath() + '|' + file.lastModified() + '|' + file.length();
        }
    }

    private static void trim() {
        synchronized (CACHE) {
            trimLocked();
        }
    }

    private static void trimLocked() {
        if (CACHE.size() <= MAX_CACHE_ENTRIES) return;
        Iterator<Map.Entry<String, Entry>> iterator = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_CACHE_ENTRIES && iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (!entry.canEvict()) continue;
            iterator.remove();
            entry.close();
        }
    }

    private static final class Entry {
        private final File file;
        private final List<WeakReference<View>> observers = new ArrayList<>();
        private volatile Bitmap thumbnail;
        private boolean started;
        private volatile boolean failed;

        private Entry(File file) {
            this.file = file;
        }

        private void observe(View view) {
            if (view == null) return;
            synchronized (observers) {
                for (WeakReference<View> reference : observers) {
                    if (reference.get() == view) return;
                }
                observers.add(new WeakReference<>(view));
            }
        }

        private void unobserve(View view) {
            synchronized (observers) {
                observers.removeIf(reference -> {
                    View current = reference.get();
                    return current == null || current == view;
                });
            }
            trim();
        }

        private boolean hasObservers() {
            synchronized (observers) {
                observers.removeIf(reference -> reference.get() == null);
                return !observers.isEmpty();
            }
        }

        private synchronized void start() {
            if (started || thumbnail != null || failed) return;
            started = true;
            EXECUTOR.execute(() -> {
                try {
                    thumbnail = decodeThumbnail(file);
                } catch (Exception ignored) {
                    failed = true;
                }
                notifyObservers();
                trim();
            });
        }

        private boolean canEvict() {
            return (thumbnail != null || failed) && !hasObservers();
        }

        private void notifyObservers() {
            synchronized (observers) {
                observers.removeIf(reference -> reference.get() == null);
                for (WeakReference<View> reference : observers) {
                    View view = reference.get();
                    if (view != null) view.post(view::invalidate);
                }
            }
        }

        private void close() {
            Bitmap bitmap = thumbnail;
            thumbnail = null;
            if (bitmap != null) bitmap.close();
        }
    }
}
