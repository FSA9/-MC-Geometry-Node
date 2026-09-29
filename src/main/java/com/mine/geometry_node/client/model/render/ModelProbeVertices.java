package com.mine.geometry_node.client.model.render;
import com.mine.geometry_node.core.engine.system.model.asset.ModelMesh;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
final class ModelProbeVertices {
    private ModelProbeVertices() {}
    static void emit(ModelMesh mesh,int v,VertexConsumer out,PoseStack.Pose pose,int light) {
        out.addVertex(pose,mesh.attribute(v,0),mesh.attribute(v,1),mesh.attribute(v,2))
                .setColor(mesh.color(v)).setUv(mesh.attribute(v,6),mesh.attribute(v,7))
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose,mesh.attribute(v,3),mesh.attribute(v,4),mesh.attribute(v,5));
    }
}
