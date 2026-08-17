package com.mine.geometry_node.client.model.render.backend.host.geometry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

/**
 * Pure CPU contract for a future static HOST geometry bridge.
 *
 * <p>The source contains position, normal, UV and color as twelve little-endian floats per vertex.
 * Host pipeline fields such as packed light and the constant no-overlay value are intentionally outside this plan.
 * The quad order describes the four logical vertices currently emitted for each source triangle;
 * it is not a claim about the final GPU index format used by a Minecraft render adapter.</p>
 */
public final class HostStaticGeometryPlan {
    public static final int COMPONENTS_PER_VERTEX = 12;
    public static final int SOURCE_VERTICES_PER_TRIANGLE = 3;
    public static final int QUAD_VERTICES_PER_TRIANGLE = 4;
    public static final int SOURCE_VERTEX_BYTES = COMPONENTS_PER_VERTEX * Float.BYTES;

    private final byte[] packedSource;
    private final int[] quadOrder;
    private final int[] mirroredQuadOrder;
    private final int triangleCount;

    private HostStaticGeometryPlan(byte[] packedSource, int[] quadOrder, int[] mirroredQuadOrder,
                                   int triangleCount) {
        this.packedSource = packedSource;
        this.quadOrder = quadOrder;
        this.mirroredQuadOrder = mirroredQuadOrder;
        this.triangleCount = triangleCount;
    }

    public static HostStaticGeometryPlan from(HostEntityGeometry geometry) {
        Objects.requireNonNull(geometry, "geometry");
        float[] source = geometry.staticVertexData();
        if (source.length % (SOURCE_VERTICES_PER_TRIANGLE * COMPONENTS_PER_VERTEX) != 0) {
            throw new IllegalArgumentException("HOST geometry does not contain complete triangles");
        }
        int triangleCount = source.length / (SOURCE_VERTICES_PER_TRIANGLE * COMPONENTS_PER_VERTEX);
        ByteBuffer packed = ByteBuffer.allocate(Math.multiplyExact(source.length, Float.BYTES))
                .order(ByteOrder.LITTLE_ENDIAN);
        for (float value : source) packed.putFloat(value);

        int[] regular = new int[Math.multiplyExact(triangleCount, QUAD_VERTICES_PER_TRIANGLE)];
        int[] mirrored = new int[regular.length];
        for (int triangle = 0; triangle < triangleCount; triangle++) {
            int sourceBase = triangle * SOURCE_VERTICES_PER_TRIANGLE;
            int orderBase = triangle * QUAD_VERTICES_PER_TRIANGLE;
            regular[orderBase] = sourceBase;
            regular[orderBase + 1] = sourceBase + 1;
            regular[orderBase + 2] = sourceBase + 2;
            regular[orderBase + 3] = sourceBase + 2;
            mirrored[orderBase] = sourceBase;
            mirrored[orderBase + 1] = sourceBase + 2;
            mirrored[orderBase + 2] = sourceBase + 1;
            mirrored[orderBase + 3] = sourceBase + 1;
        }
        return new HostStaticGeometryPlan(packed.array(), regular, mirrored, triangleCount);
    }

    public int triangleCount() { return triangleCount; }
    public int sourceVertexCount() { return triangleCount * SOURCE_VERTICES_PER_TRIANGLE; }
    public int quadVertexCount() { return triangleCount * QUAD_VERTICES_PER_TRIANGLE; }
    public byte[] packedSource() { return Arrays.copyOf(packedSource, packedSource.length); }
    public int[] quadOrder(boolean mirrored) {
        int[] selected = mirrored ? mirroredQuadOrder : quadOrder;
        return Arrays.copyOf(selected, selected.length);
    }
    public long sourceByteSize() { return packedSource.length; }
    public long orderByteSize() { return (long) quadOrder.length * Integer.BYTES; }
    public long contractByteSize(boolean retainBothWindings) {
        return sourceByteSize() + orderByteSize() * (retainBothWindings ? 2 : 1);
    }
    public long expandedQuadByteSize() { return (long) quadVertexCount() * SOURCE_VERTEX_BYTES; }
}
