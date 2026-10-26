package com.mine.geometry_node.client.runtime.model.render.pipeline;

/** Immutable material contract retained until a deferred collector's RenderType actually draws. */
public record ModelIrisAlphaPolicy(Mode mode, float cutoff) {
    public enum Mode { OPAQUE, MASK, BLEND }

    public ModelIrisAlphaPolicy {
        if (mode == null || !Float.isFinite(cutoff) || cutoff < 0)
            throw new IllegalArgumentException("Invalid model alpha policy");
    }
    public static ModelIrisAlphaPolicy mask(float cutoff) { return new ModelIrisAlphaPolicy(Mode.MASK, cutoff); }
    public static ModelIrisAlphaPolicy blend() { return new ModelIrisAlphaPolicy(Mode.BLEND, 0); }
    public static ModelIrisAlphaPolicy opaque() { return new ModelIrisAlphaPolicy(Mode.OPAQUE, 0); }
    public boolean needsOverride() { return mode != Mode.OPAQUE; }
    public float threshold(ModelIrisAlphaSource.Comparison comparison) {
        // BLEND has no mask cutoff: all nonnegative output alpha, including zero, survives.
        return ModelIrisAlphaSource.threshold(mode == Mode.MASK ? cutoff : 0, comparison);
    }
}
