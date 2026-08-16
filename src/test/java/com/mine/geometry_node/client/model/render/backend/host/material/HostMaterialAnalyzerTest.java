package com.mine.geometry_node.client.model.render.backend.host.material;

import com.mine.geometry_node.client.model.render.integration.ModelCompatibilityLoss;
import com.mine.geometry_node.client.model.runtime.StaticModelMaterial;
import com.mine.geometry_node.client.model.runtime.StaticModelTexture;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class HostMaterialAnalyzerTest {
    @Test
    void reportsNonDefaultCutoffSamplerAndTransformWithoutRejectingRigidDraw() {
        StaticModelTexture base = new StaticModelTexture(0,
                new ModelTextureSampler(ModelTextureWrap.CLAMP_TO_EDGE, ModelTextureWrap.REPEAT,
                        ModelTextureFilter.LINEAR, ModelTextureFilter.LINEAR), 2,
                new ModelTextureTransform(0.25F, 0, 0, 1, 1));
        StaticModelMaterial material = new StaticModelMaterial(1, 1, 1, 1, base,
                ModelAlphaMode.MASK, 0.25F, true, 0, 0, 0, StaticModelTexture.absent());

        HostMaterialProjection result = HostMaterialAnalyzer.analyze(
                HostMaterialProfile.HOST_NATIVE_ENTITY, material, false);

        assertTrue(result.selectable());
        assertEquals(2, result.projectedUvSet());
        assertTrue(result.losses().contains(ModelCompatibilityLoss.ALPHA_CUTOFF_APPROXIMATED));
        assertTrue(result.losses().contains(ModelCompatibilityLoss.TEXTURE_SAMPLER_APPROXIMATED));
        assertFalse(result.losses().contains(ModelCompatibilityLoss.TEXTURE_TRANSFORM_UNREPRESENTABLE));
        assertTrue(result.losses().contains(ModelCompatibilityLoss.SIDED_NORMAL_APPROXIMATED));
    }

    @Test
    void rejectsOnlySkinnedDrawAndReportsPbrLossesIndependently() {
        StaticModelMaterial material = new StaticModelMaterial(1, 1, 1, 1, StaticModelTexture.absent(),
                ModelAlphaMode.OPAQUE, 0.5F, false, 0, 0, 0, StaticModelTexture.absent(),
                0.5F, 0.2F, StaticModelTexture.absent(), StaticModelTexture.absent(), 1,
                StaticModelTexture.absent(), 1);
        HostMaterialProjection result = HostMaterialAnalyzer.analyze(
                HostMaterialProfile.HOST_NATIVE_ENTITY, material, true);
        assertFalse(result.selectable());
        assertFalse(result.selectable());
        assertFalse(result.losses().contains(ModelCompatibilityLoss.GPU_SKINNING_UNREPRESENTABLE));
        assertTrue(result.losses().contains(ModelCompatibilityLoss.METALLIC_UNREPRESENTABLE));
        assertTrue(result.losses().contains(ModelCompatibilityLoss.ROUGHNESS_UNREPRESENTABLE));
        assertFalse(result.losses().contains(ModelCompatibilityLoss.METALLIC_ROUGHNESS_APPROXIMATED));
    }


    @Test
    void labPbrFactorOnlyMetallicEndpointsDoNotReportLoss() {
        for (float endpoint : new float[]{0, 1}) {
            StaticModelMaterial material = new StaticModelMaterial(1, 1, 1, 1, StaticModelTexture.absent(),
                    ModelAlphaMode.OPAQUE, 0.5F, false, 0, 0, 0, StaticModelTexture.absent(),
                    endpoint, 1, StaticModelTexture.absent(), StaticModelTexture.absent(), 1,
                    StaticModelTexture.absent(), 1);
            HostMaterialProjection result = HostMaterialAnalyzer.analyze(
                    HostMaterialProfile.HOST_NATIVE_LABPBR, material, false);
            assertFalse(result.losses().contains(ModelCompatibilityLoss.METALLIC_UNREPRESENTABLE));
        }
    }

    @Test
    void labPbrFactorOnlyIntermediateMetallicReportsLoss() {
        StaticModelMaterial material = new StaticModelMaterial(1, 1, 1, 1, StaticModelTexture.absent(),
                ModelAlphaMode.OPAQUE, 0.5F, false, 0, 0, 0, StaticModelTexture.absent(),
                0.5F, 1, StaticModelTexture.absent(), StaticModelTexture.absent(), 1,
                StaticModelTexture.absent(), 1);
        HostMaterialProjection result = HostMaterialAnalyzer.analyze(
                HostMaterialProfile.HOST_NATIVE_LABPBR, material, false);
        assertTrue(result.losses().contains(ModelCompatibilityLoss.METALLIC_UNREPRESENTABLE));
    }

    @Test
    void labPbrProjectsOnlyRolesSharingTheSelectedUvContract() {
        ModelTextureSampler sampler = new ModelTextureSampler(ModelTextureWrap.REPEAT, ModelTextureWrap.REPEAT,
                ModelTextureFilter.LINEAR_MIPMAP_LINEAR, ModelTextureFilter.LINEAR);
        StaticModelTexture base = new StaticModelTexture(0, sampler, 0, ModelTextureTransform.identity());
        StaticModelTexture normal = new StaticModelTexture(1, sampler, 0, ModelTextureTransform.identity());
        StaticModelTexture ao = new StaticModelTexture(2, sampler, 1, ModelTextureTransform.identity());
        StaticModelMaterial material = new StaticModelMaterial(1, 1, 1, 1, base,
                ModelAlphaMode.OPAQUE, 0.5F, false, 0, 0, 0, StaticModelTexture.absent(),
                0, 0.6F, StaticModelTexture.absent(), normal, 1, ao, 1);

        HostMaterialProjection result = HostMaterialAnalyzer.analyze(
                HostMaterialProfile.HOST_NATIVE_LABPBR, material, false);

        assertTrue(result.selectable());
        assertTrue(result.losses().contains(ModelCompatibilityLoss.NORMAL_TEXTURE_APPROXIMATED));
        assertTrue(result.losses().contains(ModelCompatibilityLoss.ROUGHNESS_APPROXIMATED));
        assertTrue(result.losses().contains(ModelCompatibilityLoss.METALLIC_UNREPRESENTABLE));
        assertTrue(result.losses().contains(ModelCompatibilityLoss.OCCLUSION_TEXTURE_UNREPRESENTABLE));
        assertTrue(result.losses().contains(ModelCompatibilityLoss.INDEPENDENT_UV_UNREPRESENTABLE));
        assertFalse(result.losses().contains(ModelCompatibilityLoss.LABPBR_SHADERPACK_CONSUMPTION_UNVERIFIED));
    }

    @Test
    void labPbrDoesNotProjectRoleWithIndependentSampler() {
        ModelTextureSampler selectedSampler = new ModelTextureSampler(ModelTextureWrap.REPEAT, ModelTextureWrap.REPEAT,
                ModelTextureFilter.LINEAR_MIPMAP_LINEAR, ModelTextureFilter.LINEAR);
        ModelTextureSampler independentSampler = new ModelTextureSampler(ModelTextureWrap.CLAMP_TO_EDGE,
                ModelTextureWrap.REPEAT, ModelTextureFilter.LINEAR_MIPMAP_LINEAR, ModelTextureFilter.LINEAR);
        StaticModelTexture base = new StaticModelTexture(0, selectedSampler, 0, ModelTextureTransform.identity());
        StaticModelTexture normal = new StaticModelTexture(1, independentSampler, 0, ModelTextureTransform.identity());
        StaticModelMaterial material = new StaticModelMaterial(1, 1, 1, 1, base,
                ModelAlphaMode.OPAQUE, 0.5F, false, 0, 0, 0, StaticModelTexture.absent(),
                0, 1, StaticModelTexture.absent(), normal, 1, StaticModelTexture.absent(), 1);

        HostMaterialProjection result = HostMaterialAnalyzer.analyze(
                HostMaterialProfile.HOST_NATIVE_LABPBR, material, false);

        assertTrue(result.losses().contains(ModelCompatibilityLoss.NORMAL_TEXTURE_UNREPRESENTABLE));
        assertTrue(result.losses().contains(ModelCompatibilityLoss.TEXTURE_SAMPLER_APPROXIMATED));
        assertFalse(result.losses().contains(ModelCompatibilityLoss.NORMAL_TEXTURE_APPROXIMATED));
    }

    @Test
    void sharedGltfDefaultSamplerKeepsPbrRolesProjectedWithOneSamplerLoss() {
        ModelTextureSampler sampler = ModelTextureSampler.gltfDefault();
        StaticModelTexture base = new StaticModelTexture(0, sampler, 0, ModelTextureTransform.identity());
        StaticModelTexture mr = new StaticModelTexture(1, sampler, 0, ModelTextureTransform.identity());
        StaticModelTexture normal = new StaticModelTexture(2, sampler, 0, ModelTextureTransform.identity());
        StaticModelMaterial material = new StaticModelMaterial(1, 1, 1, 1, base,
                ModelAlphaMode.OPAQUE, 0.5F, false, 0, 0, 0, StaticModelTexture.absent(),
                1, 1, mr, normal, 1, StaticModelTexture.absent(), 1);

        HostMaterialProjection result = HostMaterialAnalyzer.analyze(
                HostMaterialProfile.HOST_NATIVE_LABPBR, material, false);

        assertTrue(result.losses().contains(ModelCompatibilityLoss.ROUGHNESS_APPROXIMATED));
        assertTrue(result.losses().contains(ModelCompatibilityLoss.NORMAL_TEXTURE_APPROXIMATED));
        assertTrue(result.losses().contains(ModelCompatibilityLoss.TEXTURE_SAMPLER_APPROXIMATED));
        assertFalse(result.losses().contains(ModelCompatibilityLoss.ROUGHNESS_UNREPRESENTABLE));
        assertFalse(result.losses().contains(ModelCompatibilityLoss.NORMAL_TEXTURE_UNREPRESENTABLE));
    }

    @Test
    void reportsSingleSidedBlendAsAQualifiedSidednessLoss() {
        StaticModelMaterial material = new StaticModelMaterial(1, 1, 1, 0.5F, StaticModelTexture.absent(),
                ModelAlphaMode.BLEND, 0.5F, false, 0, 0, 0, StaticModelTexture.absent());
        HostMaterialProjection result = HostMaterialAnalyzer.analyze(
                HostMaterialProfile.HOST_NATIVE_ENTITY, material, false);
        assertTrue(result.selectable());
        assertFalse(result.doubleSided());
        assertTrue(result.losses().contains(ModelCompatibilityLoss.SINGLE_SIDED_TRANSLUCENCY_UNREPRESENTABLE));
        assertTrue(result.losses().contains(ModelCompatibilityLoss.TRANSPARENT_ORDERING_APPROXIMATED));
    }

    @Test
    void cutoutReportsMinecraftFixedThresholdInsteadOfGltfDefault() {
        StaticModelMaterial gltfDefault = new StaticModelMaterial(1, 1, 1, 1, StaticModelTexture.absent(),
                ModelAlphaMode.MASK, 0.5F, false, 0, 0, 0, StaticModelTexture.absent());
        StaticModelMaterial minecraftThreshold = new StaticModelMaterial(1, 1, 1, 1, StaticModelTexture.absent(),
                ModelAlphaMode.MASK, 0.1F, false, 0, 0, 0, StaticModelTexture.absent());
        assertTrue(HostMaterialAnalyzer.analyze(HostMaterialProfile.HOST_NATIVE_ENTITY, gltfDefault, false)
                .losses().contains(ModelCompatibilityLoss.ALPHA_CUTOFF_APPROXIMATED));
        assertFalse(HostMaterialAnalyzer.analyze(HostMaterialProfile.HOST_NATIVE_ENTITY, minecraftThreshold, false)
                .losses().contains(ModelCompatibilityLoss.ALPHA_CUTOFF_APPROXIMATED));
    }

    @Test
    void independentMetallicRoughnessCoordinatesLoseBothRoles() {
        ModelTextureSampler sampler = ModelTextureSampler.gltfDefault();
        StaticModelTexture base = new StaticModelTexture(0, sampler, 0, ModelTextureTransform.identity());
        StaticModelTexture mr = new StaticModelTexture(1, sampler, 1, ModelTextureTransform.identity());
        StaticModelMaterial material = new StaticModelMaterial(1, 1, 1, 1, base,
                ModelAlphaMode.OPAQUE, 0.5F, false, 0, 0, 0, StaticModelTexture.absent(),
                1, 1, mr, StaticModelTexture.absent(), 1, StaticModelTexture.absent(), 1);
        HostMaterialProjection result = HostMaterialAnalyzer.analyze(
                HostMaterialProfile.HOST_NATIVE_LABPBR, material, false);
        assertTrue(result.losses().contains(ModelCompatibilityLoss.METALLIC_UNREPRESENTABLE));
        assertTrue(result.losses().contains(ModelCompatibilityLoss.ROUGHNESS_UNREPRESENTABLE));
        assertTrue(result.losses().contains(ModelCompatibilityLoss.INDEPENDENT_UV_UNREPRESENTABLE));
    }

    @Test
    void instanceAlphaAddsEffectiveTransparencyLosses() {
        StaticModelMaterial singleSided = new StaticModelMaterial(1, 1, 1, 1, StaticModelTexture.absent(),
                ModelAlphaMode.OPAQUE, 0.5F, false, 0, 0, 0, StaticModelTexture.absent());
        EnumSet<ModelCompatibilityLoss> losses = EnumSet.noneOf(ModelCompatibilityLoss.class);
        HostMaterialAnalyzer.addInstanceLosses(singleSided, 0.5F, false, losses);
        assertTrue(losses.contains(ModelCompatibilityLoss.TRANSPARENT_ORDERING_APPROXIMATED));
        assertTrue(losses.contains(ModelCompatibilityLoss.SINGLE_SIDED_TRANSLUCENCY_UNREPRESENTABLE));

        losses.clear();
        HostMaterialAnalyzer.addInstanceLosses(singleSided, 0.5F, true, losses);
        assertTrue(losses.contains(ModelCompatibilityLoss.TRANSPARENT_ORDERING_APPROXIMATED));
        assertFalse(losses.contains(ModelCompatibilityLoss.SINGLE_SIDED_TRANSLUCENCY_UNREPRESENTABLE));
    }

    @Test
    void instanceLossesIgnoreAlreadyBlendMaterialAndOpaquePlacement() {
        StaticModelMaterial blend = new StaticModelMaterial(1, 1, 1, 0.5F, StaticModelTexture.absent(),
                ModelAlphaMode.BLEND, 0.5F, false, 0, 0, 0, StaticModelTexture.absent());
        StaticModelMaterial opaque = new StaticModelMaterial(1, 1, 1, 1, StaticModelTexture.absent(),
                ModelAlphaMode.OPAQUE, 0.5F, false, 0, 0, 0, StaticModelTexture.absent());
        EnumSet<ModelCompatibilityLoss> losses = EnumSet.noneOf(ModelCompatibilityLoss.class);
        HostMaterialAnalyzer.addInstanceLosses(blend, 0.5F, false, losses);
        HostMaterialAnalyzer.addInstanceLosses(opaque, 1, false, losses);
        assertTrue(losses.isEmpty());
    }
}
