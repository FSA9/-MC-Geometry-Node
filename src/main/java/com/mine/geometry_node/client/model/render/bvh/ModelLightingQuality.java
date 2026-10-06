package com.mine.geometry_node.client.model.render.bvh;

import com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.Map;
import java.util.Objects;

/** Session-only local direct-light settings, independent of sunlight shadow rendering. */
public record ModelLightingQuality(boolean automatic, int areaSamples, float resolutionScale) {
    public static final ModelLightingQuality DEFAULT = new ModelLightingQuality(true, 4, 1.0f);

    private static ModelLightingQuality active = DEFAULT;

    public ModelLightingQuality(int areaSamples, float resolutionScale) {
        this(false, areaSamples, resolutionScale);
    }

    public ModelLightingQuality {
        if (areaSamples < 0 || areaSamples > 4)
            throw new IllegalArgumentException("area samples must be in [0,4]");
        if (!Float.isFinite(resolutionScale) || resolutionScale < 0.0f || resolutionScale > 1.0f)
            throw new IllegalArgumentException("resolution scale must be finite and in [0,1]");
        if (resolutionScale == 0.0f) resolutionScale = 0.0f;
    }

    public ModelLightingQuality withSamples(int samples) { return new ModelLightingQuality(samples, resolutionScale); }
    public ModelLightingQuality withResolutionScale(float scale) { return new ModelLightingQuality(areaSamples, scale); }
    public ModelLightingQuality withAutomatic(boolean value) { return new ModelLightingQuality(value, areaSamples, resolutionScale); }
    public int samplesAt(double distance) {
        if (!automatic) return areaSamples;
        if (!Double.isFinite(distance) || distance < 0) return 4;
        return distance <= 32 ? 4 : distance <= 64 ? 3 : distance <= 96 ? 2 : distance <= 128 ? 1 : 0;
    }
    public float resolutionAt(double distance) {
        if (!automatic) return areaSamples == 0 ? 0 : resolutionScale;
        if (!Double.isFinite(distance) || distance <= 32) return 1;
        return distance > 128 ? 0 : (float) (1 - (distance - 32) / 192);
    }
    public int uniformValue() { return areaSamples; }
    public String id() { return automatic ? "auto" : "samples_" + areaSamples + "_scale_" + Float.toString(resolutionScale); }
    public static ModelLightingQuality active() { return active; }

    public static void setActive(ModelLightingQuality quality) {
        RenderSystem.assertOnRenderThread();
        Objects.requireNonNull(quality);
        if (active.equals(quality)) return;
        active = quality;
        ModelBvhLightResolution.settingsChanged();
        ModelShaderGpuTimings.clearSamples();
    }

    public Map<String, Object> report() {
        return Map.of("automatic", automatic, "areaSamples", areaSamples, "resolutionScale", resolutionScale,
                "defaultAreaSamples", DEFAULT.areaSamples, "defaultResolutionScale", DEFAULT.resolutionScale,
                "sessionOnly", true);
    }
}
