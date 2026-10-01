package com.mine.geometry_node.client.model.render;
import com.mine.geometry_node.core.engine.system.model.asset.ModelMesh;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.*;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.nio.*;
import java.util.*;
/** P0 cube VBO/IBO. Resolve the actual host layout inside each render pass, never in a command/tick. */
public final class ModelProbeGpuMesh implements AutoCloseable {
    private final ModelMesh source;
    private final int light;
    private GpuBuffer vertices,indices;
    private VertexFormat layout;
    private long bytes, uploaded;
    private boolean closed;
    public ModelProbeGpuMesh(ModelMesh source,int light) {this.source=source;this.light=light;}
    public long bytes() { return bytes; }
    public long uploadedBytes() { return uploaded; }
    private void prepare() {
        RenderSystem.assertOnRenderThread();if(closed)throw new IllegalStateException("probe already closed");
        VertexFormat expected=ModelProbePipelines.PIPELINE.getVertexFormat();
        if(vertices!=null&&layout==expected)return;
        GpuBuffer vertex=null,index=null;
        try(ByteBufferBuilder arena=new ByteBufferBuilder(8192)) {
            // Fixture vertices are face-local quads. Let the host calculate quad tangents, then use our indexed triangles.
            BufferBuilder builder=new BufferBuilder(arena,VertexFormat.Mode.QUADS,expected);
            var identity=new PoseStack().last();
            for(int v=0;v<source.vertexCount();v++) ModelProbeVertices.emit(source,v,builder,identity,light);
            try(MeshData mesh=builder.buildOrThrow()) {
                if(mesh.drawState().format()!=ModelProbePipelines.PIPELINE.getVertexFormat())
                    throw new IllegalStateException("host changed vertex layout during probe preparation");
                ByteBuffer data=mesh.vertexBuffer();
                vertex=RenderSystem.getDevice().createBuffer(()->"Model P0 vertices",GpuBuffer.USAGE_VERTEX,data);
                ByteBuffer ids=ByteBuffer.allocateDirect(source.indexCount()*4).order(ByteOrder.nativeOrder());
                for(int i=0;i<source.indexCount();i++)ids.putInt(source.index(i));ids.flip();
                index=RenderSystem.getDevice().createBuffer(()->"Model P0 indices",GpuBuffer.USAGE_INDEX,ids);
                long newBytes=(long)data.remaining()+ids.remaining();
                retire();vertices=vertex;indices=index;layout=mesh.drawState().format();bytes=newBytes;uploaded+=newBytes;
            }
        } catch(RuntimeException|Error e){if(vertex!=null)vertex.close();if(index!=null)index.close();throw e;}
    }
    public void draw(Matrix4f modelView) {
        prepare();Minecraft mc=Minecraft.getInstance();var target=mc.getMainRenderTarget();
        var color=RenderSystem.outputColorTextureOverride!=null?RenderSystem.outputColorTextureOverride:target.getColorTextureView();
        var depth=RenderSystem.outputDepthTextureOverride!=null?RenderSystem.outputDepthTextureOverride:target.getDepthTextureView();
        if(color==null)return;
        var stack=RenderSystem.getModelViewStack();
        stack.pushMatrix();
        try {
        stack.set(modelView); // Iris inverse/normal uniforms must agree with DynamicTransforms.
        var transform=RenderSystem.getDynamicUniforms().writeTransform(modelView,new Vector4f(1),new Vector3f(),new Matrix4f());
        try(RenderPass pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(()->"Model P0 resident probe",color,
                OptionalInt.empty(),depth,OptionalDouble.empty())) {
            pass.setPipeline(ModelProbePipelines.PIPELINE);RenderSystem.bindDefaultUniforms(pass);pass.setUniform("DynamicTransforms",transform);
            var texture=mc.getTextureManager().getTexture(ModelProbePipelines.TEXTURE);
            pass.bindTexture("Sampler0",texture.getTextureView(),texture.getSampler());
            pass.bindTexture("Sampler1",texture.getTextureView(),texture.getSampler());
            pass.bindTexture("Sampler2",mc.gameRenderer.lightmap(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setVertexBuffer(0,vertices);pass.setIndexBuffer(indices,VertexFormat.IndexType.INT);pass.drawIndexed(0,0,source.indexCount(),1);
        }
        } finally { stack.popMatrix(); }
    }
    private void retire() {
        GpuBuffer oldVertices=vertices,oldIndices=indices;vertices=null;indices=null;bytes=0;
        if(oldVertices!=null || oldIndices!=null)RenderSystem.queueFencedTask(()->{
            if(oldVertices!=null) oldVertices.close();
            if(oldIndices!=null) oldIndices.close();
        });
    }
    public void close(){if(closed)return;closed=true;retire();}
}
