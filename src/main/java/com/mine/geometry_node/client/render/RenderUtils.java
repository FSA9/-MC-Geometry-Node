package com.mine.geometry_node.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import net.minecraft.world.phys.Vec3;

public class RenderUtils {
    // 抽离：解包颜色
    public static float[] unpackColor(int color) {
        return new float[]{
                ((color >> 16) & 0xFF) / 255f, // R
                ((color >> 8) & 0xFF) / 255f,  // G
                (color & 0xFF) / 255f,         // B
                ((color >> 24) & 0xFF) / 255f  // A
        };
    }

    public static void drawQuad(VertexConsumer buffer, Matrix4f matrix,
                                Vec3 v1, Vec3 v2, Vec3 v3, Vec3 v4,
                                int r, int g, int b, int a) {
        buffer.addVertex(matrix, (float)v1.x, (float)v1.y, (float)v1.z).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float)v2.x, (float)v2.y, (float)v2.z).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float)v3.x, (float)v3.y, (float)v3.z).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float)v4.x, (float)v4.y, (float)v4.z).setColor(r, g, b, a);
    }
}