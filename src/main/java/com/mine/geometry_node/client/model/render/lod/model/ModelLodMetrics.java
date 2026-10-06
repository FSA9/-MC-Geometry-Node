package com.mine.geometry_node.client.model.render.lod.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Render-thread counters; models and their selected objects have separate units. */
public final class ModelLodMetrics {
    public enum Pass { MAIN, SHADOW }

    private final EnumMap<Pass, Counters> totals = counters();
    private final EnumMap<Pass, Counters> current = counters();
    private final EnumMap<Pass, Counters> completed = counters();
    private long frame = -1;
    private long completedFrame = -1;
    private long completedFrames;
    private boolean frameOpen;
    private LoadedCounts loadedCounts = new LoadedCounts(0, 0);

    public record LoadedCounts(long instances, long objects) {}

    public void setLoadedCounts(long instances, long objects) {
        if (instances < 0 || objects < 0) throw new IllegalArgumentException("loaded counts must be nonnegative");
        if (loadedCounts.instances != instances || loadedCounts.objects != objects)
            loadedCounts = new LoadedCounts(instances, objects);
    }

    public LoadedCounts loadedCounts() { return loadedCounts; }

    public void beginFrame(long frame) {
        if (frame < 0) throw new IllegalArgumentException("frame must be nonnegative");
        if (frameOpen && this.frame == frame) return;
        if (frameOpen) endFrame();
        this.frame = frame;
        frameOpen = true;
        current.values().forEach(Counters::reset);
    }

    public void endFrame() {
        if (!frameOpen) return;
        for (Pass pass : Pass.values()) completed.get(pass).copyFrom(current.get(pass));
        completedFrame = frame;
        completedFrames++;
        frameOpen = false;
    }

    /** Record once for an instance with at least one submitted object in this pass. */
    public void recordModel(Pass pass) {
        Objects.requireNonNull(pass, "pass");
        totals.get(pass).models++;
        if (frameOpen) current.get(pass).models++;
    }

    /** Record once per object, including full geometry used during upload fallback. */
    public void record(Pass pass, int requestedLevel, int effectiveLevel,
                       long sourceTriangles, long selectedTriangles, boolean uploadFallback) {
        record(pass, requestedLevel, effectiveLevel, sourceTriangles, selectedTriangles, uploadFallback, false);
    }

    public void record(Pass pass, int requestedLevel, int effectiveLevel,
                       long sourceTriangles, long selectedTriangles, boolean uploadFallback, boolean uploadDeferred) {
        Objects.requireNonNull(pass, "pass");
        ModelLodPolicy.requireLevel(requestedLevel);
        ModelLodPolicy.requireLevel(effectiveLevel);
        if (sourceTriangles < 0 || selectedTriangles < 0 || selectedTriangles > sourceTriangles)
            throw new IllegalArgumentException("LOD triangle counts must satisfy 0 <= selected <= source");
        if (uploadDeferred && (selectedTriangles != 0 || uploadFallback))
            throw new IllegalArgumentException("deferred objects cannot draw triangles or use an immediate fallback");
        totals.get(pass).record(requestedLevel, effectiveLevel, sourceTriangles, selectedTriangles, uploadFallback, uploadDeferred);
        if (frameOpen)
            current.get(pass).record(requestedLevel, effectiveLevel, sourceTriangles, selectedTriangles, uploadFallback, uploadDeferred);
    }

    public void reset() {
        totals.values().forEach(Counters::reset);
        current.values().forEach(Counters::reset);
        completed.values().forEach(Counters::reset);
        frame = completedFrame = -1;
        completedFrames = 0;
        frameOpen = false;
        loadedCounts = new LoadedCounts(0, 0);
    }

    public Map<String, Object> report() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("completedFrames", completedFrames);
        result.put("frame", completedFrame);
        result.put("selectionUnit", "object");
        result.put("triangleScope", "opaque_and_mask");
        result.put("loadedInstances", loadedCounts.instances());
        result.put("loadedObjects", loadedCounts.objects());
        for (Pass pass : Pass.values()) {
            Map<String, Object> passReport = new LinkedHashMap<>();
            passReport.put("total", totals.get(pass).report());
            passReport.put("lastFrame", completed.get(pass).report());
            result.put(pass.name(), Map.copyOf(passReport));
        }
        return Map.copyOf(result);
    }

    public record HudSnapshot(long instances, long objects, long sourceTriangles, long selectedTriangles,
                              int minLevel, int maxLevel, long pendingObjects,
                              long level0Objects, long level1Objects, long level2Objects, long level3Objects) {}

    /** Last completed frame only; no report maps or percentile sorting. */
    public HudSnapshot hudSnapshot(Pass pass) {
        Counters values = completed.get(pass);
        long objects = 0, source = 0, selected = 0;
        int min = -1, max = -1;
        for (int level = 0; level < ModelLodPolicy.LEVEL_COUNT; level++) {
            objects += values.objects[level];
            source += values.sources[level];
            selected += values.selected[level];
            if (values.objects[level] > 0) { if (min < 0) min = level; max = level; }
        }
        return new HudSnapshot(values.models, objects, source, selected, min, max, values.pendingObjects,
                values.objects[0], values.objects[1], values.objects[2], values.objects[3]);
    }

    private static EnumMap<Pass, Counters> counters() {
        EnumMap<Pass, Counters> result = new EnumMap<>(Pass.class);
        for (Pass pass : Pass.values()) result.put(pass, new Counters());
        return result;
    }

    private static final class Counters {
        private final long[] requested = new long[ModelLodPolicy.LEVEL_COUNT];
        private final long[] objects = new long[ModelLodPolicy.LEVEL_COUNT];
        private final long[] sources = new long[ModelLodPolicy.LEVEL_COUNT];
        private final long[] selected = new long[ModelLodPolicy.LEVEL_COUNT];
        private long uploadFallbacks;
        private long models, pendingObjects, pendingSourceTriangles;
        private int lastRequestedLevel = -1;
        private int lastEffectiveLevel = -1;

        private void record(int requestedLevel, int effectiveLevel, long sourceTriangles,
                            long selectedTriangles, boolean uploadFallback, boolean uploadDeferred) {
            requested[requestedLevel]++;
            if (uploadDeferred) {
                pendingObjects++;
                pendingSourceTriangles += sourceTriangles;
            } else {
                objects[effectiveLevel]++;
                sources[effectiveLevel] += sourceTriangles;
                selected[effectiveLevel] += selectedTriangles;
            }
            if (uploadFallback) uploadFallbacks++;
            lastRequestedLevel = requestedLevel;
            lastEffectiveLevel = uploadDeferred ? -1 : effectiveLevel;
        }

        private void reset() {
            java.util.Arrays.fill(requested, 0);
            java.util.Arrays.fill(objects, 0);
            java.util.Arrays.fill(sources, 0);
            java.util.Arrays.fill(selected, 0);
            models = uploadFallbacks = pendingObjects = pendingSourceTriangles = 0;
            lastRequestedLevel = lastEffectiveLevel = -1;
        }

        private void copyFrom(Counters source) {
            System.arraycopy(source.requested, 0, requested, 0, requested.length);
            System.arraycopy(source.objects, 0, objects, 0, objects.length);
            System.arraycopy(source.sources, 0, sources, 0, sources.length);
            System.arraycopy(source.selected, 0, selected, 0, selected.length);
            uploadFallbacks = source.uploadFallbacks;
            models = source.models;
            pendingObjects = source.pendingObjects;
            pendingSourceTriangles = source.pendingSourceTriangles;
            lastRequestedLevel = source.lastRequestedLevel;
            lastEffectiveLevel = source.lastEffectiveLevel;
        }

        private Map<String, Object> report() {
            List<Map<String, Object>> levels = new ArrayList<>(ModelLodPolicy.LEVEL_COUNT);
            long totalObjects = 0, totalSource = 0, totalSelected = 0;
            for (int level = 0; level < ModelLodPolicy.LEVEL_COUNT; level++) {
                levels.add(Map.of("level", level, "requestedObjects", requested[level],
                        "objects", objects[level], "sourceTriangles", sources[level],
                        "selectedTriangles", selected[level]));
                totalObjects += objects[level];
                totalSource += sources[level];
                totalSelected += selected[level];
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("instances", models);
            result.put("objects", totalObjects);
            result.put("sourceTriangles", totalSource);
            result.put("selectedTriangles", totalSelected);
            result.put("uploadFallbacks", uploadFallbacks);
            result.put("pendingObjects", pendingObjects);
            result.put("pendingSourceTriangles", pendingSourceTriangles);
            result.put("lastRequestedLevel", lastRequestedLevel);
            result.put("lastEffectiveLevel", lastEffectiveLevel);
            result.put("levels", List.copyOf(levels));
            return Map.copyOf(result);
        }
    }
}
