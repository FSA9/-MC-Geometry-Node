package com.mine.geometry_node.core.engine.system.model.importer.glb;

import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mine.geometry_node.core.engine.system.model.importer.protocol.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GlbAccessorDecoder {
    private final GlbDocument document;
    private final ModelImportSession session;
    private final Map<AttributeKey, ModelVertexAttribute> attributes = new HashMap<>();
    private final Map<Integer, ModelIndexBuffer> indices = new HashMap<>();

    GlbAccessorDecoder(GlbDocument document, ModelImportSession session) {
        this.document = document;
        this.session = session;
    }

    ModelVertexAttribute attribute(int accessorIndex, ModelAttributeSemantic semantic,
                                   String location) throws ModelImportException {
        AttributeKey key = new AttributeKey(accessorIndex, semantic);
        ModelVertexAttribute cached = attributes.get(key);
        if (cached != null) return cached;
        GlbDocument.Accessor accessor = document.accessor(accessorIndex, location);
        validateAttributeInput(accessor, semantic, location);
        int outputComponents = accessor.components();
        int componentBytes = semantic.is(ModelAttributeSemantic.Kind.JOINTS) ? Short.BYTES : Float.BYTES;
        int bytes = Math.toIntExact(Math.multiplyExact(Math.multiplyExact((long) accessor.count(), outputComponents), componentBytes));
        session.budgetTracker().claim(ModelBudgetResource.ATTRIBUTE_BYTES, bytes, location);
        ByteBuffer output = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int element = 0; element < accessor.count(); element++) {
            if ((element & 0x3FFF) == 0) session.checkpoint(location);
            for (int component = 0; component < outputComponents; component++) {
                if (semantic.is(ModelAttributeSemantic.Kind.JOINTS)) {
                    output.putShort((short) readUnsignedJoint(accessor, element, component));
                } else {
                    float value = readAsFloat(accessor, element, component, location);
                    if (!Float.isFinite(value)) throw GlbFailures.attribute(location, "vertex attribute contains a non-finite value");
                    output.putFloat(value);
                }
            }
        }
        if (semantic.equals(ModelAttributeSemantic.NORMAL)) normalizeNormals(output, accessor.count(), location);
        ModelVertexAttribute decoded = new ModelVertexAttribute(semantic,
                semantic.is(ModelAttributeSemantic.Kind.JOINTS) ? ModelComponentType.UINT16 : ModelComponentType.FLOAT32,
                outputComponents, false, accessor.count(), output.array());
        attributes.put(key, decoded);
        return decoded;
    }

    ModelIndexBuffer indices(int accessorIndex, String location) throws ModelImportException {
        ModelIndexBuffer cached = indices.get(accessorIndex);
        if (cached != null) return cached;
        GlbDocument.Accessor accessor = document.accessor(accessorIndex, location);
        if (!"SCALAR".equals(accessor.type()) || accessor.normalized()
                || (accessor.componentType() != 5121 && accessor.componentType() != 5123
                && accessor.componentType() != 5125)) {
            throw GlbFailures.attribute(location, "indices must use non-normalized unsigned SCALAR data");
        }
        int bytes = Math.multiplyExact(accessor.count(), accessor.componentBytes());
        session.budgetTracker().claim(ModelBudgetResource.ATTRIBUTE_BYTES, bytes, location);
        byte[] compact = new byte[bytes];
        for (int element = 0; element < accessor.count(); element++) {
            if ((element & 0x3FFF) == 0) session.checkpoint(location);
            int source = elementOffset(accessor, element);
            int target = element * accessor.componentBytes();
            for (int byteIndex = 0; byteIndex < accessor.componentBytes(); byteIndex++) {
                compact[target + byteIndex] = document.binary.get(source + byteIndex);
            }
        }
        ModelComponentType type = switch (accessor.componentType()) {
            case 5121 -> ModelComponentType.UINT8;
            case 5123 -> ModelComponentType.UINT16;
            case 5125 -> ModelComponentType.UINT32;
            default -> throw new IllegalStateException();
        };
        ModelIndexBuffer decoded = new ModelIndexBuffer(type, accessor.count(), compact);
        indices.put(accessorIndex, decoded);
        return decoded;
    }

    byte[] bufferViewBytes(int viewIndex, String location) throws ModelImportException {
        GlbDocument.BufferView view = document.bufferView(viewIndex, location);
        byte[] result = new byte[view.length()];
        ByteBuffer source = document.binary.asReadOnlyBuffer();
        source.position(view.offset()).limit(view.offset() + view.length());
        source.get(result);
        return result;
    }

    float[] animationFloats(int accessorIndex, int components, String location) throws ModelImportException {
        GlbDocument.Accessor accessor = document.accessor(accessorIndex, location);
        if (accessor.componentType() != 5126 || accessor.normalized() || accessor.components() != components) {
            throw GlbFailures.attribute(location, "animation accessors must use non-normalized FLOAT data with "
                    + components + " component(s)");
        }
        int valueCount = Math.multiplyExact(accessor.count(), components);
        session.budgetTracker().claim(ModelBudgetResource.ATTRIBUTE_BYTES,
                Math.multiplyExact(valueCount, Float.BYTES), location);
        float[] values = new float[valueCount];
        for (int element = 0; element < accessor.count(); element++) {
            if ((element & 0x3FFF) == 0) session.checkpoint(location);
            for (int component = 0; component < components; component++) {
                float value = readAsFloat(accessor, element, component, location);
                if (!Float.isFinite(value)) throw GlbFailures.attribute(location, "animation accessor contains a non-finite value");
                values[element * components + component] = value;
            }
        }
        return values;
    }

    List<ModelMatrix4> inverseBindMatrices(int accessorIndex, int jointCount, String location)
            throws ModelImportException {
        GlbDocument.Accessor accessor = document.accessor(accessorIndex, location);
        if (accessor.componentType() != 5126 || accessor.normalized() || !"MAT4".equals(accessor.type())
                || accessor.count() != jointCount) {
            throw GlbFailures.attribute(location,
                    "inverseBindMatrices must use non-normalized FLOAT MAT4 data matching the joint count");
        }
        session.budgetTracker().claim(ModelBudgetResource.ATTRIBUTE_BYTES,
                Math.multiplyExact(Math.multiplyExact(jointCount, 16), Float.BYTES), location);
        List<ModelMatrix4> matrices = new java.util.ArrayList<>(jointCount);
        for (int element = 0; element < jointCount; element++) {
            float[] values = new float[16];
            for (int component = 0; component < 16; component++) {
                values[component] = readAsFloat(accessor, element, component, location);
                if (!Float.isFinite(values[component])) {
                    throw GlbFailures.attribute(location, "inverse-bind matrix contains a non-finite value");
                }
            }
            matrices.add(new ModelMatrix4(values));
        }
        return List.copyOf(matrices);
    }

    private void validateAttributeInput(GlbDocument.Accessor accessor, ModelAttributeSemantic semantic,
                                        String location) throws ModelImportException {
        int components = switch (semantic.kind()) {
            case POSITION, NORMAL -> 3;
            case TANGENT -> 4;
            case TEXCOORD -> 2;
            case COLOR -> accessor.components();
            case JOINTS, WEIGHTS -> 4;
        };
        if (accessor.components() != components || (semantic.is(ModelAttributeSemantic.Kind.COLOR)
                && components != 3 && components != 4)) {
            throw GlbFailures.attribute(location, "accessor component count does not match " + semantic);
        }
        switch (semantic.kind()) {
            case POSITION, NORMAL, TANGENT -> {
                if (accessor.componentType() != 5126 || accessor.normalized()) {
                    throw GlbFailures.attribute(location, semantic + " must use non-normalized FLOAT data");
                }
            }
            case TEXCOORD -> {
                boolean valid = accessor.componentType() == 5126 && !accessor.normalized()
                        || (accessor.componentType() == 5121 || accessor.componentType() == 5123) && accessor.normalized();
                if (!valid) throw GlbFailures.attribute(location, "TEXCOORD_0 has an unsupported component representation");
            }
            case COLOR -> {
                boolean valid = accessor.componentType() == 5126 && !accessor.normalized()
                        || (accessor.componentType() == 5121 || accessor.componentType() == 5123) && accessor.normalized();
                if (!valid) throw GlbFailures.attribute(location, "COLOR_0 has an unsupported component representation");
            }
            case JOINTS -> {
                if ((accessor.componentType() != 5121 && accessor.componentType() != 5123) || accessor.normalized()) {
                    throw GlbFailures.attribute(location, "JOINTS_0 must use non-normalized UNSIGNED_BYTE or UNSIGNED_SHORT data");
                }
            }
            case WEIGHTS -> {
                boolean valid = accessor.componentType() == 5126 && !accessor.normalized()
                        || (accessor.componentType() == 5121 || accessor.componentType() == 5123) && accessor.normalized();
                if (!valid) throw GlbFailures.attribute(location,
                        "WEIGHTS_0 must use FLOAT or normalized UNSIGNED_BYTE/UNSIGNED_SHORT data");
            }
        }
    }

    private int readUnsignedJoint(GlbDocument.Accessor accessor, int element, int component) {
        int offset = elementOffset(accessor, element) + component * accessor.componentBytes();
        return accessor.componentType() == 5121
                ? Byte.toUnsignedInt(document.binary.get(offset))
                : Short.toUnsignedInt(document.binary.getShort(offset));
    }

    private float readAsFloat(GlbDocument.Accessor accessor, int element, int component,
                              String location) throws ModelImportException {
        int offset = elementOffset(accessor, element) + component * accessor.componentBytes();
        ByteBuffer input = document.binary;
        return switch (accessor.componentType()) {
            case 5120 -> accessor.normalized() ? Math.max(input.get(offset) / 127.0F, -1.0F) : input.get(offset);
            case 5121 -> accessor.normalized() ? Byte.toUnsignedInt(input.get(offset)) / 255.0F : Byte.toUnsignedInt(input.get(offset));
            case 5122 -> accessor.normalized() ? Math.max(input.getShort(offset) / 32767.0F, -1.0F) : input.getShort(offset);
            case 5123 -> accessor.normalized() ? Short.toUnsignedInt(input.getShort(offset)) / 65535.0F : Short.toUnsignedInt(input.getShort(offset));
            case 5125 -> {
                long value = Integer.toUnsignedLong(input.getInt(offset));
                yield accessor.normalized() ? (float) (value / 4294967295.0D) : value;
            }
            case 5126 -> input.getFloat(offset);
            default -> throw GlbFailures.attribute(location, "unsupported component type");
        };
    }

    private int elementOffset(GlbDocument.Accessor accessor, int element) {
        GlbDocument.BufferView view = document.bufferViews.get(accessor.bufferView());
        return Math.addExact(Math.addExact(view.offset(), accessor.offset()),
                Math.multiplyExact(element, accessor.stride()));
    }

    private static void normalizeNormals(ByteBuffer data, int count, String location) throws ModelImportException {
        for (int i = 0; i < count; i++) {
            int offset = i * 12;
            float x = data.getFloat(offset), y = data.getFloat(offset + 4), z = data.getFloat(offset + 8);
            double length = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
            if (!Double.isFinite(length) || length < 1.0E-12D) {
                throw GlbFailures.attribute(location, "normal contains a zero-length or non-finite vector");
            }
            data.putFloat(offset, (float) (x / length));
            data.putFloat(offset + 4, (float) (y / length));
            data.putFloat(offset + 8, (float) (z / length));
        }
    }

    private record AttributeKey(int accessorIndex, ModelAttributeSemantic semantic) {
    }
}
