package com.mine.geometry_node.client.runtime.model.diagnostic;

import com.mine.geometry_node.client.runtime.model.render.resource.ModelResidencyLedger;
import com.mine.geometry_node.client.runtime.model.render.resource.ModelRenderBudget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded performance views; detailed geometry and compatibility diagnostics stay out of exports. */
final class ModelPerformanceReport {
    static Map<String, Object> create(Map<String, Object> snapshot, Map<String, Object> capture) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 9);
        result.put("capture", capture);
        result.put("overview", null);
        Map<String, Object> counts = select(snapshot, "repositoryEntries", "workerQueue", "assetsDisposed");
        counts.put("instances", entries(snapshot.get("instances")).size());
        counts.put("visibleInstances", entries(snapshot.get("instances")).stream()
                .filter(instance -> Boolean.TRUE.equals(instance.get("visible"))).count());
        counts.put("assets", entries(snapshot.get("assets")).size());
        counts.put("pendingLoads", size(snapshot.get("pending")));
        counts.put("failedLoads", size(snapshot.get("failures")));
        counts.put("deferredInstances", size(snapshot.get("renderDeferrals")));
        result.put("counts", counts);
        result.put("instances", entries(snapshot.get("instances")).stream().map(instance -> select(instance,
                "id", "assetId", "scene", "visible", "state", "renderDeferredReason")).toList());
        result.put("loadFailures", boundedReasons(map(snapshot.get("failures"))));
        result.put("renderDeferrals", boundedReasons(map(snapshot.get("renderDeferrals"))));
        result.put("geometry", select(map(snapshot.get("geometry")), "uniqueAssets", "instancedScenes", "scope"));
        Map<String,Object> memory = statistics(map(snapshot.get("memory")));
        memory.remove("gpuTimingAvailable"); // Availability belongs to each GPU timer, not memory accounting.
        result.put("memory", memory);
        result.put("uploadQueue", statistics(map(snapshot.get("uploadQueue"))));
        result.put("renderer", renderer(map(snapshot.get("renderer"))));
        result.put("collision", snapshot.getOrDefault("collision", Map.of()));
        result.put("assets", entries(snapshot.get("assets")).stream().map(ModelPerformanceReport::asset).toList());
        Map<?, ?> runtime = map(snapshot.get("runtime"));
        Map<String, Object> jvm = select(runtime, "heapUsedBytes", "heapCommittedBytes", "heapMaxBytes",
                "nonHeapUsedBytes", "nonHeapCommittedBytes");
        jvm.put("gc", entries(runtime.get("gc")).stream()
                .map(gc -> select(gc, "name", "collectionCount", "collectionTimeMillis")).toList());
        result.put("jvm", jvm);
        result.put("overview", ModelPerformanceOverview.create(result));
        return result;
    }

    private static Map<String, Object> renderer(Map<?, ?> source) {
        Map<String, Object> result = select(source, "backend", "transparentTrianglesTotal", "lastFailure");
        Object budget = source.get("budget");
        if (budget instanceof ModelRenderBudget) result.put("budget", budget);
        else if (budget instanceof Map<?, ?> values) result.put("budget", statistics(values));
        if (source.get("modelLod") instanceof Map<?, ?> lodInfo) result.put("modelLod", modelLod(lodInfo));
        result.put("shadowDistancePolicy", selectStatistics(map(source.get("shadowDistancePolicy")),
                "enabled", "maxDistance", "automatic", "manualTolerance", "updateInterval"));
        result.put("metrics", metrics(map(source.get("metrics"))));
        result.put("shaderGpu", source.getOrDefault("shaderGpu", null));
        result.put("modelGpu", source.getOrDefault("modelGpu", null));
        Object ledger = source.get("residency");
        result.put("residency", ledger instanceof ModelResidencyLedger.Snapshot s
                ? Map.of("cpuBytes", s.cpuBytes(), "gpuBytes", s.gpuBytes(), "textureBytes", s.textureBytes(),
                        "assets", s.assets(), "evictableAssets", s.evictableAssets(), "evictions", s.evictions())
                : statistics(map(ledger)));
        result.put("materialUploads", statistics(map(source.get("materialUploads"))));
        result.put("irisResident", selectStatistics(map(source.get("irisResident")),
                "enabled", "deferredDraws", "dynamicLightDraws", "uploadBudget"));
        result.put("lightSampleCache", selectStatistics(map(source.get("lightSampleCache")),
                "cachedSamples", "maxCachedSamples", "cacheEvictions", "cacheHits", "cacheMisses",
                "invalidations", "fullInvalidations", "detectedChanges"));
        result.put("modelLighting", lighting(map(source.get("localLightOcclusion"))));
        result.put("transparentResidency", selectStatistics(map(source.get("transparentResidency")),
                "cpuStagingBytes", "indexBytes", "indexSlots", "renderPasses", "currentDrawRuns", "ringDeferred",
                "sorts", "cacheHits", "uploads", "uploadedBytes", "deferred", "bspOrderedGroupsTotal",
                "centroidFallbackGroupsTotal", "lastInputTriangles", "lastBspSplits", "lastOutputTriangles",
                "visibleBspGroups", "centroidFallbackGroups", "sortCpuNanos", "sortCpuNanosMax"));
        Map<?, ?> routing = map(source.get("shadowRouting"));
        var shadow = selectStatistics(routing, "callbacks", "casters", "status", "lastFailure",
                "effectiveToleranceMin", "effectiveToleranceMax", "cullingStrategy",
                "fullyInsidePrimitives", "clusterTestsSkipped");
        shadow.put("corrections", selectStatistics(map(routing.get("repeatUvCompatibility")),
                "appliedDraws", "unmatchedDraws", "sundialDepthPrograms", "seusVoxelGuardPrograms",
                "seusDepthPrograms", "seusCoveragePrograms", "seusOrientedCoveragePrograms", "seusDepthDraws",
                "uniformUploads", "uniformUploadsSkipped"));
        result.put("shadow", shadow);
        return result;
    }

    private static Map<String, Object> metrics(Map<?, ?> source) {
        Map<String, Object> result = statistics(source);
        result.remove("modelLod"); // Published once at renderer.modelLod with its semantic metadata.
        result.put("timingScope", "cpu_submission");
        if (source.containsKey("hostInterval")) {
            Map<String, Object> interval = statistics(map(source.get("hostInterval")));
            interval.put("scope", "main_submit_to_transparent_complete");
            result.put("hostInterval", interval);
        }
        // Totals and latest values have different sampling scopes; retain both without duplicate aliases.
        Map<String, Object> clusters = new LinkedHashMap<>();
        for (String pass : List.of("main", "shadow", "transparent")) {
            clusters.put(pass, selectStatistics(map(map(source.get("clusterCulling")).get(pass)),
                    "calls", "workCalls", "totalCandidateClusters", "totalVisibleClusters", "totalCulledClusters", "latest"));
        }
        result.put("clusterCulling", clusters);
        Map<String, Object> shadow = selectStatistics(map(source.get("shadowCulling")), "calls", "workCalls",
                "totalCandidateModels", "totalVisibleModels", "totalCulledModels", "totalSkippedModels",
                "skipReasons", "distance", "quality", "latest");
        Map<?, ?> latestShadow = map(map(source.get("shadowCulling")).get("latest"));
        Map<String, Object> latest = statistics(latestShadow);
        if (latestShadow.containsKey("skipReason")) latest.put("skipReason", latestShadow.get("skipReason"));
        shadow.put("latest", latest);
        result.put("shadowCulling", shadow);
        for (String subsystem : List.of("lightingCpu", "transparentCpu")) {
            if (source.containsKey(subsystem)) result.put(subsystem,
                    selectStatistics(map(source.get(subsystem)), "calls", "workCalls", "lastNanos", "cpu"));
        }
        return result;
    }

    private static Map<String, Object> lighting(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> shared = selectStatistics(source, "enabled", "bvhEnabled",
                "matchedPrograms", "activeInstances", "excludedInstances",
                "opaqueAnyHitInstances", "transmissionInstances", "sharedScenes", "gpuBytes", "retiringGpuBytes",
                "bvhStackSlots", "bvhMaxDepth", "bvhNodeBytes", "bvhNodes", "bvhSahSplits", "bvhMedianSplits",
                "cpuUploadPayloadBytes", "uploadBytes", "totalUploadBytes", "uploadReady", "uploadedPayloadBytes", "pendingUploadBytes",
                "updateNanos", "peakUpdateNanos");
        if (source.containsKey("status")) shared.put("status", source.get("status"));
        result.put("sharedBvh", shared);
        Map<String, Object> local = selectStatistics(source, "heldEnabled", "placedEnabled", "dynamicEnabled",
                "heldOcclusionEnabled", "worldOcclusionEnabled", "heldLightEmission", "heldOcclusionPrograms",
                "worldOcclusionPrograms", "seusPlacedDirectReady", "placedAreaQualityPrograms", "additionalDrawCalls");
        local.putAll(selectStatistics(map(source.get("localLight")), "enabled", "activeReceivers", "disabledReceivers"));
        Map<String, Object> gi = selectStatistics(source, "primaryGiEnabled", "secondaryGiEnabled",
                "primaryGiPrograms", "secondaryGiPrograms");
        gi.putAll(select(map(source.get("giOcclusion")), "enabled", "activeReceivers", "disabledReceivers", "sharedProbes"));
        result.put("localLight", local);
        result.put("giOcclusion", gi);
        result.put("timingScope", "shared_bvh_cpu_preparation; shaderGpu contains whole pack passes, not isolated local or GI cost");
        if (source.get("quality") instanceof Map<?,?> quality) local.put("quality",
                select(quality, "automatic", "areaSamples", "resolutionScale", "defaultAreaSamples", "defaultResolutionScale", "sessionOnly"));
        if (source.containsKey("receiverQuality")) result.put("receiverQuality", entries(source.get("receiverQuality")).stream()
                .map(receiver -> select(receiver, "instance", "distance", "visible", "autoDisabled", "overridden",
                        "localEnabled", "giEnabled", "samples", "resolution")).toList());
        if (source.get("receiverPolicy") instanceof Map<?, ?> policy) result.put("receiverPolicy", select(policy,
                "receivers", "localActiveReceivers", "giActiveReceivers", "autoDisabledReceivers", "overrideCount",
                "offscreenDistance", "excludedReceivers", "occludersPreserved", "scope"));
        if (source.get("receiverTag") instanceof Map<?, ?> tag) result.put("receiverTag", select(tag,
                "taggedDraws", "writerPrograms", "decoderPrograms", "writerSupported", "decoderSupported", "scope"));
        if (source.get("resolution") instanceof Map<?,?> resolution) local.put("resolution",
                select(resolution, "requestedScale", "effectiveScale", "automatic", "status", "scope", "fullWidth", "fullHeight",
                        "allocationScale", "autoMinScale", "autoMaxScale",
                        "width", "height", "gpuBytes", "retiringGpuBytes", "compatiblePrograms", "prepassDraws", "fallbackDraws", "additionalDrawCalls",
                        "sunShadowAffected", "giAffected", "failure"));
        Map<?, ?> placed = map(source.get("placedLight"));
        Map<String, Object> placedStats = selectStatistics(placed, "instances", "sourcesIncludingOverlap", "scans",
                "patchedBlocks", "scannedCells", "cpuBytes", "gpuBytes", "retiringGpuBytes", "uploadedBytes",
                "updateNanos", "peakUpdateNanos");
        placedStats.put("regions", size(placed.get("regions")));
        local.put("placedLight", placedStats);
        Map<?, ?> providers = map(source.get("sourceProviders"));
        Map<String, Object> providerStats = selectStatistics(providers, "attachedSources", "activeSources",
                "rejectedSources", "updateNanos");
        providerStats.put("registeredProviders", size(providers.get("registeredProviders")));
        local.put("sourceProviders", providerStats);
        local.put("transparentReceivers", selectStatistics(map(source.get("transparentReceivers")), "draws"));
        return result;
    }

    private static Map<String, Object> asset(Map<?, ?> source) {
        Map<String, Object> result = select(source, "id", "name", "gpuResident");
        result.put("geometry", statistics(map(source.get("geometry"))));
        Map<?, ?> clusters = map(source.get("clusters"));
        Map<String, Object> clusterStats = selectStatistics(clusters, "count");
        List<Map<?, ?>> primitives = entries(clusters.get("primitives"));
        List<Map<String,Object>> primitiveStats = primitives.stream().map(ModelPerformanceReport::primitive)
                .filter(value -> !value.isEmpty()).toList();
        if (!primitiveStats.isEmpty()) clusterStats.put("primitives", primitiveStats);
        result.put("clusters", clusterStats);
        if (source.get("modelLod") instanceof Map<?, ?> lodInfo) result.put("modelLod", modelLod(lodInfo));
        for (String section : List.of("memory", "irisResidency", "materialUpdates")) {
            if (source.containsKey(section)) result.put(section, statistics(map(source.get(section))));
        }
        Map<?, ?> residency = map(source.get("irisResidency"));
        if (residency.get("requestedLevelUpload") instanceof Map<?, ?> progress) {
            Map<String, Object> exported = statistics(residency);
            exported.put("requestedLevelUpload", select(progress, "level", "levels", "objects", "status", "totalIndices",
                    "uploadedIndices", "pendingIndices", "completedPrimitives", "pendingPrimitives"));
            result.put("irisResidency", exported);
        }
        result.put("opaqueBatching", sceneStatistics(source.get("opaqueBatching"),
                "sourceOpaqueDraws", "preparedOpaqueDraws", "savedDrawsPerPass", "additionalCpuBytes"));
        List<Map<String, Object>> partitions = new ArrayList<>();
        for (Map<?, ?> scene : entries(source.get("lightPartitioning"))) {
            Map<String, Object> stats = selectStatistics(scene, "scene", "partitionedPrimitives",
                    "additionalDrawsPerPass", "additionalCpuBytes", "degraded");
            stats.put("shadowTessellation", selectStatistics(map(scene.get("shadowTessellation")),
                    "additionalCpuBytes", "refinedPrimitives", "outputTriangles", "degraded"));
            partitions.add(stats);
        }
        result.put("lightPartitioning", partitions);
        result.put("lightOcclusion", sceneStatistics(source.get("lightOcclusion"), "opaqueTriangles",
                "maskSourceTriangles", "blendSourceTriangles", "generatedAlphaTriangles", "nodeCount", "buildNanos",
                "cpuBytes", "gpuBytesEstimated", "peakBuildCpuBytesEstimated", "bvhMaxDepth", "bvhNodeCapacity", "bvhSahSplits", "bvhMedianSplits"));
        result.put("transparentPreparation", sceneStatistics(source.get("transparentPreparation"),
                "inputTriangles", "outputTriangles", "splits", "work", "buildNanos", "cpuBytes", "gpuBytesEstimated"));
        return result;
    }

    private static Map<String, Object> primitive(Map<?, ?> source) {
        return select(source, "primitiveId", "triangleCount", "clusterCount",
                "alphaMode", "doubleSided", "castsShadow", "unlit");
    }

    /** LOD reports contain four bounded levels and per-pass selection counters. */
    private static Map<String, Object> modelLod(Map<?, ?> source) {
        Map<String, Object> result = select(source, "status", "cacheStatus", "key", "cpuBytes", "gpuBytes",
                "sourceTriangles", "preservedAlphaMeshes", "buildNanos", "cacheFailure", "cacheDirectory",
                "selectionScope", "geometryScope", "bvhScope", "gpuScope", "scope", "objects", "objectMetadataBytes");
        for (String key : List.of("policy", "metrics", "fallbackReasons")) {
            if (source.get(key) instanceof Map<?, ?> values) result.put(key, copyMap(values));
        }
        if (source.get("fallbackReasons") instanceof Collection<?> values) result.put("fallbackReasons", copyValue(values));
        if (source.containsKey("levels")) result.put("levels", entries(source.get("levels")).stream()
                .map(level -> select(level, "level", "targetRatio", "targetReached", "triangles", "vertices", "cpuBytes", "gpuBytes",
                        "reducedMeshes", "sourceMeshes", "maxError", "residentPrimitives", "pendingPrimitives")).toList());
        return result;
    }
    private static Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null) result.put(key.toString(), copyValue(value));
        });
        return result;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) return copyMap(map);
        if (value instanceof Collection<?> collection) return collection.stream().map(ModelPerformanceReport::copyValue).toList();
        return value;
    }

    private static List<Map<String, Object>> sceneStatistics(Object value, String... keys) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<?, ?> scene : entries(value)) {
            Map<String, Object> stats = selectStatistics(scene, keys);
            stats.put("scene", scene.containsKey("scene") ? scene.get("scene") : result.size());
            if (scene.containsKey("status")) stats.put("status", scene.get("status"));
            if (scene.get("reason") instanceof String reason && !reason.isEmpty()) stats.put("reason", reason);
            result.add(stats);
        }
        return result;
    }

    private static Map<String, Object> selectStatistics(Map<?, ?> source, String... keys) {
        return statistics(select(source, keys));
    }

    private static Map<String, Object> boundedReasons(Map<?, ?> source) {
        List<Map<String, Object>> reasons = new ArrayList<>();
        for (var entry : source.entrySet()) {
            if (reasons.size() == 32) break;
            String reason = String.valueOf(entry.getValue());
            reasons.add(Map.of("id", String.valueOf(entry.getKey()), "reason", reason.substring(0, Math.min(512, reason.length()))));
        }
        return Map.of("entries", reasons, "omitted", Math.max(0, source.size() - reasons.size()));
    }

    private static Map<String, Object> select(Map<?, ?> source, String... keys) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : keys) if (source.containsKey(key)) result.put(key, source.get(key));
        return result;
    }

    private static Map<String, Object> statistics(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!(key instanceof String) && !(key instanceof Number)) return;
            String name = key.toString();
            if (name.equals("windowCapacity")) return;
            if (value instanceof Number || value instanceof Boolean) result.put(name, value);
            else if (value instanceof String && List.of("status", "reason", "failure", "lastFailure", "scope", "timingScope").contains(name)) result.put(name, value);
            else if (value instanceof Map<?, ?> nested) result.put(name, statistics(nested));
        });
        return result;
    }

    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    private static int size(Object value) {
        return value instanceof Collection<?> collection ? collection.size() : value instanceof Map<?, ?> map ? map.size() : 0;
    }
    private static List<Map<?, ?>> entries(Object value) {
        List<Map<?, ?>> result = new ArrayList<>();
        if (value instanceof Collection<?> collection) for (Object item : collection) {
            if (item instanceof Map<?, ?> entry) result.add(entry);
        }
        return result;
    }

    private ModelPerformanceReport() {}
}
