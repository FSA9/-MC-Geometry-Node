package com.mine.geometry_node.client.model.render;

import com.mine.geometry_node.core.engine.system.model.domain.ModelTextureTransform;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

final class ModelDynamicUniformWriter {
    private ModelDynamicUniformWriter() { }

    static GpuBufferSlice write(Matrix4f transform, Vector4f color, Vector3f emissive, float alphaCutoff,
                                ModelTextureTransform base, ModelTextureTransform emissiveTransform,
                                Vector3f lightDirection, float directionalLightStrength) {
        Matrix4f textureData = new Matrix4f().m00(alphaCutoff)
                .m10(lightDirection.x).m11(lightDirection.y).m12(lightDirection.z)
                .m20(directionalLightStrength)
                .m01(base.offsetX()).m02(base.offsetY()).m03(base.rotation())
                .m13(base.scaleX()).m23(base.scaleY())
                .m21(emissiveTransform.offsetX()).m22(emissiveTransform.offsetY())
                .m30(emissiveTransform.rotation()).m31(emissiveTransform.scaleX()).m32(emissiveTransform.scaleY());
        return RenderSystem.getDynamicUniforms().writeTransform(transform, color, emissive, textureData);
    }
}
