package com.mine.geometry_node.client.model.render.backend.host.light.asset;

import com.mine.geometry_node.client.model.render.backend.host.material.HostMaterialProjectionPolicy;
import com.mine.geometry_node.client.model.render.backend.host.material.HostOcclusionClass;
import com.mine.geometry_node.client.model.runtime.StaticModelMaterial;

import java.util.Objects;

/** Frozen render-facing and optical material policy for a lighting surface. */
public record HostLightingMaterial(int materialIndex, HostOcclusionClass occlusionClass,
                                   boolean receiverDoubleSided, boolean opticalDoubleSided,
                                   float alphaCutoff, float transmittance,
                                   MaskCoverage maskCoverage) {
    public HostLightingMaterial {
        Objects.requireNonNull(occlusionClass, "occlusionClass");
        Objects.requireNonNull(maskCoverage, "maskCoverage");
        if (materialIndex < 0 || !Float.isFinite(alphaCutoff)
                || !Float.isFinite(transmittance) || transmittance < 0 || transmittance > 1) {
            throw new IllegalArgumentException("invalid lighting material policy");
        }
    }

    public static HostLightingMaterial from(int materialIndex, StaticModelMaterial material) {
        Objects.requireNonNull(material, "material");
        HostOcclusionClass occlusion = HostMaterialProjectionPolicy.occlusionClass(material);
        return switch (occlusion) {
            case OPAQUE_BLOCKER -> new HostLightingMaterial(materialIndex, occlusion, material.doubleSided(),
                    true, material.alphaCutoff(), 0F, MaskCoverage.NOT_APPLICABLE);
            case MASK_COVERAGE_REQUIRED -> new HostLightingMaterial(materialIndex, occlusion,
                    material.doubleSided(), true, material.alphaCutoff(), 1F, MaskCoverage.PENDING);
            case TRANSMISSIVE -> new HostLightingMaterial(materialIndex, occlusion, material.doubleSided(),
                    false, material.alphaCutoff(), 1F, MaskCoverage.NOT_APPLICABLE);
        };
    }

    public boolean blocksLight() {
        return occlusionClass == HostOcclusionClass.OPAQUE_BLOCKER && transmittance == 0F;
    }

    public enum MaskCoverage { NOT_APPLICABLE, PENDING, READY }
}
