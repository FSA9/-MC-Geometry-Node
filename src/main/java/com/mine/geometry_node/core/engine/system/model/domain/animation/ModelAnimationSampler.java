package com.mine.geometry_node.core.engine.system.model.domain.animation;

import java.util.Arrays;

public final class ModelAnimationSampler {
    private final ModelAnimationInterpolation interpolation;
    private final int outputComponentCount;
    private final float[] keyTimes;
    private final float[] outputValues;

    public ModelAnimationSampler(ModelAnimationInterpolation interpolation, int outputComponentCount,
                                 float[] keyTimes, float[] outputValues) {
        if (interpolation == null || keyTimes == null || outputValues == null) {
            throw new IllegalArgumentException("animation sampler fields must not be null");
        }
        if (outputComponentCount < 1) throw new IllegalArgumentException("outputComponentCount must be positive");
        int multiplier = interpolation == ModelAnimationInterpolation.CUBIC_SPLINE ? 3 : 1;
        long expected = Math.multiplyExact(Math.multiplyExact((long) keyTimes.length, outputComponentCount), multiplier);
        if (keyTimes.length == 0 || outputValues.length != expected) {
            throw new IllegalArgumentException("animation output length does not match key count");
        }
        float previous = -Float.MAX_VALUE;
        for (float time : keyTimes) {
            if (!Float.isFinite(time) || time < 0.0F || time <= previous) {
                throw new IllegalArgumentException("animation key times must be finite, non-negative and strictly increasing");
            }
            previous = time;
        }
        for (float value : outputValues) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("animation output values must be finite");
        }
        this.interpolation = interpolation;
        this.outputComponentCount = outputComponentCount;
        this.keyTimes = Arrays.copyOf(keyTimes, keyTimes.length);
        this.outputValues = Arrays.copyOf(outputValues, outputValues.length);
    }

    public ModelAnimationInterpolation interpolation() { return interpolation; }
    public int outputComponentCount() { return outputComponentCount; }
    public int keyCount() { return keyTimes.length; }
    public float keyTime(int index) { return keyTimes[index]; }
    public float outputValue(int keyIndex, int component) {
        return outputValues[Math.addExact(Math.multiplyExact(keyIndex, outputComponentCount), component)];
    }
    public float startTime() { return keyTimes[0]; }
    public float endTime() { return keyTimes[keyTimes.length - 1]; }
    public float[] keyTimes() { return Arrays.copyOf(keyTimes, keyTimes.length); }
    public float[] outputValues() { return Arrays.copyOf(outputValues, outputValues.length); }
}
