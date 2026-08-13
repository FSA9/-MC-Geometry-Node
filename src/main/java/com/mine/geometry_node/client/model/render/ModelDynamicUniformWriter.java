package com.mine.geometry_node.client.model.render;

import com.mine.geometry_node.core.engine.system.model.domain.ModelTextureTransform;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

final class ModelDynamicUniformWriter {
    private ModelDynamicUniformWriter() { }

    static GpuBufferSlice write(Matrix4f transform, Vector3f lightDirection, float worldLight, boolean fullBright) {
        Matrix4f textureData = new Matrix4f()
                .m10(lightDirection.x).m11(lightDirection.y).m12(lightDirection.z)
                .m20(worldLight).m21(fullBright ? 1.0F : 0.0F);
        return RenderSystem.getDynamicUniforms().writeTransform(transform, new Vector4f(1), new Vector3f(), textureData);
    }
}
