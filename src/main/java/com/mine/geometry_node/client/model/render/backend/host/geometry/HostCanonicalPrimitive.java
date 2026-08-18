package com.mine.geometry_node.client.model.render.backend.host.geometry;

import com.mine.geometry_node.core.engine.system.model.domain.ModelAttributeSemantic;
import com.mine.geometry_node.core.engine.system.model.domain.ModelBounds;
import com.mine.geometry_node.core.engine.system.model.domain.ModelComponentType;
import com.mine.geometry_node.core.engine.system.model.domain.ModelPrimitive;
import com.mine.geometry_node.core.engine.system.model.domain.ModelVertexAttribute;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Complete immutable asset-level primitive consumed by HOST CPU projection and derived resources. */
public final class HostCanonicalPrimitive {
    private final Identity identity;
    private final ModelBounds bounds;
    private final int vertexCount;
    private final Map<ModelAttributeSemantic, Attribute> attributes;
    private final int[] canonicalIndices;
    private final int[][] occurrencesByVertex;
    private final List<NodeOccurrence> nodeOccurrences;

    private HostCanonicalPrimitive(Identity identity, ModelBounds bounds, int vertexCount,
                                   Map<ModelAttributeSemantic, Attribute> attributes, int[] canonicalIndices,
                                   int[][] occurrencesByVertex, List<NodeOccurrence> nodeOccurrences) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.vertexCount = vertexCount;
        this.attributes = Map.copyOf(attributes);
        this.canonicalIndices = canonicalIndices;
        this.occurrencesByVertex = occurrencesByVertex;
        this.nodeOccurrences = List.copyOf(nodeOccurrences);
    }

    public static HostCanonicalPrimitive from(int meshIndex, int primitiveIndex, ModelPrimitive primitive) {
        return from(meshIndex, primitiveIndex, primitive, List.of());
    }

    public static HostCanonicalPrimitive from(int meshIndex, int primitiveIndex, ModelPrimitive primitive,
                                              List<NodeOccurrence> nodeOccurrences) {
        Objects.requireNonNull(primitive, "primitive");
        Objects.requireNonNull(nodeOccurrences, "nodeOccurrences");
        if (!primitive.attributes().containsKey(ModelAttributeSemantic.POSITION)) {
            throw new IllegalStateException("validated primitive lacks canonical POSITION");
        }
        Map<ModelAttributeSemantic, Attribute> attributes = new LinkedHashMap<>();
        for (Map.Entry<ModelAttributeSemantic, ModelVertexAttribute> entry : primitive.attributes().entrySet()) {
            ModelVertexAttribute source = entry.getValue();
            if (source.elementCount() != primitive.vertexCount()) {
                throw new IllegalArgumentException("canonical attribute vertex count mismatch: " + entry.getKey());
            }
            attributes.put(entry.getKey(), decode(source));
        }
        int[] indices = new int[primitive.indices().indexCount()];
        int[] occurrenceCounts = new int[primitive.vertexCount()];
        for (int occurrence = 0; occurrence < indices.length; occurrence++) {
            int vertex = Math.toIntExact(primitive.indices().indexAt(occurrence));
            if (vertex < 0 || vertex >= primitive.vertexCount()) {
                throw new IllegalArgumentException("canonical index outside vertex data");
            }
            indices[occurrence] = vertex;
            occurrenceCounts[vertex]++;
        }
        int[][] reverse = new int[primitive.vertexCount()][];
        for (int vertex = 0; vertex < reverse.length; vertex++) reverse[vertex] = new int[occurrenceCounts[vertex]];
        Arrays.fill(occurrenceCounts, 0);
        for (int occurrence = 0; occurrence < indices.length; occurrence++) {
            int vertex = indices[occurrence];
            reverse[vertex][occurrenceCounts[vertex]++] = occurrence;
        }
        return new HostCanonicalPrimitive(new Identity(meshIndex, primitiveIndex, primitive.materialIndex()),
                primitive.bounds(), primitive.vertexCount(), attributes, indices, reverse, nodeOccurrences);
    }

    public static long estimatedBytes(ModelPrimitive primitive) {
        long bytes = 0;
        for (ModelVertexAttribute attribute : primitive.attributes().values()) {
            bytes = Math.addExact(bytes, Math.multiplyExact((long) attribute.elementCount(),
                    Math.multiplyExact((long) attribute.componentCount(), Float.BYTES)));
        }
        return Math.addExact(bytes, Math.multiplyExact((long) primitive.indices().indexCount(),
                2L * Integer.BYTES));
    }

    public Identity identity() { return identity; }
    public int vertexCount() { return vertexCount; }
    public int occurrenceCount() { return canonicalIndices.length; }
    public int triangleCount() { return canonicalIndices.length / 3; }
    public ModelBounds bounds() { return bounds; }
    public Map<ModelAttributeSemantic, Attribute> attributes() { return attributes; }
    public Attribute attribute(ModelAttributeSemantic semantic) { return attributes.get(semantic); }
    public List<NodeOccurrence> nodeOccurrences() { return nodeOccurrences; }

    public float positionComponent(int vertexIndex, int component) {
        return Objects.requireNonNull(attribute(ModelAttributeSemantic.POSITION), "canonical POSITION")
                .component(vertexIndex, component);
    }

    public int canonicalIndexAtOccurrence(int occurrenceIndex) {
        if (occurrenceIndex < 0 || occurrenceIndex >= canonicalIndices.length) {
            throw new IndexOutOfBoundsException(occurrenceIndex);
        }
        return canonicalIndices[occurrenceIndex];
    }

    public int[] occurrencesForCanonicalVertex(int vertexIndex) {
        if (vertexIndex < 0 || vertexIndex >= vertexCount) throw new IndexOutOfBoundsException(vertexIndex);
        return Arrays.copyOf(occurrencesByVertex[vertexIndex], occurrencesByVertex[vertexIndex].length);
    }

    public float[] positions() { return attribute(ModelAttributeSemantic.POSITION).values(); }
    public int[] indices() { return Arrays.copyOf(canonicalIndices, canonicalIndices.length); }
    int[] projectionIndices() { return canonicalIndices; }

    private static Attribute decode(ModelVertexAttribute source) {
        float[] values = new float[Math.multiplyExact(source.elementCount(), source.componentCount())];
        ByteBuffer data = source.readOnlyData().order(ByteOrder.LITTLE_ENDIAN);
        int componentBytes = source.componentType().byteSize();
        int stride = componentBytes * source.componentCount();
        for (int element = 0; element < source.elementCount(); element++) {
            for (int component = 0; component < source.componentCount(); component++) {
                values[element * source.componentCount() + component] = component(data,
                        element * stride + component * componentBytes, source.componentType(), source.normalized());
            }
        }
        return new Attribute(source.semantic(), source.componentCount(), source.elementCount(), values);
    }

    private static float component(ByteBuffer data, int offset, ModelComponentType type, boolean normalized) {
        return switch (type) {
            case FLOAT32 -> data.getFloat(offset);
            case UINT8 -> normalized ? Byte.toUnsignedInt(data.get(offset)) / 255F : Byte.toUnsignedInt(data.get(offset));
            case INT8 -> normalized ? Math.max(data.get(offset) / 127F, -1F) : data.get(offset);
            case UINT16 -> normalized ? Short.toUnsignedInt(data.getShort(offset)) / 65535F
                    : Short.toUnsignedInt(data.getShort(offset));
            case INT16 -> normalized ? Math.max(data.getShort(offset) / 32767F, -1F) : data.getShort(offset);
            case UINT32 -> normalized ? Integer.toUnsignedLong(data.getInt(offset)) / 4294967295F
                    : Integer.toUnsignedLong(data.getInt(offset));
        };
    }

    public record Identity(int meshIndex, int primitiveIndex, int materialIndex) {
        public Identity {
            if (meshIndex < 0 || primitiveIndex < 0 || materialIndex < 0) {
                throw new IllegalArgumentException("canonical primitive identity must be non-negative");
            }
        }
    }

    public static final class Attribute {
        private final ModelAttributeSemantic semantic;
        private final int componentCount;
        private final int elementCount;
        private final float[] values;

        private Attribute(ModelAttributeSemantic semantic, int componentCount, int elementCount, float[] values) {
            this.semantic = semantic;
            this.componentCount = componentCount;
            this.elementCount = elementCount;
            this.values = values;
        }

        public ModelAttributeSemantic semantic() { return semantic; }
        public int componentCount() { return componentCount; }
        public int elementCount() { return elementCount; }
        public float component(int element, int component) {
            if (element < 0 || element >= elementCount || component < 0 || component >= componentCount) {
                throw new IndexOutOfBoundsException("canonical attribute component is outside the primitive");
            }
            return values[element * componentCount + component];
        }
        public float[] values() { return Arrays.copyOf(values, values.length); }
    }

    public static final class NodeOccurrence {
        private final int nodeIndex;
        private final int skinIndex;
        private final Matrix4f modelTransform;
        private final ModelBounds modelBounds;

        public NodeOccurrence(int nodeIndex, int skinIndex, Matrix4f modelTransform, ModelBounds modelBounds) {
            if (nodeIndex < 0 || skinIndex < -1) throw new IllegalArgumentException("invalid node occurrence identity");
            this.nodeIndex = nodeIndex;
            this.skinIndex = skinIndex;
            this.modelTransform = new Matrix4f(Objects.requireNonNull(modelTransform, "modelTransform"));
            this.modelBounds = Objects.requireNonNull(modelBounds, "modelBounds");
        }

        public int nodeIndex() { return nodeIndex; }
        public int skinIndex() { return skinIndex; }
        public boolean skinned() { return skinIndex >= 0; }
        public Matrix4f modelTransform() { return new Matrix4f(modelTransform); }
        public ModelBounds modelBounds() { return modelBounds; }
    }
}
