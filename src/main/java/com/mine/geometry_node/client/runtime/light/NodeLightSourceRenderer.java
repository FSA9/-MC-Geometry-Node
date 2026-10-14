package com.mine.geometry_node.client.runtime.light;

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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Native emitting surfaces. Supported packs evaluate the same shapes in their HDR composite. */
public final class NodeLightSourceRenderer {
    private static final int SEGMENTS = 48;
    private static final int RADIAL_SEGMENTS = 8;
    private static final EmitterMesh DISK = mesh(false), SPHERE = mesh(true);
    private static final float[] POSITIONS = new float[DISK.positions.length];
    private static final int[] COLORS = new int[DISK.positions.length];
    private static final RenderType SURFACE = surface();

    private NodeLightSourceRenderer() {}

    private static RenderType surface() {
        var source = RenderPipelines.DEBUG_QUADS;
        var builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("geometry_node", "pipeline/light_source"))
                .withVertexShader(source.getVertexShader()).withFragmentShader(source.getFragmentShader())
                .withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode())
                .withColorTargetState(source.getColorTargetState()).withCull(false)
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true));
        for (var uniform : source.getUniforms()) builder.withUniform(uniform.name(), uniform.type());
        return RenderType.create("node_light_source", RenderSetup.builder(builder.build()).createRenderSetup());
    }

    public static void render(PoseStack pose) {
        if (!NativeTerrainLights.enabled() || !NodeLightRenderer.active()) return;
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vertices = null;
        for (var light : NodeLightRenderer.lights()) {
            var d = light.definition();
            double radius = d.emitterRadius();
            if (!d.config().sourceVisible() || radius <= 0 || d.intensity() <= 0) continue;
            Vec3 center = light.position().subtract(NodeLightRenderer.camera());
            boolean point = "point".equals(d.type());
            boolean area = "area".equals(d.type());
            if (!point) {
                double axial = light.direction().dot(center.scale(-1));
                if (axial <= 0) continue;
                double lateral = Math.max(0, Math.sqrt(Math.max(0, center.lengthSqr() - axial * axial)) - radius);
                double maximumCosine = axial / Math.sqrt(axial * axial + lateral * lateral);
                if (!area && angularFactor(maximumCosine, d.coneAngle(), d.edgeSoftness()) <= 0) continue;
            }
            if (vertices == null) vertices = buffers.getBuffer(SURFACE);
            double surfaceArea = area ? d.config().area().width() * d.config().area().height() : Math.PI * radius * radius;
            double radiance = d.intensity() / Math.max(surfaceArea, 0.000001);
            int r = channel(d.color().x * radiance);
            int g = channel(d.color().y * radiance);
            int b = channel(d.color().z * radiance);
            Matrix4f matrix = pose.last().pose();
            if (area) {
                Vec3 right = light.right().scale(d.config().area().width() * 0.5);
                Vec3 up = light.up().scale(d.config().area().height() * 0.5);
                vertex(vertices, matrix, center.subtract(right).subtract(up), r, g, b);
                vertex(vertices, matrix, center.add(right).subtract(up), r, g, b);
                vertex(vertices, matrix, center.add(right).add(up), r, g, b);
                vertex(vertices, matrix, center.subtract(right).add(up), r, g, b);
                continue;
            }
            drawMesh(vertices, matrix, light, center, radius, point, r, g, b);
        }
        if (vertices != null) buffers.endBatch(SURFACE);
    }

    static double angularFactor(double cosine, float coneAngle, float softness) {
        double half = Math.toRadians(coneAngle * 0.5);
        double outer = Math.cos(half), inner = Math.cos(half * (1 - softness));
        if (cosine <= outer) return 0;
        if (inner <= outer + 0.00001) return 1;
        double t = Math.clamp((cosine - outer) / (inner - outer), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static void drawMesh(VertexConsumer vertices, Matrix4f matrix, ClientLightStore.ResolvedLight light,
                                 Vec3 center, double radius, boolean point, int r, int g, int b) {
        EmitterMesh mesh = point ? SPHERE : DISK;
        var definition = light.definition();
        double half = Math.toRadians(definition.coneAngle() * 0.5);
        double outer = Math.cos(half), inner = Math.cos(half * (1 - definition.edgeSoftness()));
        double denominator = Math.max(Math.PI * radius * radius, 0.000001);
        for (int i = 0; i < mesh.positions.length; i += 3) {
            double x = mesh.positions[i] * radius, y = mesh.positions[i + 1] * radius;
            double px = center.x + (point ? x : light.right().x * x + light.up().x * y);
            double py = center.y + (point ? y : light.right().y * x + light.up().y * y);
            double pz = center.z + (point ? mesh.positions[i + 2] * radius : light.right().z * x + light.up().z * y);
            POSITIONS[i] = (float) px; POSITIONS[i + 1] = (float) py; POSITIONS[i + 2] = (float) pz;
            if (!point) {
                // Shared disk vertices retain their own viewing angle, including near the source.
                double length = Math.sqrt(px * px + py * py + pz * pz);
                double cosine = length < 1.0e-5f ? 0 : -(light.direction().x * px + light.direction().y * py + light.direction().z * pz) / length;
                double t = Math.clamp((cosine - outer) / Math.max(inner - outer, 0.00001), 0, 1);
                double angular = cosine <= outer ? 0 : inner <= outer + 0.00001 ? 1 : t * t * (3 - 2 * t);
                double brightness = definition.intensity() * angular / denominator;
                r = channel(definition.color().x * brightness);
                g = channel(definition.color().y * brightness);
                b = channel(definition.color().z * brightness);
            }
            COLORS[i] = r; COLORS[i + 1] = g; COLORS[i + 2] = b;
        }
        for (int index : mesh.indices) {
            int i = index * 3;
            vertices.addVertex(matrix, POSITIONS[i], POSITIONS[i + 1], POSITIONS[i + 2])
                    .setColor(COLORS[i], COLORS[i + 1], COLORS[i + 2], 255);
        }
    }

    private static EmitterMesh mesh(boolean sphere) {
        int rows = sphere ? 12 : RADIAL_SEGMENTS, columns = sphere ? 24 : SEGMENTS;
        double[] positions = new double[(rows + 1) * (columns + 1) * 3];
        int[] indices = new int[rows * columns * 4];
        for (int row = 0; row <= rows; row++) {
            double phi = Math.PI * row / rows - Math.PI / 2;
            double ring = sphere ? Math.cos(phi) : (double) row / rows;
            for (int column = 0; column <= columns; column++) {
                double theta = column * (2 * Math.PI / columns);
                int i = (row * (columns + 1) + column) * 3;
                positions[i] = ring * Math.cos(theta);
                positions[i + 1] = sphere ? Math.sin(phi) : ring * Math.sin(theta);
                positions[i + 2] = sphere ? ring * Math.sin(theta) : 0;
                if (row == rows || column == columns) continue;
                int offset = (row * columns + column) * 4, first = row * (columns + 1) + column;
                indices[offset] = first; indices[offset + 1] = first + columns + 1;
                indices[offset + 2] = first + columns + 2; indices[offset + 3] = first + 1;
            }
        }
        return new EmitterMesh(positions, indices);
    }

    private record EmitterMesh(double[] positions, int[] indices) {}

    private static int channel(double linear) {
        return (int) Math.round(255 * Math.pow(Math.clamp(linear, 0, 1), 1 / 2.2));
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, Vec3 p, int r, int g, int b) {
        vertices.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, 255);
    }
}
