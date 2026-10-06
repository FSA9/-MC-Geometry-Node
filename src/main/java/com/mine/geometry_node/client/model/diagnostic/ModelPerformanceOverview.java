package com.mine.geometry_node.client.model.diagnostic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only interpretation of measured samples; missing measurements are never zero cost. */
final class ModelPerformanceOverview {
    static Map<String, Object> create(Map<String, Object> report) {
        var result = new LinkedHashMap<String, Object>();
        var renderer = child(report, "renderer");
        var metrics = child(renderer, "metrics");
        List<Map<String, Object>> cpu = new ArrayList<>();
        for (String name : List.of("mainPass", "shadowPass", "lightingCpu", "transparentCpu")) {
            cpu.add(timing(name, "renderer.metrics." + name + ".cpu", child(child(metrics, name), "cpu"), "cpuNanos"));
        }
        rank(cpu);
        result.put("cpuMs", cpu);
        List<Map<String, Object>> gpu = new ArrayList<>();
        for (String group : List.of("modelGpu", "shaderGpu")) {
            Object passes = child(renderer, group).get("passes");
            if (passes instanceof List<?> values) for (Object value : values) {
                if (!(value instanceof Map<?, ?> pass)) continue;
                var row = timing(String.valueOf(pass.get("name")), "renderer." + group + ".passes", pass, "gpuNanos");
                row.put("group", group);
                row.put("timerStatus", java.util.Objects.toString(child(renderer, group).get("status"), "UNKNOWN"));
                gpu.add(row);
            }
        }
        rank(gpu);
        result.put("gpuMs", gpu);
        result.put("hostIntervalMs", timing("main_submit_to_transparent_complete", "renderer.metrics.hostInterval",
                child(metrics, "hostInterval"), "nanos"));
        result.put("readingRules", List.of(
                "Rankings compare measured scope medians, not exclusive frame cost or proof of a bottleneck.",
                "CPU mainPass already includes transparentCpu. Iris phases and upload times overlap parent scopes; never sum them.",
                "CPU and GPU overlap. GPU batch intervals can include CPU submission starvation; do not add CPU and GPU timings.",
                "Percentiles use rolling windows; totals/maxima are since reset. GPU samples arrive asynchronously and scopes are not frame-aligned.",
                "Model GPU intervals sample every 16 frames; shaderGpu measures full pack draws, not isolated model lighting.",
                "hostInterval excludes portions of the frame. reportedFps is the game counter, not a measured CPU/GPU frame budget.",
                "No sample means unknown, not zero. Disabled modules and pending uploads must be checked before comparisons.",
                "Group coverage reports the best available sample status; SAMPLED does not mean every pass has sufficient fresh samples. Inspect individual timing rows."));
        result.put("coverage", List.of(
                coverage("model_cpu", sampleState(cpu), "renderer.metrics", "Main/shadow submission, lighting preparation, transparency; not all game CPU work."),
                coverage("model_gpu", state(child(renderer, "modelGpu")), "renderer.modelGpu", "Main opaque batch, model shadow batch, transparent batch; collector fallback excluded."),
                coverage("pack_gpu", state(child(renderer, "shaderGpu")), "renderer.shaderGpu", "Instrumented Iris fullscreen draws; excludes compute and other uninstrumented passes."),
                coverage("local_and_gi_gpu", "PROFILE_COMPARISON_REQUIRED", "renderer.modelLighting", "Shared BVH; no isolated GPU function timings. Use /model local profile lighting."),
                coverage("whole_frame_cpu_gpu", "NOT_MEASURED", "capture", "FPS and host interval are context only; no exclusive whole-frame CPU/GPU accounting."),
                coverage("ray_traversal", "NOT_MEASURED", "renderer.modelLighting.sharedBvh", "Tree size/build data exists; ray counts, visited nodes and intersection tests are not measured."),
                coverage("post_backface_triangles", "NOT_MEASURED", "renderer.modelLod", "LOD totals and CPU culling counters are not visible GPU triangle counts.")));
        List<String> observations = new ArrayList<>();
        if (number(child(report, "counts"), "pendingLoads") > 0
                || number(child(report, "counts"), "deferredInstances") > 0) observations.add("LOAD_OR_RESIDENCY_PENDING");
        if (number(child(report, "counts"), "failedLoads") > 0) observations.add("MODEL_LOAD_FAILURES");
        var bvh = child(child(renderer, "modelLighting"), "sharedBvh");
        if (Boolean.FALSE.equals(bvh.get("uploadReady"))) observations.add("BVH_UPLOAD_NOT_READY");
        for (String group : List.of("modelGpu", "shaderGpu")) {
            var timer = child(renderer, group);
            if (!"SAMPLED".equals(state(timer))) observations.add(group + ":" + state(timer));
            if (number(timer, "droppedSamples") > 0) observations.add(group + ":DROPPED_SAMPLES");
        }
        result.put("observations", observations);
        result.put("indirectAssessment", "NOT_PROVEN: inspect mainPass/shadowPass CPU, irisSubmission phases and draw counts together. High draw count alone is insufficient; compare modelGpu and pack GPU timings, then benchmark the changed path.");
        return result;
    }

    private static Map<String, Object> coverage(String module, String status, String path, String scope) {
        return Map.of("module", module, "status", status, "path", path, "scope", scope);
    }

    private static String state(Map<?, ?> timer) {
        if (timer.isEmpty()) return "NOT_MEASURED";
        if (!Boolean.TRUE.equals(timer.get("available"))) return java.util.Objects.toString(timer.get("status"), "UNAVAILABLE");
        if (timer.get("passes") instanceof List<?> passes && !passes.isEmpty()) {
            var rows = new ArrayList<Map<String, Object>>();
            for (Object value : passes) if (value instanceof Map<?, ?> pass) {
                rows.add(timing("", "", pass, "gpuNanos"));
            }
            String status = sampleState(rows);
            if (!"NO_SAMPLES".equals(status)) return status;
        }
        return "NO_COMPLETED_SAMPLES";
    }

    private static String sampleState(List<Map<String, Object>> rows) {
        boolean stale = false, low = false;
        for (var row : rows) {
            if (!row.containsKey("msP50")) continue;
            switch (String.valueOf(row.get("status"))) {
                case "SAMPLED" -> { return "SAMPLED"; }
                case "LOW_SAMPLE_COUNT" -> low = true;
                case "STALE_SAMPLES" -> stale = true;
                default -> { }
            }
        }
        return low ? "LOW_SAMPLE_COUNT" : stale ? "STALE_SAMPLES" : "NO_SAMPLES";
    }

    private static Map<String, Object> timing(String name, String path, Map<?, ?> values, String prefix) {
        var row = new LinkedHashMap<String, Object>();
        row.put("name", name);
        row.put("path", path);
        long samples = number(values, "windowSamples");
        row.put("windowSamples", samples);
        row.put("status", samples == 0 ? "NO_SAMPLES" : samples < 20 ? "LOW_SAMPLE_COUNT" : "SAMPLED");
        for (String key : List.of("sampleAgeMs", "windowSpanMs")) {
            if (values.get(key) instanceof Number value) row.put(key, value);
        }
        if (samples > 0 && values.get("sampleAgeMs") instanceof Number age && age.doubleValue() > 5000) {
            row.put("status", "STALE_SAMPLES");
        }
        if (samples > 0) for (String suffix : List.of("P50", "P95", "P99")) {
            if (values.get(prefix + suffix) instanceof Number value) row.put("ms" + suffix, value.doubleValue() / 1_000_000.0);
        }
        return row;
    }

    private static void rank(List<Map<String, Object>> rows) {
        rows.sort(Comparator.comparingDouble((Map<String, Object> row) ->
                row.get("msP50") instanceof Number value ? value.doubleValue() : -1).reversed());
    }

    private static long number(Map<?, ?> map, String key) {
        return map.get(key) instanceof Number number ? number.longValue() : 0;
    }
    private static Map<?, ?> child(Map<?, ?> map, String key) {
        return map.get(key) instanceof Map<?, ?> child ? child : Map.of();
    }
    private ModelPerformanceOverview() {}
}
