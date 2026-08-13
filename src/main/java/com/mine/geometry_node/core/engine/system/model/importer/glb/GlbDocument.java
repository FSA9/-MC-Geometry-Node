package com.mine.geometry_node.core.engine.system.model.importer.glb;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mine.geometry_node.core.engine.system.model.importer.*;

import java.util.ArrayList;
import java.util.List;
import java.nio.ByteBuffer;

final class GlbDocument {
    private static final String KHR_TEXTURE_TRANSFORM = "KHR_texture_transform";
    final JsonObject root;
    final ByteBuffer binary;
    final List<BufferView> bufferViews;
    final List<Accessor> accessors;
    final java.util.Set<String> usedExtensions;

    private GlbDocument(JsonObject root, ByteBuffer binary, List<BufferView> bufferViews,
                        List<Accessor> accessors, java.util.Set<String> usedExtensions) {
        this.root = root;
        this.binary = binary.asReadOnlyBuffer().order(java.nio.ByteOrder.LITTLE_ENDIAN);
        this.bufferViews = List.copyOf(bufferViews);
        this.accessors = List.copyOf(accessors);
        this.usedExtensions = java.util.Set.copyOf(usedExtensions);
    }

    static GlbDocument parse(JsonObject root, ByteBuffer binary, ModelImportSession session)
            throws ModelImportException {
        validateAsset(root);
        java.util.Set<String> usedExtensions = validateExtensions(root, session);
        int declaredBufferLength = validateBuffer(root, binary.remaining());
        List<BufferView> views = parseBufferViews(root, declaredBufferLength, session);
        List<Accessor> accessors = parseAccessors(root, views, session);
        return new GlbDocument(root, binary, views, accessors, usedExtensions);
    }

    Accessor accessor(int index, String location) throws ModelImportException {
        if (index < 0 || index >= accessors.size()) throw GlbFailures.reference(location, "accessor index is out of range");
        return accessors.get(index);
    }

    BufferView bufferView(int index, String location) throws ModelImportException {
        if (index < 0 || index >= bufferViews.size()) throw GlbFailures.reference(location, "bufferView index is out of range");
        return bufferViews.get(index);
    }

    private static void validateAsset(JsonObject root) throws ModelImportException {
        JsonObject asset = GlbJson.object(root.get("asset"), "asset");
        String version = GlbJson.string(asset, "version", "", "asset");
        if (!"2.0".equals(version)) throw GlbFailures.unsupported("asset.version", "only glTF 2.0 is supported");
        if (asset.has("minVersion") && !"2.0".equals(GlbJson.string(asset, "minVersion", "", "asset"))) {
            throw GlbFailures.unsupported("asset.minVersion", "asset requires an unsupported glTF version");
        }
    }

    private static java.util.Set<String> validateExtensions(JsonObject root, ModelImportSession session) throws ModelImportException {
        JsonArray used = GlbJson.array(root, "extensionsUsed");
        java.util.Set<String> usedNames = new java.util.HashSet<>();
        for (int i = 0; i < used.size(); i++) {
            String extension = extensionName(used.get(i), "extensionsUsed[" + i + "]");
            usedNames.add(extension);
            if (KHR_TEXTURE_TRANSFORM.equals(extension)) continue;
            session.diagnose(new ModelImportDiagnostic(ModelImportDiagnostic.Severity.WARNING,
                    "ignored_optional_extension", "extensionsUsed[" + i + "]",
                    "optional extension is not interpreted: " + extension));
        }
        JsonArray required = GlbJson.array(root, "extensionsRequired");
        for (int i = 0; i < required.size(); i++) {
            String extension = extensionName(required.get(i), "extensionsRequired[" + i + "]");
            if (!KHR_TEXTURE_TRANSFORM.equals(extension)) {
                throw GlbFailures.unsupported("extensionsRequired",
                        "required glTF extension is not supported: " + extension);
            }
            if (!usedNames.contains(extension)) {
                throw GlbFailures.invalid("extensionsRequired",
                        "required extension must also be listed in extensionsUsed: " + extension);
            }
        }
        return java.util.Set.copyOf(usedNames);
    }

    boolean usesExtension(String extension) { return usedExtensions.contains(extension); }

    private static String extensionName(JsonElement value, String location) throws ModelImportException {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw GlbFailures.invalid(location, "extension name must be a string");
        }
        return value.getAsString();
    }

    private static int validateBuffer(JsonObject root, int binaryLength) throws ModelImportException {
        JsonArray buffers = GlbJson.array(root, "buffers");
        if (buffers.size() != 1) throw GlbFailures.invalid("buffers", "M2 GLB requires exactly one embedded buffer");
        JsonObject buffer = GlbJson.object(buffers.get(0), "buffers[0]");
        if (buffer.has("uri")) throw GlbFailures.unsupported("buffers[0].uri", "external and data URI buffers are not supported in GLB");
        int byteLength = GlbJson.nonNegativeInt(buffer, "byteLength", -1, "buffers[0]");
        if (byteLength < 0) throw GlbFailures.invalid("buffers[0].byteLength", "buffer byteLength is required");
        if (byteLength > binaryLength || binaryLength - byteLength > 3) {
            throw GlbFailures.invalid("buffers[0].byteLength", "BIN chunk length does not match the declared buffer length");
        }
        return byteLength;
    }

    private static List<BufferView> parseBufferViews(JsonObject root, int binaryLength,
                                                      ModelImportSession session) throws ModelImportException {
        JsonArray array = GlbJson.array(root, "bufferViews");
        session.budgetTracker().claim(ModelBudgetResource.BUFFER_VIEWS, array.size(), "bufferViews");
        List<BufferView> views = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            session.checkpoint("bufferViews[" + i + "]");
            JsonObject value = GlbJson.object(array.get(i), "bufferViews[" + i + "]");
            int buffer = GlbJson.requiredInt(value, "buffer", "bufferViews[" + i + "]");
            if (buffer != 0) throw GlbFailures.reference("bufferViews[" + i + "].buffer", "only embedded buffer 0 is valid");
            int offset = GlbJson.nonNegativeInt(value, "byteOffset", 0, "bufferViews[" + i + "]");
            if ((offset & 3) != 0) {
                throw GlbFailures.invalid("bufferViews[" + i + "].byteOffset", "bufferView byteOffset must be four-byte aligned");
            }
            int length = GlbJson.nonNegativeInt(value, "byteLength", -1, "bufferViews[" + i + "]");
            if (length < 0) throw GlbFailures.invalid("bufferViews[" + i + "].byteLength", "byteLength is required");
            int stride = GlbJson.nonNegativeInt(value, "byteStride", 0, "bufferViews[" + i + "]");
            if (stride != 0 && (stride < 4 || stride > 252 || (stride & 3) != 0)) {
                throw GlbFailures.invalid("bufferViews[" + i + "].byteStride", "byteStride must be aligned and within [4, 252]");
            }
            requireRange(offset, length, binaryLength, "bufferViews[" + i + "]");
            views.add(new BufferView(offset, length, stride));
        }
        return views;
    }

    private static List<Accessor> parseAccessors(JsonObject root, List<BufferView> views,
                                                 ModelImportSession session) throws ModelImportException {
        JsonArray array = GlbJson.array(root, "accessors");
        session.budgetTracker().claim(ModelBudgetResource.ACCESSORS, array.size(), "accessors");
        List<Accessor> accessors = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            session.checkpoint("accessors[" + i + "]");
            String location = "accessors[" + i + "]";
            JsonObject value = GlbJson.object(array.get(i), location);
            if (value.has("sparse")) throw GlbFailures.unsupported(location + ".sparse", "sparse accessors are not supported in M2");
            int viewIndex = GlbJson.requiredInt(value, "bufferView", location);
            if (viewIndex < 0 || viewIndex >= views.size()) throw GlbFailures.reference(location + ".bufferView", "bufferView index is out of range");
            int offset = GlbJson.nonNegativeInt(value, "byteOffset", 0, location);
            int componentType = GlbJson.requiredInt(value, "componentType", location);
            int componentBytes = componentBytes(componentType, location + ".componentType");
            int count = GlbJson.nonNegativeInt(value, "count", -1, location);
            if (count < 1) throw GlbFailures.invalid(location + ".count", "accessor count must be positive");
            String typeName = GlbJson.string(value, "type", "", location);
            int components = componentCount(typeName, location + ".type");
            boolean normalized = GlbJson.bool(value, "normalized", false, location);
            int elementBytes = Math.multiplyExact(componentBytes, components);
            BufferView view = views.get(viewIndex);
            int stride = view.stride == 0 ? elementBytes : view.stride;
            if (stride < elementBytes || offset % componentBytes != 0) {
                throw GlbFailures.invalid(location, "accessor stride or alignment is invalid");
            }
            long lastEnd = Math.addExact(offset,
                    Math.addExact(Math.multiplyExact((long) (count - 1), stride), elementBytes));
            if (lastEnd > view.length) throw GlbFailures.invalid(location, "accessor exceeds its bufferView boundary");
            accessors.add(new Accessor(viewIndex, offset, componentType, componentBytes,
                    count, typeName, components, normalized, stride));
        }
        return accessors;
    }

    private static int componentBytes(int type, String location) throws ModelImportException {
        return switch (type) {
            case 5120, 5121 -> 1;
            case 5122, 5123 -> 2;
            case 5125, 5126 -> 4;
            default -> throw GlbFailures.unsupported(location, "unsupported accessor component type: " + type);
        };
    }

    private static int componentCount(String type, String location) throws ModelImportException {
        return switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            case "MAT2" -> 4;
            case "MAT3" -> 9;
            case "MAT4" -> 16;
            default -> throw GlbFailures.unsupported(location, "unsupported accessor type: " + type);
        };
    }

    private static void requireRange(int offset, int length, int total, String location) throws ModelImportException {
        long end = (long) offset + length;
        if (offset < 0 || length < 0 || end > total) throw GlbFailures.invalid(location, "byte range exceeds BIN chunk");
    }

    record BufferView(int offset, int length, int stride) {
    }

    record Accessor(int bufferView, int offset, int componentType, int componentBytes,
                    int count, String type, int components, boolean normalized, int stride) {
    }
}
