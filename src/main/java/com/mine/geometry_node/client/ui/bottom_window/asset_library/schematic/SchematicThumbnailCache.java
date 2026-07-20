package com.mine.geometry_node.client.ui.bottom_window.asset_library.schematic;

import icyllis.modernui.view.View;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SchematicThumbnailCache {
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
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
        if (CACHE.size() > MAX_CACHE_ENTRIES) {
            CACHE.clear();
        }

        String key = key(file);
        Entry entry = CACHE.get(key);
        if (entry != null) {
            entry.observe(observer);
            return entry.thumbnail;
        }

        Entry created = new Entry();
        created.observe(observer);
        Entry existing = CACHE.putIfAbsent(key, created);
        if (existing != null) {
            existing.observe(observer);
            return existing.thumbnail;
        }

        EXECUTOR.submit(() -> {
            SchematicThumbnail thumbnail;
            try {
                thumbnail = SchematicThumbnailReader.read(file);
            } catch (Exception e) {
                thumbnail = SchematicThumbnail.error(e.getMessage());
            }
            created.thumbnail = thumbnail;
            created.notifyObservers();
        });
        return null;
    }

    private static String key(File file) {
        try {
            return file.getCanonicalPath() + "|" + file.lastModified() + "|" + file.length();
        } catch (Exception ignored) {
            return file.getAbsolutePath() + "|" + file.lastModified() + "|" + file.length();
        }
    }

    private static final class Entry {
        private final List<WeakReference<View>> observers = new ArrayList<>();
        private volatile SchematicThumbnail thumbnail;

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
