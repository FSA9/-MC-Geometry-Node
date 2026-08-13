package com.mine.geometry_node.client.model.render;

import com.mine.geometry_node.client.model.runtime.ModelInstancePlacement;
import com.mine.geometry_node.client.model.runtime.StaticModelMaterial;
import com.mine.geometry_node.client.model.runtime.StaticModelTexture;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import org.joml.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelDrawContractTest {
    private static final ModelVertexLayout LAYOUT = new ModelVertexLayout(List.of(
            new ModelVertexLayoutElement(ModelAttributeSemantic.POSITION, ModelComponentType.FLOAT32, 3, false),
            new ModelVertexLayoutElement(ModelAttributeSemantic.NORMAL, ModelComponentType.FLOAT32, 3, false),
            new ModelVertexLayoutElement(ModelAttributeSemantic.TEXCOORD_0, ModelComponentType.FLOAT32, 2, false),
            new ModelVertexLayoutElement(ModelAttributeSemantic.COLOR_0, ModelComponentType.FLOAT32, 4, false)));

    @Test
    void opaqueCoverageIgnoresMaterialAlphaButInstanceAlphaPromotes() {
        StaticModelMaterial opaque = material(ModelAlphaMode.OPAQUE, 0.2F, false);
        ModelDrawContract solid = ModelDrawContract.resolve(LAYOUT, opaque, placement(false, 1), 0.5F, false);
        ModelDrawContract translucent = ModelDrawContract.resolve(LAYOUT, opaque, placement(false, 0.4F), 0.5F, false);

        assertEquals(1.0F, solid.color().w, 1.0E-6F);
        assertFalse(solid.pipeline().translucent());
        assertEquals(0.4F, translucent.color().w, 1.0E-6F);
        assertTrue(translucent.pipeline().translucent());
        assertEquals(ModelAlphaMode.OPAQUE, translucent.pipeline().alphaMode());
    }

    @Test
    void maskMaterialAlphaAffectsCutoffValueButOnlyInstanceAlphaPromotes() {
        StaticModelMaterial mask = material(ModelAlphaMode.MASK, 0.25F, false);
        ModelDrawContract solid = ModelDrawContract.resolve(LAYOUT, mask, placement(false, 1), 1, false);
        ModelDrawContract translucent = ModelDrawContract.resolve(LAYOUT, mask, placement(false, 0.5F), 1, false);

        assertEquals(0.25F, solid.color().w, 1.0E-6F);
        assertEquals(0.5F, solid.alphaCutoff(), 1.0E-6F);
        assertFalse(solid.pipeline().translucent());
        assertEquals(0.125F, translucent.color().w, 1.0E-6F);
        assertTrue(translucent.pipeline().translucent());
        assertEquals(ModelAlphaMode.MASK, translucent.pipeline().alphaMode());
    }

    @Test
    void blendMaterialAlwaysUsesTranslucentQueue() {
        ModelDrawContract blend = ModelDrawContract.resolve(LAYOUT,
                material(ModelAlphaMode.BLEND, 0.6F, false), placement(false, 1), 1, false);
        assertTrue(blend.pipeline().translucent());
        assertEquals(ModelAlphaMode.BLEND, blend.pipeline().alphaMode());
        assertEquals(0.6F, blend.color().w, 1.0E-6F);
    }

    @Test
    void transparentDepthUsesIndividualPrimitiveBoundsAndCameraSpaceZ() {
        ModelBounds near = new ModelBounds(new ModelVector3(-1, -1, -3), new ModelVector3(1, 1, -1));
        ModelBounds far = new ModelBounds(new ModelVector3(-1, -1, -9), new ModelVector3(1, 1, -7));
        float nearDepth = ModelDrawOrdering.viewDepth(near, new Matrix4f());
        float farDepth = ModelDrawOrdering.viewDepth(far, new Matrix4f());
        assertTrue(ModelDrawOrdering.compareTransparentDepth(farDepth, nearDepth) < 0);
    }

    @Test
    void transparentDepthPlacesNonFiniteValuesAfterFiniteValues() {
        assertTrue(ModelDrawOrdering.compareTransparentDepth(-2, Float.NaN) < 0);
        assertTrue(ModelDrawOrdering.compareTransparentDepth(Float.POSITIVE_INFINITY, -2) > 0);
        assertEquals(0, ModelDrawOrdering.compareTransparentDepth(Float.NaN, Float.NaN));
    }

    @Test
    void parallelDepthLayersKeepTheirOrderAcrossFrontFacingCameraRotation() {
        ModelBounds far = pointBounds(0, 0, -0.30F);
        ModelBounds middle = pointBounds(0, 0, -0.18F);
        ModelBounds near = pointBounds(0, 0, -0.06F);

        for (float yaw : new float[] {-0.75F, -0.35F, 0, 0.35F, 0.75F}) {
            Matrix4f view = new Matrix4f().rotateY(yaw);
            float farDepth = ModelDrawOrdering.viewDepth(far, view);
            float middleDepth = ModelDrawOrdering.viewDepth(middle, view);
            float nearDepth = ModelDrawOrdering.viewDepth(near, view);
            assertTrue(ModelDrawOrdering.compareTransparentDepth(farDepth, middleDepth) < 0);
            assertTrue(ModelDrawOrdering.compareTransparentDepth(middleDepth, nearDepth) < 0);
        }
    }

    @Test
    void textureRequiresUvAndWorldLightMultipliesMaterialAndInstanceTint() {
        ModelVertexLayout positionOnly = new ModelVertexLayout(List.of(
                new ModelVertexLayoutElement(ModelAttributeSemantic.POSITION,
                        ModelComponentType.FLOAT32, 3, false)));
        StaticModelMaterial material = new StaticModelMaterial(0.5F, 0.25F, 1, 1,
                0, ModelAlphaMode.OPAQUE, 0.5F, false);
        ModelInstancePlacement placement = new ModelInstancePlacement(new Vector3d(), new Quaternionf(),
                new Vector3f(1), false, false, 0.8F, 0.4F, 0.2F, 1);

        ModelDrawContract withoutUv = ModelDrawContract.resolve(positionOnly, material, placement, 0.5F, false);
        ModelDrawContract withUv = ModelDrawContract.resolve(LAYOUT, material, placement, 0.5F, false);

        assertFalse(withoutUv.pipeline().textured());
        assertTrue(withUv.pipeline().textured());
        assertEquals(new Vector4f(0.2F, 0.05F, 0.1F, 1), withUv.color());
    }

    @Test
    void emissiveTextureRequiresUvAndSelectsSecondSamplerVariant() {
        StaticModelTexture absent = StaticModelTexture.absent();
        StaticModelTexture emissive = new StaticModelTexture(1, ModelTextureSampler.gltfDefault(),
                ModelTextureTransform.identity());
        StaticModelMaterial material = new StaticModelMaterial(1, 1, 1, 1, absent,
                ModelAlphaMode.OPAQUE, 0.5F, false, 1, 1, 1, emissive);
        ModelVertexLayout positionOnly = new ModelVertexLayout(List.of(
                new ModelVertexLayoutElement(ModelAttributeSemantic.POSITION,
                        ModelComponentType.FLOAT32, 3, false)));

        assertFalse(ModelDrawContract.resolve(positionOnly, material, placement(false, 1), 1, false)
                .pipeline().emissiveTextured());
        assertTrue(ModelDrawContract.resolve(LAYOUT, material, placement(false, 1), 1, false)
                .pipeline().emissiveTextured());
    }

    @Test
    void fullBrightDisablesDirectionalTermAndMirroredDoubleSidedKeepsOrientationVariant() {
        ModelDrawContract full = ModelDrawContract.resolve(LAYOUT, material(ModelAlphaMode.OPAQUE, 1, true),
                placement(true, 1), 1, true);
        assertEquals(0, full.directionalLightStrength());
        assertTrue(full.pipeline().doubleSided());
        assertTrue(full.pipeline().mirrored());
    }

    @Test
    void viewRotationKeepsWorldNormalAndWorldLightRelationshipStable() {
        Matrix4f model = new Matrix4f().rotateY(0.4F).scale(0.5F, 2.0F, 1.25F);
        Vector3f normal = new Vector3f(0, 1, 0);
        float first = lightingDot(new Matrix4f().rotateX(0.25F), model, normal);
        float second = lightingDot(new Matrix4f().rotateY(-1.2F).rotateX(0.7F), model, normal);
        assertEquals(first, second, 1.0E-5F);
    }

    private static float lightingDot(Matrix4f view, Matrix4f model, Vector3f normal) {
        Vector3f normalView = ModelDrawContract.normalMatrix(new Matrix4f(view).mul(model))
                .transform(new Vector3f(normal)).normalize();
        return normalView.dot(ModelDrawContract.lightDirectionInView(view));
    }

    private static StaticModelMaterial material(ModelAlphaMode mode, float alpha, boolean doubleSided) {
        return new StaticModelMaterial(1, 1, 1, alpha, 0, mode, 0.5F, doubleSided);
    }

    private static ModelBounds pointBounds(float x, float y, float z) {
        ModelVector3 point = new ModelVector3(x, y, z);
        return new ModelBounds(point, point);
    }

    private static ModelInstancePlacement placement(boolean fullBright, float alpha) {
        return new ModelInstancePlacement(new Vector3d(), new Quaternionf(), new Vector3f(1),
                fullBright, false, 1, 1, 1, alpha);
    }
}
