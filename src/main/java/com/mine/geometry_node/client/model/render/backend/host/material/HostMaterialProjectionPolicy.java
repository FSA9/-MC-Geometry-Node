package com.mine.geometry_node.client.model.render.backend.host.material;

import com.mine.geometry_node.client.model.runtime.StaticModelMaterial;
import com.mine.geometry_node.client.model.runtime.StaticModelTexture;
import com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode;

import java.util.Objects;

/** Shared texture-coordinate and occlusion policy for all HOST asset consumers. */
public final class HostMaterialProjectionPolicy {
    private HostMaterialProjectionPolicy() {
    }

    public static StaticModelTexture renderCoordinateSource(StaticModelMaterial material) {
        Objects.requireNonNull(material, "material");
        StaticModelTexture[] textures = {material.baseColorTexture(), material.metallicRoughnessTexture(),
                material.normalTexture(), material.occlusionTexture(), material.emissiveTexture()};
        for (StaticModelTexture texture : textures) {
            if (texture.present()) return texture;
        }
        return StaticModelTexture.absent();
    }

    /** Alpha coverage is defined only by the base-color texture in glTF material semantics. */
    public static StaticModelTexture occlusionAlphaSource(StaticModelMaterial material) {
        Objects.requireNonNull(material, "material");
        return material.baseColorTexture().present()
                ? material.baseColorTexture() : StaticModelTexture.absent();
    }

    public static HostOcclusionClass occlusionClass(StaticModelMaterial material) {
        Objects.requireNonNull(material, "material");
        ModelAlphaMode alphaMode = material.alphaMode();
        if (alphaMode == ModelAlphaMode.BLEND) return HostOcclusionClass.TRANSMISSIVE;
        if (alphaMode == ModelAlphaMode.MASK) return HostOcclusionClass.MASK_COVERAGE_REQUIRED;
        return HostOcclusionClass.OPAQUE_BLOCKER;
    }

    public static boolean compatibleCoordinates(StaticModelTexture texture, StaticModelTexture coordinate) {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(coordinate, "coordinate");
        return texture.present() && texture.texCoord() == coordinate.texCoord()
                && texture.transform().equals(coordinate.transform())
                && texture.sampler().equals(coordinate.sampler());
    }
}
