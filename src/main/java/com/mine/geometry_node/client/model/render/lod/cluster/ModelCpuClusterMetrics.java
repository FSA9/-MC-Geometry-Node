package com.mine.geometry_node.client.model.render.lod.cluster;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Render-thread counters for CPU cluster hierarchy traversal in main and Shadow passes. */
public final class ModelCpuClusterMetrics {
    public enum Pass { MAIN, SHADOW }
    private long calls, workCalls, mainCalls, shadowCalls;
    private long candidateNodes, selectedNodes, culledNodes;
    private long sourceTriangles, selectedTriangles;
    private long fallbackCount, disabledCalls, leafDraws, proxyDraws;
    private long maxDepth, traversalLimitHits;
    private long triangleInvariantViolations;
    private long traversalNanos;
    private final EnumMap<Pass, Long> traversalByPass = new EnumMap<>(Pass.class);
    private final Map<String, Long> fallbackReasons = new LinkedHashMap<>();
    private final Map<Integer, Long> selectedDepths = new LinkedHashMap<>();
    private String latestStatus = "UNTESTED";
    private String latestReason = "NONE";
    private Pass latestPass = Pass.MAIN;
    private int latestCandidates, latestSelected, latestCulled, latestSourceTriangles, latestSelectedTriangles, latestDepth;
    private final EnumMap<Pass, Latest> latestByPass = new EnumMap<>(Pass.class);
    /** Short rolling per-pass frame windows consumed by the live debug HUD. */
    private final EnumMap<Pass, FrameWindow> frameByPass = new EnumMap<>(Pass.class);
    private boolean frameOpen;
    private long openFrame;
    private long lastCompletedFrame;

    public ModelCpuClusterMetrics() {
        frameByPass.put(Pass.MAIN, new FrameWindow());
        frameByPass.put(Pass.SHADOW, new FrameWindow());
        traversalByPass.put(Pass.MAIN, 0L);
        traversalByPass.put(Pass.SHADOW, 0L);
    }

    /** Begin collecting one frame's CPU cluster selection totals. */
    public void beginFrame(long frame) {
        if (frame < 0) throw new IllegalArgumentException("frame must be nonnegative");
        openFrame = frame; frameOpen = true;
        frameByPass.values().forEach(FrameWindow::beginFrame);
    }

    /** Close the current frame window; no-op if the renderer did not open one. */
    public void endFrame() {
        if (!frameOpen) return;
        frameByPass.values().forEach(window -> window.endFrame(openFrame));
        lastCompletedFrame = openFrame; frameOpen = false;
    }

    public void record(Pass pass, int candidates, int selected, int culled,
                       int source, int triangles, int depth, int leaves, int proxies,
                       boolean fallback, String reason) {
        Objects.requireNonNull(pass, "pass");
        if (candidates < 0 || selected < 0 || culled < 0 || source < 0 || triangles < 0
                || depth < 0 || leaves < 0 || proxies < 0)
            throw new IllegalArgumentException("CPU cluster metrics must be nonnegative");
        if (triangles > source) triangleInvariantViolations++;
        calls++;
        if (candidates > 0 || selected > 0 || culled > 0 || source > 0 || triangles > 0) workCalls++;
        if (pass == Pass.MAIN) mainCalls++; else shadowCalls++;
        candidateNodes = Math.addExact(candidateNodes, candidates);
        selectedNodes = Math.addExact(selectedNodes, selected);
        culledNodes = Math.addExact(culledNodes, culled);
        sourceTriangles = Math.addExact(sourceTriangles, source);
        selectedTriangles = Math.addExact(selectedTriangles, triangles);
        leafDraws = Math.addExact(leafDraws, leaves);
        proxyDraws = Math.addExact(proxyDraws, proxies);
        maxDepth = Math.max(maxDepth, depth);
        latestPass = pass;
        latestCandidates = candidates; latestSelected = selected; latestCulled = culled;
        latestSourceTriangles = source; latestSelectedTriangles = triangles; latestDepth = depth;
        latestByPass.put(pass, new Latest(candidates, selected, culled, source, triangles, depth, leaves, proxies,
                fallback ? "FALLBACK" : (selected > 0 ? "ACTIVE" : "ZERO_WORK"), normalize(reason)));
        latestReason = normalize(reason);
        latestStatus = fallback ? "FALLBACK" : (selected > 0 ? "ACTIVE" : "ZERO_WORK");
        if (fallback) {
            fallbackCount++;
            fallbackReasons.merge(latestReason, 1L, Long::sum);
        }
        selectedDepths.merge(depth, 1L, Long::sum);
        if ("TRAVERSAL_LIMIT".equals(latestReason)) traversalLimitHits++;
        if (!fallback && leaves == 0 && proxies == 0 && selected > 0) disabledCalls++;
        frameByPass.get(pass).record(candidates, selected, culled, source, triangles, depth, fallback);
    }

    /** Records CPU time spent traversing one hierarchy for a pass. */
    public void recordTraversal(Pass pass, long nanos) {
        Objects.requireNonNull(pass, "pass");
        if (nanos < 0) throw new IllegalArgumentException("traversal nanos must be nonnegative");
        traversalNanos = Math.addExact(traversalNanos, nanos);
        traversalByPass.merge(pass, nanos, Long::sum);
        frameByPass.get(pass).traversal(nanos);
    }

    public void recordDisabled(Pass pass, int source, String reason) {
        record(pass, 0, 1, 0, source, source, 0, 0, 0, true, reason);
        disabledCalls++;
        frameByPass.get(pass).disabled();
    }

    public void reset() {
        calls = workCalls = mainCalls = shadowCalls = 0;
        candidateNodes = selectedNodes = culledNodes = sourceTriangles = selectedTriangles = 0;
        fallbackCount = disabledCalls = leafDraws = proxyDraws = maxDepth = traversalLimitHits = 0;
        triangleInvariantViolations = 0;
        traversalNanos = 0; traversalByPass.replaceAll((pass, value) -> 0L);
        fallbackReasons.clear(); selectedDepths.clear();
        latestStatus = "UNTESTED"; latestReason = "NONE"; latestPass = Pass.MAIN;
        latestCandidates = latestSelected = latestCulled = latestSourceTriangles = latestSelectedTriangles = latestDepth = 0;
        latestByPass.clear();
        frameByPass.values().forEach(FrameWindow::reset);
        frameOpen = false; openFrame = lastCompletedFrame = 0;
    }

    private static String normalize(String reason) { return reason == null || reason.isBlank() ? "UNKNOWN" : reason; }

    private record Latest(int candidateNodes, int selectedNodes, int culledNodes,
                         int sourceTriangles, int selectedTriangles, int depth,
                         int leafDraws, int proxyDraws, String status, String reason) {
        private Map<String, Object> report() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", status);
            result.put("candidateNodes", candidateNodes);
            result.put("selectedNodes", selectedNodes);
            result.put("culledNodes", culledNodes);
            result.put("sourceTriangles", sourceTriangles);
            result.put("selectedTriangles", selectedTriangles);
            result.put("triangleReduction", Math.max(0, sourceTriangles - selectedTriangles));
            result.put("depth", depth);
            result.put("leafDraws", leafDraws);
            result.put("proxyDraws", proxyDraws);
            result.put("reason", reason);
            return Map.copyOf(result);
        }
    }

    public Map<String, Object> report() {
        Map<String, Object> latest = new LinkedHashMap<>();
        latest.put("pass", latestPass.name()); latest.put("status", latestStatus);
        latest.put("candidateNodes", latestCandidates); latest.put("selectedNodes", latestSelected);
        latest.put("culledNodes", latestCulled); latest.put("sourceTriangles", latestSourceTriangles);
        latest.put("selectedTriangles", latestSelectedTriangles); latest.put("depth", latestDepth);
        latest.put("reason", latestReason);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policy", ModelCpuClusterPolicy.active().report());
        result.put("calls", calls); result.put("workCalls", workCalls);
        result.put("mainCalls", mainCalls); result.put("shadowCalls", shadowCalls);
        result.put("candidateNodes", candidateNodes); result.put("selectedNodes", selectedNodes); result.put("culledNodes", culledNodes);
        result.put("sourceTriangles", sourceTriangles); result.put("selectedTriangles", selectedTriangles);
        result.put("triangleReduction", Math.max(0L, sourceTriangles - selectedTriangles));
        result.put("reductionPercent", sourceTriangles <= 0 ? 0.0 : Math.max(0.0, Math.min(100.0,
                (sourceTriangles - Math.max(0L, selectedTriangles)) * 100.0 / sourceTriangles)));
        result.put("fallbackCount", fallbackCount); result.put("disabledCalls", disabledCalls);
        result.put("leafDraws", leafDraws); result.put("proxyDraws", proxyDraws);
        result.put("maxDepth", maxDepth); result.put("traversalLimitHits", traversalLimitHits);
        result.put("triangleInvariantViolations", triangleInvariantViolations);
        result.put("triangleInvariant", triangleInvariantViolations == 0 ? "PASS" : "FAIL");
        result.put("traversalNanos", traversalNanos);
        result.put("traversalByPass", new EnumMap<>(traversalByPass));
        result.put("fallbackReasons", new LinkedHashMap<>(fallbackReasons));
        result.put("selectedDepths", new LinkedHashMap<>(selectedDepths));
        result.put("latest", latest);
        Map<String, Object> latestByPassReport = new LinkedHashMap<>();
        for (Pass pass : Pass.values()) {
            Latest passLatest = latestByPass.get(pass);
            Map<String, Object> passReport = new LinkedHashMap<>(passLatest == null ? Map.of("status", "UNTESTED") : passLatest.report());
            passReport.put("traversalNanos", traversalByPass.getOrDefault(pass, 0L));
            latestByPassReport.put(pass.name(), Map.copyOf(passReport));
        }
        result.put("latestByPass", latestByPassReport);
        Map<String, Object> frameReport = new LinkedHashMap<>();
        frameReport.put("frame", lastCompletedFrame);
        for (Pass pass : Pass.values()) frameReport.put(pass.name(), frameByPass.get(pass).report());
        result.put("frame", frameReport);
        result.put("status", calls == 0 ? "UNTESTED" : latestStatus);
        result.put("selectionMode", "CPU screen-space geometric-error hierarchy traversal; main and Shadow share nodes and use separate pixel budgets");
        result.put("geometryScope", "OPAQUE/MASK hierarchy nodes; BLEND retains full BSP geometry");
        result.put("bvhScope", "full-resolution lightOcclusionScenes; CPU cluster render geometry never replaces BVH input");
        return Map.copyOf(result);
    }

    /** Allocation-free while recording; percentile sorting is deferred to report/HUD reads. */
    private static final class FrameWindow {
        private static final int CAPACITY = 120;
        private final long[] sources = new long[CAPACITY];
        private final long[] selected = new long[CAPACITY];
        private final long[] candidates = new long[CAPACITY];
        private final long[] selectedNodes = new long[CAPACITY];
        private final long[] culled = new long[CAPACITY];
        private final int[] depths = new int[CAPACITY];
        private final long[] fallbacks = new long[CAPACITY];
        private final long[] disableds = new long[CAPACITY];
        private final long[] traversals = new long[CAPACITY];
        private int count, cursor;
        private long calls, frameSources, frameSelected, frameCandidates, frameSelectedNodes, frameCulled, frameFallbacks, frameTraversalNanos;
        private int frameDepth;
        private long disabled;
        private long lastFrame, lastCalls, lastSources, lastSelected, lastCandidates, lastSelectedNodes, lastCulled, lastFallbacks, lastDisabled, lastTraversalNanos;
        private int lastDepth;

        private void beginFrame() {
            calls = frameSources = frameSelected = frameCandidates = frameSelectedNodes = frameCulled = frameFallbacks = frameTraversalNanos = 0;
            frameDepth = 0; disabled = 0;
        }
        private void record(int candidatesValue, int selectedValue, int culledValue, int sourceValue, int selectedTrianglesValue, int depthValue, boolean fallback) {
            calls++; frameCandidates += candidatesValue; frameSelectedNodes += selectedValue; frameCulled += culledValue;
            frameSources += sourceValue; frameSelected += selectedTrianglesValue; frameDepth = Math.max(frameDepth, depthValue);
            if (fallback) frameFallbacks++;
        }
        private void disabled() { disabled++; }
        private void traversal(long nanos) { frameTraversalNanos = Math.addExact(frameTraversalNanos, nanos); }
        private void endFrame(long frame) {
            if (calls == 0) return;
            int index = cursor++ % CAPACITY;
            sources[index] = frameSources; selected[index] = frameSelected; candidates[index] = frameCandidates;
            selectedNodes[index] = frameSelectedNodes; culled[index] = frameCulled; depths[index] = frameDepth; fallbacks[index] = frameFallbacks; disableds[index] = disabled; traversals[index] = frameTraversalNanos;
            count = Math.min(CAPACITY, count + 1);
            lastFrame = frame; lastCalls = calls; lastSources = frameSources; lastSelected = frameSelected; lastCandidates = frameCandidates;
            lastSelectedNodes = frameSelectedNodes; lastCulled = frameCulled; lastDepth = frameDepth; lastFallbacks = frameFallbacks; lastDisabled = disabled; lastTraversalNanos = frameTraversalNanos;
        }
        private Map<String, Object> report() {
            long[] sourceValues = copy(sources), selectedValues = copy(selected), candidateValues = copy(candidates), selectedNodeValues = copy(selectedNodes), culledValues = copy(culled);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("samples", count); result.put("capacity", CAPACITY); result.put("lastFrame", lastFrame);
            result.put("lastCalls", lastCalls); result.put("lastSourceTriangles", lastSources); result.put("lastSelectedTriangles", lastSelected);
            result.put("lastTriangleReduction", Math.max(0L, lastSources - lastSelected));
            result.put("lastReductionPercent", reduction(lastSources, lastSelected));
            result.put("lastCandidateNodes", lastCandidates); result.put("lastSelectedNodes", lastSelectedNodes);
            result.put("lastCulledNodes", lastCulled); result.put("lastDepth", lastDepth);
            result.put("lastFallbacks", lastFallbacks); result.put("lastDisabled", lastDisabled);
            result.put("lastTraversalNanos", lastTraversalNanos);
            result.put("traversalNanosP95", percentile(Arrays.copyOf(traversals, count), .95));
            result.put("sourceTrianglesP50", percentile(sourceValues, .50)); result.put("sourceTrianglesP95", percentile(sourceValues, .95));
            result.put("selectedTrianglesP50", percentile(selectedValues, .50)); result.put("selectedTrianglesP95", percentile(selectedValues, .95));
            result.put("selectedTrianglesMax", max(selectedValues));
            result.put("candidateNodesP95", percentile(candidateValues, .95)); result.put("selectedNodesP95", percentile(selectedNodeValues, .95));
            result.put("culledNodesP95", percentile(culledValues, .95));
            result.put("depthP95", percentile(depths, count, .95)); result.put("depthMax", max(depths, count));
            result.put("fallbackFrames", positive(fallbacks));
            result.put("disabledFrames", positive(disableds));
            return Map.copyOf(result);
        }
        private long[] copy(long[] source) { return Arrays.copyOf(source, count); }
        private static long percentile(long[] values, double p) {
            if (values.length == 0) return 0; Arrays.sort(values);
            return values[Math.min(values.length - 1, Math.max(0, (int) Math.ceil(values.length * p) - 1))];
        }
        private static long percentile(int[] values, int length, double p) {
            if (length == 0) return 0; int[] copy = Arrays.copyOf(values, length); Arrays.sort(copy);
            return copy[Math.min(length - 1, Math.max(0, (int) Math.ceil(length * p) - 1))];
        }
        private static long max(long[] values) { long result = 0; for (long value : values) result = Math.max(result, value); return result; }
        private static long max(int[] values, int length) { long result = 0; for (int i = 0; i < length; i++) result = Math.max(result, values[i]); return result; }
        private static long positive(long[] values) { long result = 0; for (long value : values) if (value > 0) result++; return result; }
        private static double reduction(long source, long selectedValue) {
            return source <= 0 ? 0.0 : Math.max(0.0, Math.min(100.0, (source - Math.max(0L, selectedValue)) * 100.0 / source));
        }
        private void reset() {
            count = cursor = 0; beginFrame(); lastFrame = lastCalls = lastSources = lastSelected = lastCandidates = lastSelectedNodes = lastCulled = lastFallbacks = lastDisabled = lastTraversalNanos = 0; lastDepth = 0;
            Arrays.fill(sources, 0); Arrays.fill(selected, 0); Arrays.fill(candidates, 0); Arrays.fill(selectedNodes, 0); Arrays.fill(culled, 0); Arrays.fill(depths, 0); Arrays.fill(fallbacks, 0); Arrays.fill(disableds, 0); Arrays.fill(traversals, 0);
        }
    }
}
