package com.mine.geometry_node.client.model.render.backend.host.light.integration;

import com.mine.geometry_node.client.model.render.backend.host.iris.entity.IrisEntityTranslucency;
import com.mine.geometry_node.client.model.render.backend.host.iris.labpbr.IrisLabPbrProjector;
import com.mine.geometry_node.client.model.render.backend.host.iris.labpbr.ModelProjectorCapability;
import com.mine.geometry_node.client.model.render.backend.host.iris.shadow.IrisShadowAdapter;
import com.mine.geometry_node.client.model.render.integration.ModelIntegrationController;
import com.mine.geometry_node.client.model.runtime.ModelResourceReloadListener;

import java.util.Objects;

/** Captures all existing Iris/HOST evidence once; render callbacks consume one immutable generation. */
public final class HostLightingEnvironment {
    private static long generation;
    private static volatile HostLightingEnvironmentSnapshot current = unavailable(0, 0, 0);

    private HostLightingEnvironment() {
    }

    public static synchronized HostLightingEnvironmentSnapshot captureFrameEnvironment() {
        long reloadGeneration = ModelResourceReloadListener.reloadGeneration();
        long integrationGeneration = ModelIntegrationController.integrationStatus().generation();
        boolean hostNativeRequired = ModelIntegrationController.requiresCompatibilityBackend();
        IrisLabPbrProjector.Snapshot projector = hostNativeRequired
                ? IrisLabPbrProjector.snapshot(reloadGeneration)
                : new IrisLabPbrProjector.Snapshot(reloadGeneration, ModelProjectorCapability.INACTIVE,
                        "HOST_NATIVE_INACTIVE");
        IrisEntityTranslucency.Snapshot translucency = hostNativeRequired
                ? IrisEntityTranslucency.snapshot()
                : new IrisEntityTranslucency.Snapshot(false, "HOST_NATIVE_INACTIVE");
        return acceptObservation(reloadGeneration, integrationGeneration, hostNativeRequired,
                projector, translucency, shadowEvidence(hostNativeRequired));
    }

    static synchronized HostLightingEnvironmentSnapshot acceptObservation(
            long reloadGeneration,
            long integrationGeneration,
            boolean hostNativeRequired,
            IrisLabPbrProjector.Snapshot projector,
            IrisEntityTranslucency.Snapshot translucency,
            HostLightingEnvironmentSnapshot.ShadowEvidence shadow) {
        HostLightingEnvironmentSnapshot observed = new HostLightingEnvironmentSnapshot(
                current.generation(), reloadGeneration, integrationGeneration, hostNativeRequired,
                projector, translucency, shadow);
        if (current.equals(observed)) return current;
        long nextGeneration = sameCapabilities(current, observed) ? current.generation() : ++generation;
        current = new HostLightingEnvironmentSnapshot(nextGeneration, reloadGeneration, integrationGeneration,
                hostNativeRequired, projector, translucency, shadow);
        return current;
    }

    public static HostLightingEnvironmentSnapshot snapshot() {
        return current;
    }

    /** Fail-closed invalidation used by resource reload and world-session teardown. */
    public static synchronized void invalidate(long resourceReloadGeneration) {
        if (resourceReloadGeneration < 0) {
            throw new IllegalArgumentException("resourceReloadGeneration must not be negative");
        }
        current = unavailable(++generation, resourceReloadGeneration,
                ModelIntegrationController.integrationStatus().generation());
    }

    private static HostLightingEnvironmentSnapshot.ShadowEvidence shadowEvidence(boolean hostNativeRequired) {
        if (!hostNativeRequired) {
            return new HostLightingEnvironmentSnapshot.ShadowEvidence(
                    IrisShadowAdapter.installed(), null, "", 0, false);
        }
        return new HostLightingEnvironmentSnapshot.ShadowEvidence(
                IrisShadowAdapter.installed(), IrisShadowAdapter.capabilities(), IrisShadowAdapter.failure(),
                IrisShadowAdapter.lastSubmittedDraws(), IrisShadowAdapter.translucentPhaseObserved());
    }

    private static boolean sameCapabilities(HostLightingEnvironmentSnapshot left,
                                            HostLightingEnvironmentSnapshot right) {
        return left.resourceReloadGeneration() == right.resourceReloadGeneration()
                && left.integrationGeneration() == right.integrationGeneration()
                && left.hostNativeRequired() == right.hostNativeRequired()
                && left.projector().equals(right.projector())
                && left.translucency().equals(right.translucency())
                && left.shadow().installed() == right.shadow().installed()
                && Objects.equals(left.shadow().capabilities(), right.shadow().capabilities())
                && left.shadow().failure().equals(right.shadow().failure());
    }

    private static HostLightingEnvironmentSnapshot unavailable(long environmentGeneration,
                                                               long resourceReloadGeneration,
                                                               long integrationGeneration) {
        return new HostLightingEnvironmentSnapshot(environmentGeneration, resourceReloadGeneration,
                integrationGeneration, false,
                new IrisLabPbrProjector.Snapshot(resourceReloadGeneration, ModelProjectorCapability.ABSENT,
                        "LIGHTING_ENVIRONMENT_INVALIDATED"),
                new IrisEntityTranslucency.Snapshot(false, "LIGHTING_ENVIRONMENT_INVALIDATED"),
                new HostLightingEnvironmentSnapshot.ShadowEvidence(
                        false, null, "LIGHTING_ENVIRONMENT_INVALIDATED", 0, false));
    }
}
