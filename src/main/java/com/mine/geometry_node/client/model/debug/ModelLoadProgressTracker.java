package com.mine.geometry_node.client.model.debug;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe, transient progress shared by worker stages and the client HUD. */
public final class ModelLoadProgressTracker {
    private static final Map<String, Entry> ACTIVE = new ConcurrentHashMap<>();

    private ModelLoadProgressTracker() {}

    public static String key(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    public static void begin(Path path) {
        String key = key(path);
        ACTIVE.put(key, new Entry(label(path), "Queued", 0.02));
    }

    public static void begin(String key) {
        int slash = Math.max(key.lastIndexOf('/'), key.lastIndexOf('\\'));
        ACTIVE.put(key, new Entry(slash < 0 ? key : key.substring(slash + 1), "Queued", 0.02));
    }

    public static void update(Path path, String stage, double progress) {
        update(key(path), label(path), stage, progress);
    }

    public static void update(String key, String stage, double progress) {
        int slash = Math.max(key.lastIndexOf('/'), key.lastIndexOf('\\'));
        update(key, slash < 0 ? key : key.substring(slash + 1), stage, progress);
    }

    private static void update(String key, String label, String stage, double progress) {
        ACTIVE.computeIfPresent(key, (ignored, current) ->
                new Entry(label, stage, Math.max(current.progress(), Math.clamp(progress, 0.0, 1.0))));
    }

    public static void finish(Path path) { ACTIVE.remove(key(path)); }
    public static void finish(String key) { ACTIVE.remove(key); }
    public static void clear() { ACTIVE.clear(); }

    public static List<Snapshot> snapshot() {
        List<Snapshot> result = new ArrayList<>(ACTIVE.size());
        ACTIVE.forEach((key, entry) -> result.add(new Snapshot(key, entry.label(), entry.stage(), entry.progress())));
        result.sort(Comparator.comparing(Snapshot::label).thenComparing(Snapshot::key));
        return List.copyOf(result);
    }

    private static String label(Path path) {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    private record Entry(String label, String stage, double progress) {}
    public record Snapshot(String key, String label, String stage, double progress) {}
}
