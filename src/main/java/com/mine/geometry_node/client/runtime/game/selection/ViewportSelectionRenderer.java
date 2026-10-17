package com.mine.geometry_node.client.runtime.game.selection;

import com.mine.geometry_node.client.runtime.game.render.EditViewportRuntime;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Foot-plane discs drawn after opaque depth, before translucent entities (including slime shells). */
public final class ViewportSelectionRenderer {
    private static final int SEGMENTS = 48;
    private static final double EDGE_WIDTH = .025;
    private static final int CENTER = 0x388EFAA5;
    private static final int INNER = 0x504FE87B;
    private static final int EDGE = 0xA020C050;
    private static final double[] X = new double[SEGMENTS + 1], Z = new double[SEGMENTS + 1];
    private static final RenderType DISC = discType();
    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = i * Math.PI * 2 / SEGMENTS;
            X[i] = Math.cos(angle);
            Z[i] = Math.sin(angle);
        }
    }

    private ViewportSelectionRenderer() {}

    private static RenderType discType() {
        var source = RenderPipelines.DEBUG_QUADS;
        var builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("geometry_node", "pipeline/selection_disc"))
                .withVertexShader(source.getVertexShader()).withFragmentShader(source.getFragmentShader())
                .withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode())
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                // Read the real world depth, but do not occlude subsequent translucent geometry.
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false));
        for (var uniform : source.getUniforms()) builder.withUniform(uniform.name(), uniform.type());
        return RenderType.create("geometry_node_selection_disc",
                RenderSetup.builder(builder.build()).sortOnUpload().createRenderSetup());
    }

    public static void render(PoseStack pose, Vec3 camera) {
        var selected = EditViewportRuntime.renderedSelection();
        if (selected.isEmpty()) return;
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vertices = null;
        // Use this pass's interpolated bounds, never a previous UI presentation's entity positions.
        for (var target : ViewportEntitySource.extractedTargets()) {
            if (!selected.contains(target.key()) || target.decoration() != ViewportTarget.Decoration.DISC) continue;
            if (vertices == null) vertices = buffers.getBuffer(DISC);
            disc(vertices, pose.last().pose(), camera, target.bounds());
        }
        if (vertices != null) buffers.endBatch(DISC);
    }

    private static void disc(VertexConsumer vertices, Matrix4f matrix, Vec3 camera, AABB bounds) {
        double cx = (bounds.minX + bounds.maxX) * .5 - camera.x;
        double cy = bounds.minY + .012 - camera.y;
        double cz = (bounds.minZ + bounds.maxZ) * .5 - camera.z;
        double radius = Math.max(.22, Math.max(bounds.getXsize(), bounds.getZsize()) * .65);
        double inner = radius - EDGE_WIDTH;
        for (int i = 0; i < SEGMENTS; i++) {
            // Degenerate quad gives a triangle fan; adjoining rim quads share the same vertices.
            vertex(vertices, matrix, cx, cy, cz, CENTER);
            vertex(vertices, matrix, cx + X[i] * inner, cy, cz + Z[i] * inner, INNER);
            vertex(vertices, matrix, cx + X[i + 1] * inner, cy, cz + Z[i + 1] * inner, INNER);
            vertex(vertices, matrix, cx, cy, cz, CENTER);

            vertex(vertices, matrix, cx + X[i] * inner, cy, cz + Z[i] * inner, INNER);
            vertex(vertices, matrix, cx + X[i] * radius, cy, cz + Z[i] * radius, EDGE);
            vertex(vertices, matrix, cx + X[i + 1] * radius, cy, cz + Z[i + 1] * radius, EDGE);
            vertex(vertices, matrix, cx + X[i + 1] * inner, cy, cz + Z[i + 1] * inner, INNER);
        }
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix,
                               double x, double y, double z, int argb) {
        vertices.addVertex(matrix, (float) x, (float) y, (float) z).setColor(argb);
    }
}
