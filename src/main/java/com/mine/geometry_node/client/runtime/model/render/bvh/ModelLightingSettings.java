package com.mine.geometry_node.client.runtime.model.render.bvh;

import java.util.HashMap;
import java.util.Map;

/** Session settings keyed by placed-instance ID, never by shared asset identity. */
public final class ModelLightingSettings {
    public record Settings(boolean localEnabled, boolean giEnabled, ModelLightingQuality quality) {}
    private record Override(Boolean local, Boolean gi, Boolean automatic, Integer samples, Float resolution) {
        boolean hasLocal() { return local != null || automatic != null || samples != null || resolution != null; }
        boolean empty() { return !hasLocal() && gi == null; }
    }
    private static final Override EMPTY = new Override(null, null, null, null, null);
    private static final Map<String, Override> overrides = new HashMap<>();
    private static boolean localEnabled = true, giEnabled = true;
    private static boolean overridesSuspended;
    private static long revision;

    public static Settings global() { return new Settings(localEnabled, giEnabled, ModelLightingQuality.active()); }
    public static Settings effective(String id) {
        Settings global = global();
        Override value = overrides.get(id);
        if (value == null || overridesSuspended) return global;
        var base = global.quality();
        return new Settings(value.local == null ? global.localEnabled() : value.local,
                value.gi == null ? global.giEnabled() : value.gi,
                new ModelLightingQuality(value.automatic == null ? base.automatic() : value.automatic,
                        value.samples == null ? base.areaSamples() : value.samples,
                        value.resolution == null ? base.resolutionScale() : value.resolution));
    }
    public static void setLocalEnabled(String id, boolean enabled) {
        if (id == null) { localEnabled = enabled; revision++; return; }
        var v = overrides.getOrDefault(id, EMPTY);
        put(id, new Override(enabled, v.gi, v.automatic, v.samples, v.resolution));
    }
    public static void setGiEnabled(String id, boolean enabled) {
        if (id == null) { giEnabled = enabled; revision++; return; }
        var v = overrides.getOrDefault(id, EMPTY);
        put(id, new Override(v.local, enabled, v.automatic, v.samples, v.resolution));
    }
    public static void setSamples(String id, int samples) {
        new ModelLightingQuality(samples, 1);
        if (id == null) { ModelLightingQuality.setActive(ModelLightingQuality.active().withSamples(samples)); return; }
        var v = overrides.getOrDefault(id, EMPTY);
        put(id, new Override(v.local, v.gi, false, samples, v.resolution));
    }
    public static void setResolution(String id, float resolution) {
        new ModelLightingQuality(4, resolution);
        if (id == null) { ModelLightingQuality.setActive(ModelLightingQuality.active().withResolutionScale(resolution)); return; }
        var v = overrides.getOrDefault(id, EMPTY);
        put(id, new Override(v.local, v.gi, false, v.samples, resolution));
    }
    public static void setAutomatic(String id) {
        if (id == null) { localEnabled = true; ModelLightingQuality.setActive(ModelLightingQuality.active().withAutomatic(true)); revision++; return; }
        var v = overrides.getOrDefault(id, EMPTY);
        put(id, new Override(true, v.gi, true, v.samples, v.resolution));
    }
    public static void resetLocal(String id) {
        if (id == null) { localEnabled = true; ModelLightingQuality.setActive(ModelLightingQuality.DEFAULT); revision++; return; }
        var v = overrides.getOrDefault(id, EMPTY);
        put(id, new Override(null, v.gi, null, null, null));
    }
    public static void resetGi(String id) {
        if (id == null) { giEnabled = true; revision++; return; }
        var v = overrides.getOrDefault(id, EMPTY);
        put(id, new Override(v.local, null, v.automatic, v.samples, v.resolution));
    }
    public static boolean hasLocalOverride(String id) { var v = overrides.get(id); return v != null && v.hasLocal(); }
    public static boolean hasGiOverride(String id) { var v = overrides.get(id); return v != null && v.gi != null; }
    public static int overrideCount() { return overrides.size(); }
    public static long revision() { return revision; }
    public static void setOverridesSuspended(boolean suspended) { overridesSuspended = suspended; revision++; }
    public static boolean overridesSuspended() { return overridesSuspended; }
    public static void removeInstance(String id) { if (overrides.remove(id) != null) revision++; }
    public static void clearInstances() { overrides.clear(); revision++; }
    public static boolean autoDisabled(boolean inFrustum, double distance) {
        return !inFrustum && Double.isFinite(distance) && distance > 32;
    }
    private static void put(String id, Override value) {
        if (value.empty()) overrides.remove(id); else overrides.put(id, value);
        revision++;
    }
    private ModelLightingSettings() {}
}
