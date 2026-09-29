package com.mine.geometry_node.core.engine.system.model.capability;
import java.util.*;
/** Evidence is per pack + feature. API availability cannot promote a feature to VERIFIED. */
public final class ModelCapabilities {
    public enum Feature { MAIN_PASS, DEPTH, SMOOTH_NORMALS, SPATIAL_LIGHT, MASK, BLEND, SUN_SHADOW,
        TRANSLUCENT_SHADOW, PBR, LOCAL_LIGHT_OCCLUSION, RELOAD }
    public enum Status { UNTESTED, API_AVAILABLE, VERIFIED, DEGRADED, UNSUPPORTED, FAILED }
    public record Evidence(Status status, String detail) {
        public Evidence { Objects.requireNonNull(status); Objects.requireNonNull(detail); }
    }
    private final Map<Feature,Evidence> features;
    public ModelCapabilities(Map<Feature,Evidence> features) {
        var complete=new EnumMap<Feature,Evidence>(Feature.class);
        for(var f:Feature.values()) complete.put(f,new Evidence(Status.UNTESTED,"no visual evidence recorded"));
        complete.putAll(features); this.features=Collections.unmodifiableMap(complete);
    }
    public Map<Feature,Evidence> features() { return features; }
}
