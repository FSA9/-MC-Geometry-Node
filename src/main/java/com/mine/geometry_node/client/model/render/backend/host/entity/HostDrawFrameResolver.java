package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightBinding;
import com.mine.geometry_node.client.model.runtime.ModelInstancePlacement;
import com.mine.geometry_node.client.model.runtime.StaticModelMaterial;
import com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/** Authoritative pure resolver for frame-dependent HOST draw values. */
public final class HostDrawFrameResolver {
    private HostDrawFrameResolver() {}

    public static Optional<HostResolvedDraw> resolve(HostDrawPlan.Draw draw,
                                                     ModelInstancePlacement placement,
                                                     Matrix4fc nodeWorld,
                                                     double cameraX, double cameraY, double cameraZ,
                                                     int requestedLod,
                                                     boolean preserveBlend,
                                                     boolean materialFallback,
                                                     ToIntFunction<Vector3d> packedLightAt) {
        return resolve(draw, placement, nodeWorld, cameraX, cameraY, cameraZ, requestedLod,
                preserveBlend, materialFallback, HostLightBinding::constant, packedLightAt);
    }

    public static Optional<HostResolvedDraw> resolve(HostDrawPlan.Draw draw,
                                                     ModelInstancePlacement placement,
                                                     Matrix4fc nodeWorld,
                                                     double cameraX, double cameraY, double cameraZ,
                                                     int requestedLod,
                                                     boolean preserveBlend,
                                                     boolean materialFallback,
                                                     IntFunction<HostLightBinding> lightBindingAt,
                                                     ToIntFunction<Vector3d> packedLightAt) {
        Objects.requireNonNull(draw, "draw");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(nodeWorld, "nodeWorld");
        Objects.requireNonNull(lightBindingAt, "lightBindingAt");
        Objects.requireNonNull(packedLightAt, "packedLightAt");
        if (draw.geometry() == null) return Optional.empty();
        Optional<HostDrawTransform> transform = HostDrawTransform.resolve(
                placement, nodeWorld, draw.localCenter(), cameraX, cameraY, cameraZ);
        if (transform.isEmpty()) return Optional.empty();

        StaticModelMaterial material = draw.material();
        boolean effectiveTranslucent = material.alphaMode() == ModelAlphaMode.BLEND
                || placement.alpha() < 0.999F;
        boolean opaqueFallback = effectiveTranslucent && !preserveBlend;
        HostLightBinding light;
        if (placement.fullBright()) {
            light = HostLightBinding.fullBright();
        } else {
            int fallback = packedLightAt.applyAsInt(transform.get().worldCenter());
            light = Objects.requireNonNull(lightBindingAt.apply(fallback), "resolved light binding");
        }
        float red = materialFallback ? 1 : material.red() * placement.red();
        float green = materialFallback ? 1 : material.green() * placement.green();
        float blue = materialFallback ? 1 : material.blue() * placement.blue();
        float alpha = opaqueFallback || materialFallback ? 1
                : (material.alphaMode() == ModelAlphaMode.OPAQUE ? 1 : material.alpha()) * placement.alpha();
        try {
            return Optional.of(new HostResolvedDraw(transform.get(), draw.geometry().lod().level(requestedLod),
                    light, red, green, blue, alpha, effectiveTranslucent, opaqueFallback, materialFallback));
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }
}
