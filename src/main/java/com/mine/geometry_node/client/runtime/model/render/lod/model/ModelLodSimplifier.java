package com.mine.geometry_node.client.runtime.model.render.lod.model;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.meshoptimizer.MeshoptStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.util.meshoptimizer.MeshOptimizer.*;

/** Attribute-aware edge collapse; source vertices and open boundaries stay intact. */
public final class ModelLodSimplifier {
    static final String ALGORITHM = "meshoptimizer-lwjgl-3.4.1-attributes-permissive-border-chain-v4";
    static final float[] RATIOS = {(float) ModelLodPolicy.triangleRatio(1),
            (float) ModelLodPolicy.triangleRatio(2), (float) ModelLodPolicy.triangleRatio(3)};
    static final float[] ERRORS = {0.0025f, 0.005f, 0.01f};
    static final long MAX_WORKING_BYTES = 512L * 1024 * 1024;
    private static final int ATTRIBUTES = 19;
    static final int OPTIONS = meshopt_SimplifyLockBorder | meshopt_SimplifyPermissive;
    static final float[] WEIGHTS = {
            .5f, .5f, .5f, .25f, .25f, .25f, .25f,
            10, 10, 10, 10, 10, 10, 10, 10, 1, 1, 1, 1
    };

    private ModelLodSimplifier() {}

    public static ModelLodMesh.Levels simplify(ModelLodMesh.Input input, ModelCancellation token,
                                                long additionalCpuBudget) throws IOException {
        token.check();
        if (input.material().alphaMode() != GltfAsset.AlphaMode.OPAQUE || input.triangleCount() <= 2)
            return ModelLodMesh.Levels.source(input);
        long worstRetained = (long) input.indices().length * Integer.BYTES * 3;
        float[] attributeWeights = weights(input, token);
        int activeAttributes = 0;
        for (float weight : attributeWeights) if (weight > 0) activeAttributes++;
        long working = (long) input.vertexCount() * (256 + activeAttributes * 24L)
                + (long) input.indices().length * 48 + worstRetained;
        if (worstRetained > additionalCpuBudget) throw new BudgetException("retained_index_budget");
        if (working > MAX_WORKING_BYTES) throw new BudgetException("simplifier_working_budget");

        ByteBuffer positionBytes = null, attributeBytes = null;
        IntBuffer indices = null, destination = null, remap = null, inverse = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            positionBytes = MemoryUtil.memAlloc(Math.multiplyExact(input.positions().length, Float.BYTES));
            FloatBuffer positions = positionBytes.asFloatBuffer().put(input.positions()).flip();
            attributeBytes = MemoryUtil.memAlloc(Math.multiplyExact(input.vertexCount(), ATTRIBUTES * Float.BYTES));
            FloatBuffer attributes = attributeBytes.asFloatBuffer();
            for (int vertex = 0; vertex < input.vertexCount(); vertex++) {
                if ((vertex & 4095) == 0) token.check();
                append(attributes, input.normals(), vertex, 3);
                append(attributes, input.tangents(), vertex, 4);
                append(attributes, input.baseUv(), vertex, 2);
                append(attributes, input.emissionUv(), vertex, 2);
                append(attributes, input.uv0(), vertex, 2);
                append(attributes, input.uv1(), vertex, 2);
                int color = input.colors().length == 0 ? -1 : input.colors()[vertex];
                attributes.put((color >>> 16 & 255) / 255f).put((color >>> 8 & 255) / 255f)
                        .put((color & 255) / 255f).put((color >>> 24) / 255f);
            }
            attributes.flip();
            indices = MemoryUtil.memAllocInt(input.indices().length).put(input.indices()).flip();
            destination = MemoryUtil.memAllocInt(input.indices().length);
            remap = MemoryUtil.memAllocInt(input.vertexCount());
            inverse = MemoryUtil.memAllocInt(input.vertexCount());
            MeshoptStream.Buffer streams = MeshoptStream.malloc(2, stack);
            streams.get(0).set(positionBytes, 12, 12);
            streams.get(1).set(attributeBytes, ATTRIBUTES * 4L, ATTRIBUTES * 4L);
            meshopt_generateVertexRemapMulti(remap, indices, input.vertexCount(), streams);
            // Weld only identical complete vertices, then map back to original vertex IDs.
            for (int vertex = 0; vertex < input.vertexCount(); vertex++) {
                int mapped = remap.get(vertex);
                if (mapped >= 0) inverse.put(mapped, vertex);
            }
            for (int i = 0; i < indices.limit(); i++) indices.put(i, inverse.get(remap.get(indices.get(i))));
            FloatBuffer weights = stack.floats(attributeWeights), error = stack.mallocFloat(1);
            List<ModelLodMesh> levels = new ArrayList<>(3);
            ModelLodMesh previous = new ModelLodMesh(input.indices(), 0, true);
            for (int level = 0; level < 3; level++) {
                token.check();
                int target = Math.max(3, (int) Math.floor(input.triangleCount() * RATIOS[level]) * 3);
                int count = Math.toIntExact(meshopt_simplifyWithAttributes(destination, indices, positions,
                        input.vertexCount(), 12, attributes, ATTRIBUTES * 4L, weights, null,
                        target, Math.max(0, ERRORS[level] - previous.error()),
                        OPTIONS, error));
                token.check();
                float actualError = error.get(0);
                if (count < 3 || count > indices.limit() || count % 3 != 0
                        || !Float.isFinite(actualError) || actualError < 0)
                    throw new IOException("invalid meshoptimizer output");
                if (count == indices.limit()) {
                    levels.add(previous);
                } else {
                    int[] reduced = new int[count];
                    destination.get(0, reduced);
                    for (int index : reduced) if (index < 0 || index >= input.vertexCount())
                        throw new IOException("meshoptimizer produced an invalid vertex index");
                    previous = new ModelLodMesh(reduced, previous.error() + actualError, false);
                    levels.add(previous);
                    indices.clear().put(reduced).flip();
                }
            }
            return new ModelLodMesh.Levels(levels);
        } finally {
            MemoryUtil.memFree(inverse);
            MemoryUtil.memFree(remap);
            MemoryUtil.memFree(destination);
            MemoryUtil.memFree(indices);
            MemoryUtil.memFree(attributeBytes);
            MemoryUtil.memFree(positionBytes);
        }
    }

    private static void append(FloatBuffer target, float[] values, int vertex, int width) {
        for (int i = 0; i < width; i++) target.put(values.length == 0 ? 0 : values[vertex * width + i]);
    }

    private static float[] weights(ModelLodMesh.Input input, ModelCancellation token) throws IOException {
        float[] weights = WEIGHTS.clone();
        float[][] arrays = {input.normals(), input.tangents(), input.baseUv(), input.emissionUv(), input.uv0(), input.uv1()};
        int[] widths = {3, 4, 2, 2, 2, 2};
        int offset = 0;
        for (int field = 0; field < arrays.length; field++) {
            float[] values = arrays[field];
            int width = widths[field];
            boolean duplicate = false;
            for (int earlier = 2; earlier < field; earlier++)
                if (widths[earlier] == width && Arrays.equals(arrays[earlier], values)) duplicate = true;
            for (int component = 0; component < width; component++) {
                boolean constant = true;
                for (int vertex = 1; !duplicate && vertex < values.length / width; vertex++) {
                    if ((vertex & 4095) == 0) token.check();
                    if (values[vertex * width + component] != values[component]) { constant = false; break; }
                }
                if (duplicate || constant) weights[offset + component] = 0;
            }
            offset += width;
        }
        for (int component = 0; component < 4; component++) {
            int shift = component == 0 ? 16 : component == 1 ? 8 : component == 2 ? 0 : 24;
            boolean constant = true;
            for (int vertex = 1; vertex < input.colors().length; vertex++) {
                if ((vertex & 4095) == 0) token.check();
                if ((input.colors()[vertex] >>> shift & 255) != (input.colors()[0] >>> shift & 255)) { constant = false; break; }
            }
            if (constant) weights[15 + component] = 0;
        }
        return weights;
    }

    static final class BudgetException extends IOException {
        BudgetException(String reason) { super(reason); }
    }
}
