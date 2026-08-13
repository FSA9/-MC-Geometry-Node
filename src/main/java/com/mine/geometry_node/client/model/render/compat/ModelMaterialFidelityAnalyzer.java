package com.mine.geometry_node.client.model.render.compat;

import com.mine.geometry_node.client.model.runtime.StaticModelMaterial;
import com.mine.geometry_node.client.model.runtime.StaticModelTexture;
import java.util.EnumSet;

/** Capability analysis for the standard entity compatibility backend, evaluated per draw. */
public final class ModelMaterialFidelityAnalyzer {
    private ModelMaterialFidelityAnalyzer() {
    }

    public static ModelCompatibilityProjection analyze(ModelCompatibilityProfile profile,
                                                        StaticModelMaterial material, boolean skinned) {
        EnumSet<ModelCompatibilityLoss> losses = EnumSet.noneOf(ModelCompatibilityLoss.class);
        if (material.alphaMode() == com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode.MASK
                && Math.abs(material.alphaCutoff() - 0.1F) > 1.0E-6F) {
            losses.add(ModelCompatibilityLoss.ALPHA_CUTOFF_APPROXIMATED);
        }
        if (material.alphaMode() == com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode.BLEND
                && !material.doubleSided()) losses.add(ModelCompatibilityLoss.SINGLE_SIDED_TRANSLUCENCY_UNREPRESENTABLE);
        if (material.alphaMode() == com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode.BLEND) {
            losses.add(ModelCompatibilityLoss.TRANSPARENT_ORDERING_APPROXIMATED);
        }
        if (material.doubleSided()) losses.add(ModelCompatibilityLoss.SIDED_NORMAL_APPROXIMATED);
        boolean labPbr = profile == ModelCompatibilityProfile.HOST_NATIVE_LABPBR;
        boolean roughnessInput = material.roughnessFactor() != 1.0F
                || material.metallicRoughnessTexture().present();
        // Textured metallic requires pixel inspection while constructing the auxiliary.
        // Factor-only endpoint materials can be classified without decoding an image.
        if (!labPbr
                || (material.metallicRoughnessTexture().present()
                    && !compatibleRole(material, material.metallicRoughnessTexture()))
                || (!material.metallicRoughnessTexture().present()
                    && !metallicEndpoint(material.metallicFactor()))) {
            losses.add(ModelCompatibilityLoss.METALLIC_UNREPRESENTABLE);
        }
        if (roughnessInput) {
            losses.add(labPbr && (!material.metallicRoughnessTexture().present()
                    || compatibleRole(material, material.metallicRoughnessTexture()))
                    ? ModelCompatibilityLoss.ROUGHNESS_APPROXIMATED
                    : ModelCompatibilityLoss.ROUGHNESS_UNREPRESENTABLE);
        }
        if (material.normalTexture().present()) losses.add(labPbr && compatibleRole(material, material.normalTexture())
                ? ModelCompatibilityLoss.NORMAL_TEXTURE_APPROXIMATED : ModelCompatibilityLoss.NORMAL_TEXTURE_UNREPRESENTABLE);
        if (material.occlusionTexture().present()) losses.add(labPbr && compatibleRole(material, material.occlusionTexture())
                ? ModelCompatibilityLoss.OCCLUSION_TEXTURE_APPROXIMATED : ModelCompatibilityLoss.OCCLUSION_TEXTURE_UNREPRESENTABLE);
        if (material.emissiveTexture().present() || material.emissiveRed() != 0 || material.emissiveGreen() != 0
                || material.emissiveBlue() != 0) losses.add(ModelCompatibilityLoss.EMISSIVE_TEXTURE_UNREPRESENTABLE);
        int selectedUv = material.baseColorTexture().present() ? material.baseColorTexture().texCoord() : -1;
        StaticModelTexture[] textures = {material.baseColorTexture(), material.metallicRoughnessTexture(),
                material.normalTexture(), material.occlusionTexture(), material.emissiveTexture()};
        for (int role = 0; role < textures.length; role++) {
            StaticModelTexture texture = textures[role];
            if (!texture.present()) continue;
            if (selectedUv < 0) selectedUv = texture.texCoord();
            else if (texture.texCoord() != selectedUv) {
                losses.add(ModelCompatibilityLoss.INDEPENDENT_UV_UNREPRESENTABLE);
            }
            // The ENTITY geometry projection bakes one shared transform into its selected UV stream.
            StaticModelTexture selected = coordinateSource(material);
            if (!texture.transform().equals(selected.transform())) {
                losses.add(ModelCompatibilityLoss.TEXTURE_TRANSFORM_UNREPRESENTABLE);
            }
            if (!texture.sampler().equals(selected.sampler()) || !supportsEntitySampler(texture.sampler())) {
                losses.add(ModelCompatibilityLoss.TEXTURE_SAMPLER_APPROXIMATED);
            }
        }
        boolean sidednessPreserved = !losses.contains(ModelCompatibilityLoss.SINGLE_SIDED_TRANSLUCENCY_UNREPRESENTABLE);
        return new ModelCompatibilityProjection(!skinned, profile, selectedUv,
                true, true, sidednessPreserved, true, !skinned, losses);
    }

    /** Adds losses introduced by instance state after material-level analysis. */
    public static void addInstanceLosses(StaticModelMaterial material, float instanceAlpha,
                                         boolean forceDoubleSided, EnumSet<ModelCompatibilityLoss> losses) {
        if (instanceAlpha >= 0.999F || material.alphaMode()
                == com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode.BLEND) return;
        losses.add(ModelCompatibilityLoss.TRANSPARENT_ORDERING_APPROXIMATED);
        if (!material.doubleSided() && !forceDoubleSided) {
            losses.add(ModelCompatibilityLoss.SINGLE_SIDED_TRANSLUCENCY_UNREPRESENTABLE);
        }
    }

    private static boolean compatibleRole(StaticModelMaterial material, StaticModelTexture role) {
        StaticModelTexture selected = coordinateSource(material);
        return role.texCoord() == selected.texCoord()
                && role.transform().equals(selected.transform())
                && role.sampler().equals(selected.sampler());
    }

    private static StaticModelTexture coordinateSource(StaticModelMaterial material) {
        return material.baseColorTexture().present() ? material.baseColorTexture()
                : material.metallicRoughnessTexture().present() ? material.metallicRoughnessTexture()
                : material.normalTexture().present() ? material.normalTexture()
                : material.occlusionTexture().present() ? material.occlusionTexture() : material.emissiveTexture();
    }

    private static boolean supportsEntitySampler(com.mine.geometry_node.core.engine.system.model.domain.ModelTextureSampler sampler) {
        return sampler.wrapS() == com.mine.geometry_node.core.engine.system.model.domain.ModelTextureWrap.REPEAT
                && sampler.wrapT() == com.mine.geometry_node.core.engine.system.model.domain.ModelTextureWrap.REPEAT
                && sampler.minFilter() == com.mine.geometry_node.core.engine.system.model.domain.ModelTextureFilter.NEAREST
                && sampler.magFilter() == com.mine.geometry_node.core.engine.system.model.domain.ModelTextureFilter.NEAREST;
    }

    private static boolean metallicEndpoint(float value) {
        return value <= 1.0E-6F || value >= 1.0F - 1.0E-6F;
    }

}
