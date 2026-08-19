package com.mine.geometry_node.client.model.render.backend.host.light.integration;

import com.mine.geometry_node.client.model.render.backend.host.iris.entity.IrisEntityTranslucency;
import com.mine.geometry_node.client.model.render.backend.host.iris.labpbr.IrisLabPbrProjector;
import com.mine.geometry_node.client.model.render.backend.host.iris.shadow.IrisShadowCapabilities;

import java.util.Objects;

/** Immutable, frame-stable evidence about the current NATIVE lighting environment. */
public record HostLightingEnvironmentSnapshot(long generation,
                                              long resourceReloadGeneration,
                                              long integrationGeneration,
                                              boolean hostNativeRequired,
                                              IrisLabPbrProjector.Snapshot projector,
                                              IrisEntityTranslucency.Snapshot translucency,
                                              ShadowEvidence shadow) {
    public HostLightingEnvironmentSnapshot {
        if (generation < 0 || resourceReloadGeneration < 0 || integrationGeneration < 0) {
            throw new IllegalArgumentException("lighting environment generations must not be negative");
        }
        Objects.requireNonNull(projector, "projector");
        Objects.requireNonNull(translucency, "translucency");
        Objects.requireNonNull(shadow, "shadow");
    }

    /** Shadow replay evidence. A verified replay proves callback submission, not pack receiver or voxel support. */
    public record ShadowEvidence(boolean installed,
                                 IrisShadowCapabilities capabilities,
                                 String failure,
                                 boolean replayVerified,
                                 int submittedDraws,
                                 boolean translucentPhaseObserved) {
        public ShadowEvidence {
            failure = failure == null ? "" : failure;
            if (submittedDraws < 0) throw new IllegalArgumentException("submittedDraws must not be negative");
        }
    }
}
