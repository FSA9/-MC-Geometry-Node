package com.mine.geometry_node.mixin;

import com.mine.geometry_node.client.model.render.pipeline.ModelStaticRenderer;
import com.mine.geometry_node.client.model.render.pipeline.ModelIrisAlphaTest;
import com.mine.geometry_node.client.model.render.pipeline.ModelIrisBlendOverride;
import com.mine.geometry_node.GeometryNode;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.DynamicUniforms;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;

/** Observe actual immediate buffer upload calls, including deferred collector consumption. */
@Mixin(RenderType.class)
public abstract class ModelRenderTypeMetricsMixin {
    @Shadow @Final protected String name;

    @WrapMethod(method = "draw")
    private void geometryNode$measureDraw(MeshData mesh, Operation<Void> original) {
        boolean model = name.startsWith("geometry_node_model_");
        long started = model ? System.nanoTime() : 0;
        try (var alpha = ModelIrisAlphaTest.forRenderType((RenderType) (Object) this);
             var blend = ModelIrisBlendOverride.forRenderType((RenderType) (Object) this)) {
            original.call(mesh);
        } catch (IllegalStateException failure) {
            if (failure.getMessage() != null && failure.getMessage().startsWith("Missing sampler ")) {
                // RenderType.toString includes textures and overlay/lightmap flags.
                // Preserve the exception and validation; only add the context missing from host reports.
                RenderType type = (RenderType) (Object) this;
                String context = "GeometryNode render diagnosis: model=" + model
                        + "; pipeline=" + type.pipeline().getLocation()
                        + "; declaredSamplers=" + type.pipeline().getSamplers() + "; " + this;
                GeometryNode.LOGGER.error(context, failure);
                failure.addSuppressed(new IllegalStateException(context));
            }
            throw failure;
        } finally {
            if (model) ModelStaticRenderer.immediateDrawFinished(System.nanoTime() - started);
        }
    }

    @Redirect(method = "draw", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private GpuBufferSlice geometryNode$measureTransforms(DynamicUniforms uniforms, Matrix4fc view,
                                                         Vector4fc color, Vector3fc offset, Matrix4fc texture) {
        GpuBufferSlice result = uniforms.writeTransform(view, color, offset, texture);
        if (name.startsWith("geometry_node_model_")) ModelStaticRenderer.immediateUniformUploaded(result.length());
        return result;
    }

    @Redirect(method = "draw", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/vertex/VertexFormat;uploadImmediateVertexBuffer(Ljava/nio/ByteBuffer;)Lcom/mojang/blaze3d/buffers/GpuBuffer;"))
    private GpuBuffer geometryNode$measureVertices(VertexFormat format, ByteBuffer data) {
        if (!name.startsWith("geometry_node_model_")) return format.uploadImmediateVertexBuffer(data);
        int bytes = data.remaining();
        long started = System.nanoTime();
        GpuBuffer result = format.uploadImmediateVertexBuffer(data);
        ModelStaticRenderer.immediateGeometryUploaded(bytes, System.nanoTime() - started);
        return result;
    }

    @Redirect(method = "draw", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/vertex/VertexFormat;uploadImmediateIndexBuffer(Ljava/nio/ByteBuffer;)Lcom/mojang/blaze3d/buffers/GpuBuffer;"))
    private GpuBuffer geometryNode$measureIndices(VertexFormat format, ByteBuffer data) {
        if (!name.startsWith("geometry_node_model_")) return format.uploadImmediateIndexBuffer(data);
        int bytes = data.remaining();
        long started = System.nanoTime();
        GpuBuffer result = format.uploadImmediateIndexBuffer(data);
        ModelStaticRenderer.immediateGeometryUploaded(bytes, System.nanoTime() - started);
        return result;
    }
}
