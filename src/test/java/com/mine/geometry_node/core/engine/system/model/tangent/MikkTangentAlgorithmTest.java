package com.mine.geometry_node.core.engine.system.model.tangent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MikkTangentAlgorithmTest {
    @Test
    void preservesMirroredUvHandednessPerFaceCorner() {
        // Two identical XY triangles. The second UV winding is mirrored. These values are a pinned-output
        // regression, not an independent differential run; see THIRD_PARTY_NOTICES.md.
        float[][] positions = {{0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 0, 0}, {1, 0, 0}, {0, 1, 0}};
        float[][] normals = {{0, 0, 1}, {0, 0, 1}, {0, 0, 1}, {0, 0, 1}, {0, 0, 1}, {0, 0, 1}};
        float[][] uvs = {{0, 0}, {1, 0}, {0, 1}, {0, 0}, {0, 1}, {1, 0}};
        CapturingContext context = new CapturingContext(positions, normals, uvs);

        assertTrue(MikkTangentAlgorithm.genTangSpaceDefault(context));
        for (int corner = 0; corner < 3; corner++) {
            assertArrayEquals(new float[]{1, 0, 0}, context.tangents[corner], 1.0E-6F);
            assertEquals(1.0F, context.signs[corner]);
        }
        for (int corner = 3; corner < 6; corner++) {
            assertArrayEquals(new float[]{0, 1, 0}, context.tangents[corner], 1.0E-6F);
            assertEquals(-1.0F, context.signs[corner]);
        }
    }

    @Test
    void returnsFiniteFallbackForDegenerateTriangle() {
        float[][] positions = {{0, 0, 0}, {0, 0, 0}, {0, 0, 0}};
        float[][] normals = {{0, 0, 1}, {0, 0, 1}, {0, 0, 1}};
        float[][] uvs = {{0, 0}, {0, 0}, {0, 0}};
        CapturingContext context = new CapturingContext(positions, normals, uvs);

        assertTrue(MikkTangentAlgorithm.genTangSpaceDefault(context));
        for (int corner = 0; corner < 3; corner++) {
            float[] tangent = context.tangents[corner];
            for (float value : tangent) assertTrue(Float.isFinite(value));
            assertEquals(1.0F, (float) Math.sqrt(tangent[0] * tangent[0]
                    + tangent[1] * tangent[1] + tangent[2] * tangent[2]), 1.0E-6F);
            assertTrue(context.signs[corner] == 1.0F || context.signs[corner] == -1.0F);
        }
    }

    private static final class CapturingContext implements MikkTSpaceContext {
        private final float[][] positions, normals, uvs;
        private final float[][] tangents;
        private final float[] signs;

        private CapturingContext(float[][] positions, float[][] normals, float[][] uvs) {
            this.positions = positions; this.normals = normals; this.uvs = uvs;
            this.tangents = new float[positions.length][3]; this.signs = new float[positions.length];
        }
        @Override public int getNumFaces() { return positions.length / 3; }
        @Override public int getNumVerticesOfFace(int face) { return 3; }
        @Override public void getPosition(float[] output, int face, int vertex) { copy(positions, output, face, vertex); }
        @Override public void getNormal(float[] output, int face, int vertex) { copy(normals, output, face, vertex); }
        @Override public void getTexCoord(float[] output, int face, int vertex) { copy(uvs, output, face, vertex); }
        @Override public void setTSpaceBasic(float[] tangent, float sign, int face, int vertex) {
            int corner = face * 3 + vertex;
            System.arraycopy(tangent, 0, tangents[corner], 0, 3); signs[corner] = sign;
        }
        @Override public void setTSpace(float[] tangent, float[] bitangent, float magnitudeS, float magnitudeT,
                                        boolean orientationPreserving, int face, int vertex) {}
        private static void copy(float[][] source, float[] output, int face, int vertex) {
            System.arraycopy(source[face * 3 + vertex], 0, output, 0, output.length);
        }
    }
}
