package com.mine.geometry_node.core.engine.system.model.importer.glb;

import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mine.geometry_node.core.engine.system.model.importer.protocol.*;
import com.mine.geometry_node.core.engine.system.model.tangent.MikkTSpaceContext;
import com.mine.geometry_node.core.engine.system.model.tangent.MikkTangentAlgorithm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/** Generates glTF tangents while preserving MikkTSpace's face-corner splits. */
final class GlbMikkTangentGenerator {
    private GlbMikkTangentGenerator() {}

    static Result generate(Map<ModelAttributeSemantic, ModelVertexAttribute> source,
                           ModelIndexBuffer sourceIndices, int texCoordSet,
                           ModelImportSession session, String location) throws ModelImportException {
        ModelVertexAttribute position = require(source, ModelAttributeSemantic.POSITION, location);
        ModelVertexAttribute normal = require(source, ModelAttributeSemantic.NORMAL, location);
        ModelAttributeSemantic uvSemantic = ModelAttributeSemantic.indexed(ModelAttributeSemantic.Kind.TEXCOORD, texCoordSet);
        ModelVertexAttribute uv = require(source, uvSemantic, location);
        int cornerCount = sourceIndices.indexCount();
        // The upstream implementation allocates several corner/triangle work arrays. Charge a
        // conservative linear bound before allocation so hostile input cannot bypass import policy.
        final long workspaceBytes;
        try {
            workspaceBytes = Math.multiplyExact((long) cornerCount, 192L);
        } catch (ArithmeticException exception) {
            throw GlbFailures.attribute(location + ".generatedTangents", "MikkTSpace workspace size overflowed");
        }
        session.budgetTracker().claim(ModelBudgetResource.ATTRIBUTE_BYTES, workspaceBytes,
                location + ".generatedTangents.workspace");
        session.checkpoint(location + ".generatedTangents");
        try {
            float[] cornerTangents = new float[Math.multiplyExact(cornerCount, 4)];
            Arrays.fill(cornerTangents, Float.NaN);
            Context context = new Context(position, normal, uv, sourceIndices, cornerTangents, session, location);
            final boolean generated;
            try {
                generated = MikkTangentAlgorithm.genTangSpaceDefault(context);
            } catch (Cancelled ignored) {
                session.cancellation().throwIfCancelled(location + ".generatedTangents");
                throw new AssertionError("cancellation signal without cancelled token");
            } catch (RuntimeException exception) {
                throw new ModelImportException(ModelImportFailure.simple(ModelImportErrorCode.INVALID_ATTRIBUTE,
                        location + ".generatedTangents", "MikkTSpace tangent generation failed"), exception);
            }
            if (!generated) throw GlbFailures.attribute(location + ".generatedTangents",
                    "MikkTSpace could not generate tangent space for this primitive");
            for (int i = 0; i < cornerTangents.length; i++) {
                if (!Float.isFinite(cornerTangents[i])) throw GlbFailures.attribute(
                        location + ".generatedTangents", "MikkTSpace produced a non-finite tangent");
            }
            return rebuild(source, sourceIndices, cornerTangents, session, location);
        } finally {
            session.budgetTracker().release(ModelBudgetResource.ATTRIBUTE_BYTES, workspaceBytes);
        }
    }

    private static ModelVertexAttribute require(Map<ModelAttributeSemantic, ModelVertexAttribute> source,
                                                ModelAttributeSemantic semantic, String location)
            throws ModelImportException {
        ModelVertexAttribute attribute = source.get(semantic);
        if (attribute == null) throw GlbFailures.attribute(location + ".attributes." + semantic,
                "normal-mapped primitive requires " + semantic + " for tangent generation");
        return attribute;
    }

    private static Result rebuild(Map<ModelAttributeSemantic, ModelVertexAttribute> source,
                                  ModelIndexBuffer sourceIndices, float[] tangents,
                                  ModelImportSession session, String location) throws ModelImportException {
        Map<VertexKey, Integer> vertices = new LinkedHashMap<>();
        int[] remapped = new int[sourceIndices.indexCount()];
        List<Integer> originals = new ArrayList<>();
        List<float[]> generated = new ArrayList<>();
        for (int corner = 0; corner < remapped.length; corner++) {
            if ((corner & 0x3FFF) == 0) session.checkpoint(location + ".generatedTangents.corners[" + corner + "]");
            int original = Math.toIntExact(sourceIndices.indexAt(corner));
            int tangentOffset = corner * 4;
            VertexKey key = new VertexKey(original,
                    Float.floatToIntBits(tangents[tangentOffset]), Float.floatToIntBits(tangents[tangentOffset + 1]),
                    Float.floatToIntBits(tangents[tangentOffset + 2]), Float.floatToIntBits(tangents[tangentOffset + 3]));
            Integer mapped = vertices.get(key);
            if (mapped == null) {
                mapped = vertices.size();
                vertices.put(key, mapped);
                originals.add(original);
                generated.add(Arrays.copyOfRange(tangents, tangentOffset, tangentOffset + 4));
            }
            remapped[corner] = mapped;
        }
        int newVertexCount = vertices.size();
        int oldVertexCount = source.get(ModelAttributeSemantic.POSITION).elementCount();
        if (newVertexCount > oldVertexCount) session.budgetTracker().claim(
                ModelBudgetResource.VERTICES, newVertexCount - (long) oldVertexCount, location + ".generatedTangents.vertices");
        ModelComponentType indexType = newVertexCount <= 256 ? ModelComponentType.UINT8
                : newVertexCount <= 65_536 ? ModelComponentType.UINT16 : ModelComponentType.UINT32;
        long copiedBytes = 0L;
        for (ModelVertexAttribute attribute : source.values()) {
            int stride = Math.multiplyExact(attribute.componentType().byteSize(), attribute.componentCount());
            copiedBytes = Math.addExact(copiedBytes, Math.multiplyExact((long) newVertexCount, stride));
        }
        int tangentBytes = Math.multiplyExact(newVertexCount, 16);
        int indexBytes = Math.multiplyExact(remapped.length, indexType.byteSize());
        long generatedBytes = Math.addExact(Math.addExact(copiedBytes, tangentBytes), indexBytes);
        session.budgetTracker().claim(ModelBudgetResource.ATTRIBUTE_BYTES, generatedBytes,
                location + ".generatedTangents");
        boolean published = false;
        try {
            Map<ModelAttributeSemantic, ModelVertexAttribute> attributes = new LinkedHashMap<>();
            for (var entry : source.entrySet()) {
                ModelVertexAttribute attribute = entry.getValue();
                int stride = Math.multiplyExact(attribute.componentType().byteSize(), attribute.componentCount());
                byte[] input = attribute.data();
                byte[] output = new byte[Math.multiplyExact(newVertexCount, stride)];
                for (int vertex = 0; vertex < newVertexCount; vertex++) {
                    System.arraycopy(input, Math.multiplyExact(originals.get(vertex), stride),
                            output, Math.multiplyExact(vertex, stride), stride);
                }
                attributes.put(entry.getKey(), new ModelVertexAttribute(entry.getKey(), attribute.componentType(),
                        attribute.componentCount(), attribute.normalized(), newVertexCount, output));
            }
            ByteBuffer tangentData = ByteBuffer.allocate(tangentBytes).order(ByteOrder.LITTLE_ENDIAN);
            for (float[] tangent : generated) for (float value : tangent) tangentData.putFloat(value);
            attributes.put(ModelAttributeSemantic.TANGENT, new ModelVertexAttribute(ModelAttributeSemantic.TANGENT,
                    ModelComponentType.FLOAT32, 4, false, newVertexCount, tangentData.array()));
            ByteBuffer indexData = ByteBuffer.allocate(indexBytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int index : remapped) switch (indexType) {
                case UINT8 -> indexData.put((byte) index);
                case UINT16 -> indexData.putShort((short) index);
                case UINT32 -> indexData.putInt(index);
                default -> throw new AssertionError(indexType);
            }
            ModelIndexBuffer resultIndices = new ModelIndexBuffer(indexType, remapped.length, indexData.array());
            // Decoder-owned accessor/index claims may be shared by multiple primitives and are
            // intentionally retained. This layer only owns the generated canonical buffers.
            published = true;
            return new Result(attributes, resultIndices);
        } finally {
            if (!published) session.budgetTracker().release(ModelBudgetResource.ATTRIBUTE_BYTES, generatedBytes);
        }
    }

    record Result(Map<ModelAttributeSemantic, ModelVertexAttribute> attributes, ModelIndexBuffer indices) {}
    private record VertexKey(int source, int x, int y, int z, int sign) {}
    private static final class Cancelled extends RuntimeException {
        @Override public synchronized Throwable fillInStackTrace() { return this; }
    }

    private static final class Context implements MikkTSpaceContext {
        private final ModelVertexAttribute position, normal, uv;
        private final ByteBuffer positionData, normalData, uvData;
        private final ModelIndexBuffer indices;
        private final float[] tangents;
        private final ModelImportSession session;
        private final String location;
        private int callbacks;

        private Context(ModelVertexAttribute position, ModelVertexAttribute normal, ModelVertexAttribute uv,
                        ModelIndexBuffer indices, float[] tangents, ModelImportSession session, String location) {
            this.position = position; this.normal = normal; this.uv = uv; this.indices = indices;
            this.positionData = position.readOnlyData().order(ByteOrder.LITTLE_ENDIAN);
            this.normalData = normal.readOnlyData().order(ByteOrder.LITTLE_ENDIAN);
            this.uvData = uv.readOnlyData().order(ByteOrder.LITTLE_ENDIAN);
            this.tangents = tangents; this.session = session; this.location = location;
        }
        @Override public int getNumFaces() { return indices.indexCount() / 3; }
        @Override public int getNumVerticesOfFace(int face) { return 3; }
        @Override public void getPosition(float[] output, int face, int vertex) { read(position, positionData, output, face, vertex); }
        @Override public void getNormal(float[] output, int face, int vertex) { read(normal, normalData, output, face, vertex); }
        @Override public void getTexCoord(float[] output, int face, int vertex) { read(uv, uvData, output, face, vertex); }
        @Override public void setTSpaceBasic(float[] tangent, float sign, int face, int vertex) {
            checkCancelled();
            int offset = (face * 3 + vertex) * 4;
            tangents[offset] = tangent[0]; tangents[offset + 1] = tangent[1];
            tangents[offset + 2] = tangent[2]; tangents[offset + 3] = sign;
        }
        @Override public void setTSpace(float[] tangent, float[] bitangent, float magnitudeS, float magnitudeT,
                                        boolean orientationPreserving, int face, int vertex) {}
        private void read(ModelVertexAttribute attribute, ByteBuffer data, float[] output, int face, int vertex) {
            checkCancelled();
            int source = Math.toIntExact(indices.indexAt(face * 3 + vertex));
            int componentBytes = attribute.componentType().byteSize();
            int offset = source * attribute.componentCount() * componentBytes;
            for (int component = 0; component < output.length; component++) output[component] = component < attribute.componentCount()
                    ? component(data, offset + component * componentBytes, attribute.componentType(), attribute.normalized()) : 0.0F;
        }
        private void checkCancelled() {
            if ((callbacks++ & 0x3FFF) == 0 && session.cancellation().isCancelled()) throw new Cancelled();
        }
        private static float component(ByteBuffer data, int offset, ModelComponentType type, boolean normalized) {
            return switch (type) {
                case FLOAT32 -> data.getFloat(offset);
                case UINT8 -> normalized ? Byte.toUnsignedInt(data.get(offset)) / 255.0F : Byte.toUnsignedInt(data.get(offset));
                case UINT16 -> normalized ? Short.toUnsignedInt(data.getShort(offset)) / 65535.0F : Short.toUnsignedInt(data.getShort(offset));
                case INT8 -> normalized ? Math.max(data.get(offset) / 127.0F, -1.0F) : data.get(offset);
                case INT16 -> normalized ? Math.max(data.getShort(offset) / 32767.0F, -1.0F) : data.getShort(offset);
                case UINT32 -> data.getInt(offset) & 0xFFFF_FFFFL;
            };
        }
    }
}
