package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightBinding;
import com.mine.geometry_node.client.model.render.backend.host.lod.HostModelLodPlan;
import java.util.Objects;

/** Immutable frame-dependent values shared by immediate and static submission for one draw. */
public record HostResolvedDraw(HostDrawTransform transform,
                               HostModelLodPlan.Level lod,
                               HostLightBinding lightBinding,
                               float red, float green, float blue, float alpha,
                               boolean effectiveTranslucent,
                               boolean opaqueFallback,
                               boolean materialFallback) {
    public HostResolvedDraw {
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(lod, "lod");
        Objects.requireNonNull(lightBinding, "lightBinding");
        requireFinite(red, "red");
        requireFinite(green, "green");
        requireFinite(blue, "blue");
        requireFinite(alpha, "alpha");
    }

    public HostStaticVariantKey staticVariantKey(Object instanceIdentity, long poseRevision,
                                                  int packedOverlay, Object layoutIdentity,
                                                  long layoutGeneration) {
        return new HostStaticVariantKey(instanceIdentity, poseRevision,
                transform.baked(), transform.normal(), packedOverlay, lightBinding, transform.mirrored(),
                red, green, blue, alpha, lod.firstTriangle(), lod.triangleCount(),
                layoutIdentity, layoutGeneration);
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
