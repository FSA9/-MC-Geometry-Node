package com.mine.geometry_node.client.model.render.shadow;

import java.util.HashMap;
import java.util.Map;

/** Only model-owned uniforms, cached for one uninterrupted shadow callback. */
final class ModelShadowUniformUploads {
    interface Driver {
        void viewport(int location, int[] value);
        void tolerance(int location, float value);
    }

    private static final class Values {
        boolean viewport;
        float tolerance = Float.NaN;
    }

    private final Map<Integer, Values> programs = new HashMap<>();
    private long uploads, skippedUploads;

    void clear() { programs.clear(); }
    void remove(int program) { programs.remove(program); }

    void apply(Driver driver, int program, int viewportLocation, int toleranceLocation, int[] viewport, float tolerance) {
        if (viewportLocation < 0 && toleranceLocation < 0) return;
        Values values = programs.computeIfAbsent(program, ignored -> new Values());
        if (viewportLocation >= 0) {
            if (!values.viewport) {
                driver.viewport(viewportLocation, viewport);
                values.viewport = true;
                uploads++;
            } else skippedUploads++;
        }
        if (toleranceLocation >= 0) {
            if (values.tolerance != tolerance) {
                driver.tolerance(toleranceLocation, tolerance);
                values.tolerance = tolerance;
                uploads++;
            } else skippedUploads++;
        }
    }

    long uploads() { return uploads; }
    long skippedUploads() { return skippedUploads; }
}
