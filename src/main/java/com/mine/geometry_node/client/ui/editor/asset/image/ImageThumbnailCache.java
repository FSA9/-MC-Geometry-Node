package com.mine.geometry_node.client.ui.editor.asset.image;

import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.BitmapFactory;
import icyllis.modernui.view.View;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class ImageThumbnailCache {
    private static final int THUMBNAIL_SIZE = 128;
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final int MAX_PENDING_JOBS = 128;
    private static final long MAX_SOURCE_BYTES = 32L * 1024L * 1024L;
    private static final long MAX_SOURCE_PIXELS = 16_777_216L;
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            2, 2, 30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(MAX_PENDING_JOBS), task -> {
        Thread thread = new Thread(task, "GeometryNode-ImageThumbnail");
        thread.setDaemon(true);
        return thread;
    });

    static {
        EXECUTOR.allowCoreThreadTimeOut(true);
    }

    private ImageThumbnailCache() {
    }

    static Subscription subscribe(File file, View view) {
        if (file == null || view == null || !file.isFile()) return Subscription.EMPTY;
        String key = key(file);
        Entry entry;
        Observer observer = new Observer(view);
        synchronized (CACHE) {
            entry = CACHE.get(key);
            if (entry == null) {
                entry = new Entry(key, canonicalPath(file), file);
                CACHE.put(key, entry);
            }
            entry.addObserver(observer);
            trimLocked();
        }
        entry.start();
        return new Subscription(entry, observer);
    }

    static void invalidate(File file) {
        if (file == null) return;
        String path = canonicalPath(file);
        synchronized (CACHE) {
            Iterator<Map.Entry<String, Entry>> iterator = CACHE.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry entry = iterator.next().getValue();
                if (!entry.path.equals(path)) continue;
                iterator.remove();
                entry.evict();
            }
        }
        EXECUTOR.purge();
    }

    static void invalidateUnder(File file) {
        if (file == null) return;
        String root = canonicalPath(file);
        String prefix = root.endsWith(File.separator) ? root : root + File.separator;
        synchronized (CACHE) {
            Iterator<Map.Entry<String, Entry>> iterator = CACHE.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry entry = iterator.next().getValue();
                if (!entry.path.equals(root) && !entry.path.startsWith(prefix)) continue;
                iterator.remove();
                entry.evict();
            }
        }
        EXECUTOR.purge();
    }

    static void clear() {
        synchronized (CACHE) {
            for (Entry entry : CACHE.values()) entry.evict();
            CACHE.clear();
        }
        EXECUTOR.purge();
    }

    private static void release(Entry entry, Observer observer) {
        synchronized (CACHE) {
            entry.removeObserver(observer);
            if (!entry.hasObservers() && !entry.isResolved()) {
                CACHE.remove(entry.key, entry);
                entry.evict();
            }
            trimLocked();
        }
        EXECUTOR.purge();
    }

    private static Bitmap decodeThumbnail(File file) throws IOException {
        checkInterrupted();
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
            checkInterrupted();
            int sourceWidth = source.getWidth();
            int sourceHeight = source.getHeight();
            float scale = Math.min(1.0f, THUMBNAIL_SIZE / (float) Math.max(sourceWidth, sourceHeight));
            int width = Math.max(1, Math.round(sourceWidth * scale));
            int height = Math.max(1, Math.round(sourceHeight * scale));
            Bitmap thumbnail = Bitmap.createBitmap(width, height, Bitmap.Format.RGBA_8888);
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                checkInterrupted();
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

    private static void trimLocked() {
        if (CACHE.size() <= MAX_CACHE_ENTRIES) return;
        Iterator<Map.Entry<String, Entry>> iterator = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_CACHE_ENTRIES && iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (entry.hasObservers()) continue;
            iterator.remove();
            entry.evict();
        }
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("thumbnail decode cancelled");
        }
    }

    private static String key(File file) {
        return canonicalPath(file) + '|' + file.lastModified() + '|' + file.length();
    }

    private static String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ignored) {
            return file.getAbsolutePath();
        }
    }

    static final class Subscription implements AutoCloseable {
        private static final Subscription EMPTY = new Subscription(null, null);
        private Entry mEntry;
        private Observer mObserver;

        private Subscription(Entry entry, Observer observer) {
            mEntry = entry;
            mObserver = observer;
        }

        Bitmap bitmap() {
            Entry entry = mEntry;
            if (entry == null) return null;
            entry.start();
            return entry.thumbnail;
        }

        @Override
        public void close() {
            Entry entry = mEntry;
            Observer observer = mObserver;
            mEntry = null;
            mObserver = null;
            if (entry != null && observer != null) release(entry, observer);
        }
    }

    private static final class Observer {
        private final WeakReference<View> view;

        private Observer(View view) {
            this.view = new WeakReference<>(view);
        }
    }

    private static final class Entry {
        private final String key;
        private final String path;
        private final File file;
        private final List<Observer> observers = new ArrayList<>();
        private volatile Bitmap thumbnail;
        private Future<?> future;
        private boolean started;
        private boolean failed;
        private boolean evicted;

        private Entry(String key, String path, File file) {
            this.key = key;
            this.path = path;
            this.file = file;
        }

        private synchronized void addObserver(Observer observer) {
            observers.add(observer);
        }

        private synchronized void removeObserver(Observer observer) {
            observers.remove(observer);
            observers.removeIf(candidate -> candidate.view.get() == null);
        }

        private synchronized boolean hasObservers() {
            observers.removeIf(observer -> observer.view.get() == null);
            return !observers.isEmpty();
        }

        private synchronized boolean isResolved() {
            return thumbnail != null || failed;
        }

        private synchronized void start() {
            if (started || evicted || thumbnail != null || failed) return;
            started = true;
            try {
                future = EXECUTOR.submit(this::decode);
            } catch (RejectedExecutionException e) {
                started = false;
                future = null;
                notifyObservers();
            }
        }

        private void decode() {
            Bitmap decoded = null;
            try {
                decoded = decodeThumbnail(file);
            } catch (Exception ignored) {
            }
            synchronized (this) {
                if (evicted) {
                    if (decoded != null) decoded.close();
                    return;
                }
                thumbnail = decoded;
                failed = decoded == null;
                future = null;
            }
            notifyObservers();
        }

        private void notifyObservers() {
            List<View> views = new ArrayList<>();
            synchronized (this) {
                observers.removeIf(observer -> observer.view.get() == null);
                for (Observer observer : observers) {
                    View view = observer.view.get();
                    if (view != null) views.add(view);
                }
            }
            for (View view : views) view.post(view::invalidate);
        }

        private synchronized void evict() {
            if (evicted) return;
            evicted = true;
            observers.clear();
            if (future != null) {
                future.cancel(true);
                future = null;
            }
            Bitmap bitmap = thumbnail;
            thumbnail = null;
            if (bitmap != null) bitmap.close();
        }
    }
}
