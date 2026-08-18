package com.mine.geometry_node.client.model.render.backend.host.material;

import com.mine.geometry_node.client.model.runtime.StaticModelMaterial;
import com.mine.geometry_node.client.model.runtime.StaticModelTexture;
import com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode;
import com.mine.geometry_node.core.engine.system.model.domain.ModelTextureSampler;
import com.mine.geometry_node.core.engine.system.model.domain.ModelTextureTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HostMaterialProjectionPolicyTest {
    @Test
    void renderCoordinatesKeepExistingRolePriority() {
        StaticModelTexture base = texture(3, 1);
        StaticModelTexture normal = texture(8, 2);
        StaticModelMaterial material = material(ModelAlphaMode.OPAQUE, base, normal);

        assertEquals(base, HostMaterialProjectionPolicy.renderCoordinateSource(material));
        assertEquals(base, HostMaterialProjectionPolicy.occlusionAlphaSource(material));
    }

    @Test
    void normalTextureNeverBecomesOcclusionAlpha() {
        StaticModelTexture normal = texture(8, 2);
        StaticModelMaterial material = material(ModelAlphaMode.MASK, StaticModelTexture.absent(), normal);

        assertEquals(normal, HostMaterialProjectionPolicy.renderCoordinateSource(material));
        assertFalse(HostMaterialProjectionPolicy.occlusionAlphaSource(material).present());
        assertEquals(HostOcclusionClass.MASK_COVERAGE_REQUIRED,
                HostMaterialProjectionPolicy.occlusionClass(material));
    }

    @Test
    void alphaModesHaveExplicitOcclusionClasses() {
        assertEquals(HostOcclusionClass.OPAQUE_BLOCKER,
                HostMaterialProjectionPolicy.occlusionClass(material(ModelAlphaMode.OPAQUE,
                        StaticModelTexture.absent(), StaticModelTexture.absent())));
        assertEquals(HostOcclusionClass.TRANSMISSIVE,
                HostMaterialProjectionPolicy.occlusionClass(material(ModelAlphaMode.BLEND,
                        StaticModelTexture.absent(), StaticModelTexture.absent())));
    }

    private static StaticModelTexture texture(int image, int texCoord) {
        return new StaticModelTexture(image, ModelTextureSampler.gltfDefault(), texCoord,
                ModelTextureTransform.identity());
    }

    private static StaticModelMaterial material(ModelAlphaMode alphaMode, StaticModelTexture base,
                                                StaticModelTexture normal) {
        return new StaticModelMaterial(1, 1, 1, 1, base, alphaMode, 0.5F, false,
                0, 0, 0, StaticModelTexture.absent(), 1, 1, StaticModelTexture.absent(),
                normal, 1, StaticModelTexture.absent(), 1);
    }
}
