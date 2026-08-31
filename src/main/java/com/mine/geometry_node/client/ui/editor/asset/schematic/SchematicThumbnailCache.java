package com.mine.geometry_node.client.ui.editor.asset.schematic;

import com.mine.geometry_node.core.engine.system.asset.preview.generator.schematic.SchematicThumbnail;
import com.mine.geometry_node.core.engine.system.asset.preview.generator.schematic.SchematicThumbnailReader;
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

    static Subscription subscribe(File file, View view) {
        if (file == null || view == null || !file.isFile()) return Subscription.EMPTY;
        String path = canonicalPath(file);
        String key = key(path, file);
        Entry entry;
        Observer observer = new Observer(view);
        synchronized (CACHE) {
            entry = CACHE.get(key);
            if (entry == null) {
                entry = new Entry(key, path, file);
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
    }

    static void clear() {
        synchronized (CACHE) {
            for (Entry entry : CACHE.values()) entry.evict();
            CACHE.clear();
        }
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
    }

    private static void removeIfCurrent(Entry entry) {
        synchronized (CACHE) {
            if (CACHE.remove(entry.key, entry)) entry.evict();
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

    private static String key(String path, File file) {
        return path + '|' + file.lastModified() + '|' + file.length();
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

        SchematicThumbnail thumbnail() {
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
        private volatile SchematicThumbnail thumbnail;
        private Future<?> future;
        private boolean started;
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
            return thumbnail != null;
        }

        private synchronized void start() {
            if (started || evicted || thumbnail != null) return;
            started = true;
            future = EXECUTOR.submit(this::decode);
        }

        private void decode() {
            if (!hasObservers()) {
                removeIfCurrent(this);
                return;
            }

            SchematicThumbnail decoded;
            try {
                decoded = SchematicThumbnailReader.read(file.toPath());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                removeIfCurrent(this);
                return;
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    removeIfCurrent(this);
                    return;
                }
                decoded = SchematicThumbnail.error(e.getMessage());
            }

            synchronized (this) {
                if (evicted) return;
                thumbnail = decoded;
                future = null;
            }
            notifyObservers();
            synchronized (CACHE) {
                trimLocked();
            }
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
            thumbnail = null;
        }
    }
}
