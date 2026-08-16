package com.mine.geometry_node.client.model.render.backend.standalone;

import com.mine.geometry_node.client.model.runtime.ModelInstancePlacement;
import com.mine.geometry_node.client.model.runtime.StaticModelMaterial;
import com.mine.geometry_node.core.engine.system.model.domain.ModelAttributeSemantic;
import com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode;
import com.mine.geometry_node.core.engine.system.model.domain.ModelVertexLayout;
import org.joml.Matrix3f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Pure draw-state contract shared by the production renderer and M7 correctness tests. */
public record ModelDrawContract(ModelPipelineKey pipeline, Vector4f color, Vector3f emissive,
                                float alphaCutoff, float worldLight, boolean fullBright,
                                com.mine.geometry_node.core.engine.system.model.domain.ModelTextureTransform baseTransform,
                                com.mine.geometry_node.core.engine.system.model.domain.ModelTextureTransform emissiveTransform) {
    private static final Vector3f WORLD_LIGHT_DIRECTION = new Vector3f(0.2F, 1.0F, 0.35F).normalize();

    public ModelDrawContract {
        color = new Vector4f(color);
        emissive = new Vector3f(emissive);
    }

    public static ModelDrawContract resolve(ModelVertexLayout layout, StaticModelMaterial material,
                                            ModelInstancePlacement placement, float worldLight, boolean mirrored) {
        float alpha = placement.alpha() * (material.alphaMode() == ModelAlphaMode.OPAQUE ? 1.0F : material.alpha());
        boolean doubleSided = material.doubleSided() || placement.forceDoubleSided();
        boolean skinned = layout.elements().stream()
                .anyMatch(element -> element.semantic().equals(ModelAttributeSemantic.JOINTS_0));
        ModelPipelineKey pipeline = new ModelPipelineKey(layout, material.alphaMode(),
                doubleSided, mirrored, material.alphaMode() == ModelAlphaMode.BLEND || placement.alpha() < 0.999F,
                skinned);
        return new ModelDrawContract(pipeline, new Vector4f(
                placement.red() * material.red(),
                placement.green() * material.green(),
                placement.blue() * material.blue(),
                alpha), new Vector3f(material.emissiveRed(), material.emissiveGreen(), material.emissiveBlue()),
                material.alphaCutoff(), worldLight, placement.fullBright(),
                material.baseColorTexture().transform(), material.emissiveTexture().transform());
    }

    public static Matrix3f normalMatrix(Matrix4fc modelView) {
        return new Matrix3f(modelView).invert().transpose();
    }

    public static Vector3f lightDirectionInView(Matrix4fc view) {
        return new Matrix3f(view).transform(new Vector3f(WORLD_LIGHT_DIRECTION)).normalize();
    }

    @Override public Vector4f color() { return new Vector4f(color); }
    @Override public Vector3f emissive() { return new Vector3f(emissive); }
}
