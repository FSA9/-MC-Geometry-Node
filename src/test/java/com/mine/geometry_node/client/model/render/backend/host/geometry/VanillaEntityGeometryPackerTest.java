package com.mine.geometry_node.client.model.render.backend.host.geometry;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class VanillaEntityGeometryPackerTest {
    private static final VanillaEntityGeometryPacker.EntityLayout ENTITY =
            new VanillaEntityGeometryPacker.EntityLayout(36, 0, 12, 16, 24, 28, 32);
    private static final int NO_OVERLAY = 0x000A0000;

    @Test
    void packsQueriedVanillaEntityLayoutWithBakedPoseLightAndQuadCount() {
        HostStaticGeometryPlan plan = plan();
        Quaternionf rotation = new Quaternionf().rotateZ((float) (Math.PI / 2));
        Matrix4f pose = new Matrix4f().translate(10, 20, 30).rotate(rotation);
        Matrix3f normal = new Matrix3f().rotate(rotation);
        int light = 0x00F000A0;

        VanillaEntityGeometryPacker.PackedGeometry packed =
                VanillaEntityGeometryPacker.pack(plan, pose, normal, ENTITY, NO_OVERLAY, light, false);

        assertEquals(ENTITY.stride(), packed.vertexStride());
        assertEquals(4, packed.vertexCount());
        assertEquals(6, packed.indexCount());
        assertEquals(4 * packed.vertexStride(), packed.vertexData().length);
        assertVertex(packed, 0, 8, 21, 33, 255, 127, 0, 63, 0.25F, 0.5F,
                light, 0, 127, 0);
        assertVertex(packed, 1, 10, 21, 30, 63, 127, 191, 255, 0.75F, 1,
                light, -127, 0, 0);
        assertVertex(packed, 2, 10, 19, 30, 255, 255, 255, 255, 0, 1,
                light, 0, 0, 127);
        assertVertex(packed, 3, 10, 19, 30, 255, 255, 255, 255, 0, 1,
                light, 0, 0, 127);

        byte[] mutation = packed.vertexData();
        mutation[0] = 42;
        assertNotEquals(42, packed.vertexData()[0]);
    }

    @Test
    void mirroredPackingUsesTheEstablishedEntityQuadOrder() {
        VanillaEntityGeometryPacker.PackedGeometry packed =
                VanillaEntityGeometryPacker.pack(plan(), new Matrix4f(), new Matrix3f(), ENTITY,
                        NO_OVERLAY, 0, true);
        ByteBuffer data = ByteBuffer.wrap(packed.vertexData()).order(ByteOrder.LITTLE_ENDIAN);
        int position = ENTITY.position();

        assertEquals(1, data.getFloat(position));
        assertEquals(3, data.getFloat(position + 2 * Float.BYTES));
        int second = packed.vertexStride();
        assertEquals(-1, data.getFloat(second + position));
        int third = packed.vertexStride() * 2;
        assertEquals(1, data.getFloat(third + position));
        assertEquals(0, data.getFloat(third + position + Float.BYTES));
        assertEquals(data.getFloat(third + position), data.getFloat(packed.vertexStride() * 3 + position));
    }

    @Test
    void normalizesInverseTransposeResultForNonUniformScaleAndRejectsInvalidTransforms() {
        Matrix4f pose = new Matrix4f().scale(2, 1, 1);
        Matrix3f normal = new Matrix3f(pose).invert().transpose();
        HostStaticGeometryPlan diagonalNormal = HostStaticGeometryPlan.from(new HostEntityGeometry(new float[]{
                0, 0, 0, 1, 1, 0, 0, 0, 1, 1, 1, 1,
                1, 0, 0, 1, 1, 0, 0, 0, 1, 1, 1, 1,
                0, 1, 0, 1, 1, 0, 0, 0, 1, 1, 1, 1
        }));

        VanillaEntityGeometryPacker.PackedGeometry packed = VanillaEntityGeometryPacker.pack(
                diagonalNormal, pose, normal, ENTITY, NO_OVERLAY, 0, false);
        ByteBuffer data = ByteBuffer.wrap(packed.vertexData()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(56, data.get(ENTITY.normal()));
        assertEquals(113, data.get(ENTITY.normal() + 1));

        assertThrows(IllegalArgumentException.class, () -> VanillaEntityGeometryPacker.pack(
                plan(), new Matrix4f(), new Matrix3f().zero(), ENTITY, NO_OVERLAY, 0, false));
        assertThrows(IllegalArgumentException.class, () -> VanillaEntityGeometryPacker.pack(
                plan(), new Matrix4f().m00(Float.NaN), new Matrix3f(), ENTITY, NO_OVERLAY, 0, false));
    }

    private static void assertVertex(VanillaEntityGeometryPacker.PackedGeometry packed, int vertex,
                                     float x, float y, float z, int red, int green, int blue, int alpha,
                                     float u, float v, int light, int nx, int ny, int nz) {
        ByteBuffer data = ByteBuffer.wrap(packed.vertexData()).order(ByteOrder.LITTLE_ENDIAN);
        int base = vertex * packed.vertexStride();
        VanillaEntityGeometryPacker.EntityLayout layout = ENTITY;
        assertEquals(x, data.getFloat(base + layout.position()));
        assertEquals(y, data.getFloat(base + layout.position() + 4));
        assertEquals(z, data.getFloat(base + layout.position() + 8));
        int color = base + layout.color();
        assertEquals(red, Byte.toUnsignedInt(data.get(color)));
        assertEquals(green, Byte.toUnsignedInt(data.get(color + 1)));
        assertEquals(blue, Byte.toUnsignedInt(data.get(color + 2)));
        assertEquals(alpha, Byte.toUnsignedInt(data.get(color + 3)));
        assertEquals(u, data.getFloat(base + layout.uv0()));
        assertEquals(v, data.getFloat(base + layout.uv0() + 4));
        assertEquals(NO_OVERLAY, data.getInt(base + layout.overlay()));
        assertEquals(light, data.getInt(base + layout.light()));
        int normal = base + layout.normal();
        assertEquals(nx, data.get(normal));
        assertEquals(ny, data.get(normal + 1));
        assertEquals(nz, data.get(normal + 2));
    }

    private static HostStaticGeometryPlan plan() {
        return HostStaticGeometryPlan.from(new HostEntityGeometry(new float[]{
                1, 2, 3, 1, 0, 0, 0.25F, 0.5F, 1, 0.5F, 0, 0.25F,
                1, 0, 0, 0, 1, 0, 0.75F, 1, 0.25F, 0.5F, 0.75F, 1,
                -1, 0, 0, 0, 0, 1, 0, 1, 1, 1, 1, 1
        }));
    }
}
