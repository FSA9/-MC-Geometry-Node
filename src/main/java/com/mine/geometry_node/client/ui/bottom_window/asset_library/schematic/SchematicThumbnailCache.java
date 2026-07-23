package com.mine.geometry_node.client.ui.bottom_window.asset_library.schematic;

import icyllis.modernui.view.View;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class SchematicThumbnailCache {
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GeometryNode-SchematicThumbnail");
        thread.setDaemon(true);
        return thread;
    });

    private SchematicThumbnailCache() {
    }

    static SchematicThumbnail get(File file, View observer) {
        if (file == null || !file.isFile()) {
            return SchematicThumbnail.error("missing file");
        }

        String key = key(file);
        Entry entry;
        synchronized (CACHE) {
            entry = CACHE.get(key);
            if (entry == null) {
                entry = new Entry(key, file);
                CACHE.put(key, entry);
                trimCacheLocked();
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
        if (file == null || observer == null) {
            return;
        }
        String key = key(file);
        Entry entry;
        synchronized (CACHE) {
            entry = CACHE.get(key);
        }
        if (entry == null) {
            return;
        }

        entry.unobserve(observer);
        if (entry.cancelIfUnobserved()) {
            synchronized (CACHE) {
                if (CACHE.get(key) == entry) {
                    CACHE.remove(key);
                }
            }
        }
    }

    private static String key(File file) {
        try {
            return file.getCanonicalPath() + "|" + file.lastModified() + "|" + file.length();
        } catch (Exception ignored) {
            return file.getAbsolutePath() + "|" + file.lastModified() + "|" + file.length();
        }
    }

    private static void removeIfCurrent(String key, Entry entry) {
        synchronized (CACHE) {
            if (CACHE.get(key) == entry) {
                CACHE.remove(key);
            }
        }
    }

    private static void trimCache() {
        synchronized (CACHE) {
            trimCacheLocked();
        }
    }

    private static void trimCacheLocked() {
        if (CACHE.size() <= MAX_CACHE_ENTRIES) {
            return;
        }

        Iterator<Map.Entry<String, Entry>> iterator = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_CACHE_ENTRIES && iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (entry.canEvict()) {
                iterator.remove();
            }
        }
    }

    private static final class Entry {
        private final String key;
        private final File file;
        private final List<WeakReference<View>> observers = new ArrayList<>();
        private volatile SchematicThumbnail thumbnail;
        private Future<?> future;

        private Entry(String key, File file) {
            this.key = key;
            this.file = file;
        }

        private void observe(View view) {
            if (view == null || thumbnail != null) {
                return;
            }
            synchronized (observers) {
                for (WeakReference<View> ref : observers) {
                    if (ref.get() == view) {
                        return;
                    }
                }
                observers.add(new WeakReference<>(view));
            }
        }

        private void unobserve(View view) {
            synchronized (observers) {
                observers.removeIf(ref -> {
                    View observed = ref.get();
                    return observed == null || observed == view;
                });
            }
        }

        private boolean hasObservers() {
            synchronized (observers) {
                observers.removeIf(ref -> ref.get() == null);
                return !observers.isEmpty();
            }
        }

        private boolean canEvict() {
            return thumbnail != null && !hasObservers();
        }

        private synchronized void start() {
            if (thumbnail != null || future != null) {
                return;
            }
            future = EXECUTOR.submit(() -> {
                if (!hasObservers()) {
                    removeIfCurrent(key, this);
                    return;
                }

                SchematicThumbnail loaded;
                try {
                    loaded = SchematicThumbnailReader.read(file);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    removeIfCurrent(key, this);
                    return;
                } catch (Exception e) {
                    if (Thread.currentThread().isInterrupted()) {
                        removeIfCurrent(key, this);
                        return;
                    }
                    loaded = SchematicThumbnail.error(e.getMessage());
                }

                thumbnail = loaded;
                notifyObservers();
                trimCache();
            });
        }

        private synchronized boolean cancelIfUnobserved() {
            if (thumbnail != null || hasObservers()) {
                return false;
            }
            if (future != null) {
                future.cancel(true);
            }
            return true;
        }

        private void notifyObservers() {
            synchronized (observers) {
                Iterator<WeakReference<View>> iterator = observers.iterator();
                while (iterator.hasNext()) {
                    View view = iterator.next().get();
                    iterator.remove();
                    if (view != null) {
                        view.post(view::invalidate);
                    }
                }
            }
        }
    }
}
