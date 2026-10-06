package com.mine.geometry_node.client.model.render.diagnostic;

import java.util.Map;

/** Bounded, asynchronous batch intervals. Never insert a query for every triangle or draw. */
public final class ModelDrawGpuTimings {
    private static final ModelShaderGpuTimings.Tracker TRACKER =
            new ModelShaderGpuTimings.Tracker(new ModelShaderGpuTimings.OpenGlDriver(), 32);
    private static final int SAMPLE_INTERVAL = 16;

    private ModelDrawGpuTimings() {}

    public static void poll() { TRACKER.poll(); }
    public static int begin(String pass, long frame) {
        return frame % SAMPLE_INTERVAL == 0 ? TRACKER.begin(pass) : -1;
    }
    public static void end(int token) { TRACKER.end(token); }
    public static void reset() { TRACKER.clearSamples(); }
    public static void clear() { TRACKER.clear(); }

    public static Map<String, Object> report() {
        var result = TRACKER.report();
        result.put("sampleIntervalFrames", SAMPLE_INTERVAL);
        result.put("scope", "GPU timestamp elapsed batch intervals, including uploads, setup and possible GPU idle while CPU submits; not isolated shader execution. main_opaque excludes deferred collector fallback; shadow covers model casters only; transparent includes sorting/preparation. No whole-frame GPU timing.");
        return result;
    }
}
