package com.mine.geometry_node.client.model.gpu;

import com.mine.geometry_node.core.engine.system.model.domain.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class ModelGpuUploadPlanner {
    public ModelGpuUploadPlan plan(ModelDefinition definition, List<DecodedModelImage> images) {
        return plan(definition, images, () -> false);
    }

    public ModelGpuUploadPlan plan(ModelDefinition definition, List<DecodedModelImage> images,
                                   BooleanSupplier cancellation) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(cancellation, "cancellation");
        if (images == null || images.size() != definition.images().size()) {
            throw new IllegalArgumentException("decoded image count must match the model definition");
        }
        Map<PrimitiveKey, AttributeProjection> projections = new HashMap<>();
        Map<ModelVertexLayout, GroupCapacity> capacities = new LinkedHashMap<>();
        for (int meshIndex = 0; meshIndex < definition.meshes().size(); meshIndex++) {
            ModelGpuPreparationService.cancelled(cancellation);
            List<ModelPrimitive> primitives = definition.meshes().get(meshIndex).primitives();
            for (int primitiveIndex = 0; primitiveIndex < primitives.size(); primitiveIndex++) {
                ModelPrimitive primitive = primitives.get(primitiveIndex);
                AttributeProjection projection = gpuLayout(primitive,
                        definition.materials().get(primitive.materialIndex()));
                projections.put(new PrimitiveKey(meshIndex, primitiveIndex), projection);
                capacities.merge(projection.layout(), GroupCapacity.of(projection.layout(), primitive), GroupCapacity::add);
            }
        }

        Map<ModelVertexLayout, GroupBuilder> groups = new LinkedHashMap<>();
        Map<PrimitiveKey, PrimitivePlacement> placements = new HashMap<>();
        for (int meshIndex = 0; meshIndex < definition.meshes().size(); meshIndex++) {
            ModelGpuPreparationService.cancelled(cancellation);
            List<ModelPrimitive> primitives = definition.meshes().get(meshIndex).primitives();
            for (int primitiveIndex = 0; primitiveIndex < primitives.size(); primitiveIndex++) {
                PrimitiveKey key = new PrimitiveKey(meshIndex, primitiveIndex);
                ModelPrimitive primitive = primitives.get(primitiveIndex);
                AttributeProjection projection = projections.get(key);
                ModelVertexLayout layout = projection.layout();
                GroupBuilder group = groups.computeIfAbsent(layout, ignored ->
                        new GroupBuilder(layout, groups.size(), capacities.get(layout)));
                placements.put(new PrimitiveKey(meshIndex, primitiveIndex), group.append(primitive, projection, cancellation));
            }
        }

        List<ModelGpuDrawRange> draws = new ArrayList<>();
        for (int nodeIndex = 0; nodeIndex < definition.nodes().size(); nodeIndex++) {
            ModelGpuPreparationService.cancelled(cancellation);
            int meshIndex = definition.nodes().get(nodeIndex).meshIndex();
            if (meshIndex < 0) continue;
            List<ModelPrimitive> primitives = definition.meshes().get(meshIndex).primitives();
            for (int primitiveIndex = 0; primitiveIndex < primitives.size(); primitiveIndex++) {
                ModelPrimitive primitive = primitives.get(primitiveIndex);
                PrimitivePlacement placement = placements.get(new PrimitiveKey(meshIndex, primitiveIndex));
                draws.add(new ModelGpuDrawRange(nodeIndex, meshIndex, primitiveIndex, placement.groupIndex(),
                        placement.firstIndex(), primitive.indices().indexCount(), primitive.materialIndex(),
                        primitive.bounds(), placement.physicalUvSlots()));
            }
        }
        draws.sort(Comparator.comparingInt(ModelGpuDrawRange::layoutGroupIndex)
                .thenComparingInt(ModelGpuDrawRange::materialIndex)
                .thenComparingInt(ModelGpuDrawRange::nodeIndex)
                .thenComparingInt(ModelGpuDrawRange::meshIndex)
                .thenComparingInt(ModelGpuDrawRange::primitiveIndex));

        List<ModelGpuLayoutGroupPlan> plans = groups.values().stream().map(GroupBuilder::build).toList();
        Map<ModelGpuTextureKey, Boolean> imageUsage = imageUsage(definition);
        List<ModelGpuImagePlan> imagePlans = new ArrayList<>(imageUsage.size());
        for (Map.Entry<ModelGpuTextureKey, Boolean> usage : imageUsage.entrySet()) {
            ModelGpuPreparationService.cancelled(cancellation);
            ModelGpuTextureKey key = usage.getKey();
            DecodedModelImage source = images.get(key.imageIndex());
            if (key.colorSpace() == ModelTextureColorSpace.SHADOW_OPACITY) {
                source = ModelShadowOpacityProjection.project(source);
            }
            imagePlans.add(new ModelGpuImagePlan(key, ModelImageMipChain.prepare(source,
                    usage.getValue(), key.colorSpace())));
        }
        return new ModelGpuUploadPlan(definition.source(), plans, draws, imagePlans);
    }

    private static Map<ModelGpuTextureKey, Boolean> imageUsage(ModelDefinition definition) {
        Map<ModelGpuTextureKey, Boolean> usage = new LinkedHashMap<>();
        for (ModelMaterial material : definition.materials()) {
            claimTexture(definition, usage, material.baseColorTexture(), ModelTextureColorSpace.SRGB_COLOR);
            if (material.alphaMode() == ModelAlphaMode.BLEND) {
                claimTexture(definition, usage, material.baseColorTexture(), ModelTextureColorSpace.SHADOW_OPACITY);
            }
            claimTexture(definition, usage, material.emissiveTexture(), ModelTextureColorSpace.SRGB_COLOR);
            claimTexture(definition, usage, material.metallicRoughness().texture(), ModelTextureColorSpace.LINEAR_DATA);
            claimTexture(definition, usage, material.normalTexture().texture(), ModelTextureColorSpace.NORMAL_VECTOR);
            claimTexture(definition, usage, material.occlusionTexture().texture(), ModelTextureColorSpace.LINEAR_DATA);
        }
        return Map.copyOf(usage);
    }

    private static void claimTexture(ModelDefinition definition, Map<ModelGpuTextureKey, Boolean> usage,
                                     ModelTextureInfo info, ModelTextureColorSpace colorSpace) {
        if (info.textureIndex() < 0) return;
        ModelTexture texture = definition.textures().get(info.textureIndex());
        ModelGpuTextureKey key = new ModelGpuTextureKey(texture.imageIndex(), colorSpace);
        usage.merge(key, texture.sampler().minFilter().mipmapped(), Boolean::logicalOr);
    }

    private record PrimitiveKey(int meshIndex, int primitiveIndex) {}
    private record PrimitivePlacement(int groupIndex, int firstIndex, Map<Integer, Integer> physicalUvSlots) {}

    private record AttributeProjection(ModelVertexLayout layout,
                                       Map<ModelAttributeSemantic, ModelAttributeSemantic> sources,
                                       Map<Integer, Integer> physicalUvSlots) {}

    private record GroupCapacity(int vertexBytes, int indexBytes) {
        private static GroupCapacity of(ModelVertexLayout layout, ModelPrimitive primitive) {
            int rawStride = layout.elements().stream()
                    .mapToInt(element -> Math.multiplyExact(element.componentType().byteSize(), element.componentCount()))
                    .sum();
            int stride = GroupBuilder.align4(rawStride);
            return new GroupCapacity(Math.multiplyExact(stride, primitive.vertexCount()),
                    Math.multiplyExact(Integer.BYTES, primitive.indices().indexCount()));
        }

        private GroupCapacity add(GroupCapacity other) {
            return new GroupCapacity(Math.addExact(vertexBytes, other.vertexBytes),
                    Math.addExact(indexBytes, other.indexBytes));
        }
    }

    private static AttributeProjection gpuLayout(ModelPrimitive primitive, ModelMaterial material) {
        if (material.normalTexture().texture().textureIndex() >= 0
                && !primitive.attributes().containsKey(ModelAttributeSemantic.TANGENT)) {
            throw new IllegalArgumentException("normal-mapped primitive requires a canonical TANGENT attribute");
        }
        ModelVertexLayout source = primitive.vertexLayout();
        List<ModelVertexLayoutElement> elements = new ArrayList<>(source.elements().size());
        Map<ModelAttributeSemantic, ModelAttributeSemantic> sources = new HashMap<>();
        Set<Integer> requiredUvSets = new LinkedHashSet<>();
        if (material.baseColorTexture().textureIndex() >= 0) requiredUvSets.add(material.baseColorTexture().texCoordSet());
        if (material.emissiveTexture().textureIndex() >= 0) requiredUvSets.add(material.emissiveTexture().texCoordSet());
        if (material.metallicRoughness().texture().textureIndex() >= 0) requiredUvSets.add(material.metallicRoughness().texture().texCoordSet());
        if (material.normalTexture().texture().textureIndex() >= 0) requiredUvSets.add(material.normalTexture().texture().texCoordSet());
        if (material.occlusionTexture().texture().textureIndex() >= 0) requiredUvSets.add(material.occlusionTexture().texture().texCoordSet());
        if (requiredUvSets.size() > 5) throw new IllegalArgumentException(
                "Minecraft model backend supports at most five referenced UV sets per draw: " + requiredUvSets);
        List<Integer> sortedUvSets = requiredUvSets.stream().sorted().toList();
        for (ModelVertexLayoutElement element : source.elements()) {
            ModelAttributeSemantic semantic = element.semantic();
            if ((semantic.is(ModelAttributeSemantic.Kind.JOINTS) || semantic.is(ModelAttributeSemantic.Kind.WEIGHTS))
                    && semantic.setIndex() != 0) {
                throw new IllegalArgumentException("Minecraft model backend cannot consume vertex attribute " + semantic);
            }
            if (!semantic.equals(ModelAttributeSemantic.POSITION) && !semantic.equals(ModelAttributeSemantic.NORMAL)
                    && !semantic.equals(ModelAttributeSemantic.TANGENT)
                    && !(semantic.is(ModelAttributeSemantic.Kind.TEXCOORD) && sortedUvSets.contains(semantic.setIndex()))
                    && !semantic.equals(ModelAttributeSemantic.COLOR_0)
                    && !semantic.equals(ModelAttributeSemantic.JOINTS_0) && !semantic.equals(ModelAttributeSemantic.WEIGHTS_0)) continue;
            ModelAttributeSemantic physical = semantic.is(ModelAttributeSemantic.Kind.TEXCOORD)
                    ? ModelAttributeSemantic.indexed(ModelAttributeSemantic.Kind.TEXCOORD,
                    sortedUvSets.indexOf(semantic.setIndex())) : semantic;
            elements.add(switch (physical.kind()) {
                case POSITION -> new ModelVertexLayoutElement(physical, ModelComponentType.FLOAT32, 3, false);
                case NORMAL -> new ModelVertexLayoutElement(physical, ModelComponentType.INT8, 3, true);
                case TANGENT -> new ModelVertexLayoutElement(physical, ModelComponentType.INT8, 4, true);
                case TEXCOORD -> new ModelVertexLayoutElement(physical, ModelComponentType.FLOAT32, 2, false);
                case COLOR -> new ModelVertexLayoutElement(physical, ModelComponentType.UINT8, 4, true);
                case JOINTS, WEIGHTS -> new ModelVertexLayoutElement(physical, ModelComponentType.FLOAT32, 4, false);
            });
            sources.put(physical, semantic);
        }
        Map<Integer, Integer> physicalUvSlots = new LinkedHashMap<>();
        for (int physical = 0; physical < sortedUvSets.size(); physical++) physicalUvSlots.put(sortedUvSets.get(physical), physical);
        return new AttributeProjection(new ModelVertexLayout(elements), Map.copyOf(sources), Map.copyOf(physicalUvSlots));
    }

    private static final class GroupBuilder {
        private final ModelVertexLayout layout;
        private final int groupIndex;
        private final int rawStride;
        private final int stride;
        private final byte[] vertices;
        private final byte[] indices;
        private int vertexCursor;
        private int indexCursor;
        private int vertexCount;
        private int indexCount;

        private GroupBuilder(ModelVertexLayout layout, int groupIndex, GroupCapacity capacity) {
            this.layout = layout;
            this.groupIndex = groupIndex;
            this.rawStride = layout.elements().stream()
                    .mapToInt(element -> Math.multiplyExact(element.componentType().byteSize(), element.componentCount()))
                    .sum();
            this.stride = align4(rawStride);
            this.vertices = new byte[capacity.vertexBytes()];
            this.indices = new byte[capacity.indexBytes()];
        }

        private PrimitivePlacement append(ModelPrimitive primitive, AttributeProjection projection,
                                          BooleanSupplier cancellation) {
            int vertexBase = vertexCount;
            Map<ModelAttributeSemantic, ModelVertexAttribute> attributes = primitive.attributes();
            Map<ModelAttributeSemantic, ByteBuffer> attributeData = new HashMap<>();
            for (ModelVertexAttribute attribute : attributes.values()) {
                attributeData.put(attribute.semantic(), attribute.readOnlyData().order(ByteOrder.LITTLE_ENDIAN));
            }
            for (int vertex = 0; vertex < primitive.vertexCount(); vertex++) {
                if ((vertex & 0x3FFF) == 0) ModelGpuPreparationService.cancelled(cancellation);
                for (ModelVertexLayoutElement element : layout.elements()) {
                    ModelAttributeSemantic sourceSemantic = projection.sources().get(element.semantic());
                    ModelVertexAttribute source = attributes.get(sourceSemantic);
                    if (source == null) throw new IllegalArgumentException("primitive does not match its GPU vertex layout");
                    vertexCursor = writeElement(vertices, vertexCursor, source,
                            attributeData.get(sourceSemantic), vertex);
                }
                vertexCursor += stride - rawStride;
            }

            int firstIndex = indexCount;
            for (int i = 0; i < primitive.indices().indexCount(); i++) {
                if ((i & 0x3FFF) == 0) ModelGpuPreparationService.cancelled(cancellation);
                long adjusted = Math.addExact(primitive.indices().indexAt(i), Integer.toUnsignedLong(vertexBase));
                if (adjusted > 0xFFFF_FFFFL) throw new IllegalArgumentException("combined model index exceeds uint32");
                indexCursor = writeUint32(indices, indexCursor, adjusted);
            }
            vertexCount = Math.addExact(vertexCount, primitive.vertexCount());
            indexCount = Math.addExact(indexCount, primitive.indices().indexCount());
            return new PrimitivePlacement(groupIndex, firstIndex, projection.physicalUvSlots());
        }

        private ModelGpuLayoutGroupPlan build() {
            if (vertexCursor != vertices.length || indexCursor != indices.length) {
                throw new IllegalStateException("layout group capacity does not match packed data");
            }
            return new ModelGpuLayoutGroupPlan(layout, stride, vertexCount, vertices, indices);
        }

        private static int writeElement(byte[] output, int cursor, ModelVertexAttribute source,
                                        ByteBuffer sourceData, int vertex) {
            int sourceElementSize = Math.multiplyExact(source.componentType().byteSize(), source.componentCount());
            int offset = Math.multiplyExact(vertex, sourceElementSize);
            switch (source.semantic().kind()) {
                case POSITION, TEXCOORD -> {
                    sourceData.get(offset, output, cursor, sourceElementSize);
                    cursor += sourceElementSize;
                }
                case NORMAL, TANGENT -> {
                    for (int component = 0; component < source.componentCount(); component++) {
                        float value = float32(sourceData, offset + component * 4);
                        output[cursor++] = (byte) Math.round(Math.max(-1.0F, Math.min(1.0F, value)) * 127.0F);
                    }
                }
                case COLOR -> {
                    for (int component = 0; component < 4; component++) {
                        float value = component < source.componentCount()
                                ? float32(sourceData, offset + component * 4)
                                : 1.0F;
                        output[cursor++] = (byte) Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 255.0F);
                    }
                }
                case JOINTS -> {
                    for (int component = 0; component < 4; component++) {
                        cursor = writeFloat(output, cursor, unsignedComponent(sourceData,
                                offset + component * source.componentType().byteSize(), source.componentType()));
                    }
                }
                case WEIGHTS -> {
                    for (int component = 0; component < 4; component++) {
                        float value = source.componentType() == ModelComponentType.FLOAT32
                                ? float32(sourceData, offset + component * Float.BYTES)
                                : normalizedUnsignedComponent(sourceData,
                                offset + component * source.componentType().byteSize(), source.componentType());
                        cursor = writeFloat(output, cursor, value);
                    }
                }
            }
            return cursor;
        }

        private static float float32(ByteBuffer data, int offset) {
            return data.getFloat(offset);
        }

        private static int writeFloat(byte[] output, int cursor, float value) {
            int bits = Float.floatToRawIntBits(value);
            output[cursor++] = (byte) bits;
            output[cursor++] = (byte) (bits >>> 8);
            output[cursor++] = (byte) (bits >>> 16);
            output[cursor++] = (byte) (bits >>> 24);
            return cursor;
        }

        private static long unsignedComponent(ByteBuffer data, int offset, ModelComponentType type) {
            return switch (type) {
                case UINT8 -> Byte.toUnsignedLong(data.get(offset));
                case UINT16 -> Short.toUnsignedLong(data.getShort(offset));
                default -> throw new IllegalArgumentException("joint indices must be unsigned integers");
            };
        }

        private static float normalizedUnsignedComponent(ByteBuffer data, int offset, ModelComponentType type) {
            return switch (type) {
                case UINT8 -> unsignedComponent(data, offset, type) / 255.0F;
                case UINT16 -> unsignedComponent(data, offset, type) / 65535.0F;
                default -> throw new IllegalArgumentException("weights must be float or normalized unsigned integers");
            };
        }

        private static int align4(int value) {
            return Math.addExact(value, 3) & ~3;
        }

        private static int writeUint32(byte[] output, int cursor, long value) {
            output[cursor++] = (byte) value;
            output[cursor++] = (byte) (value >>> 8);
            output[cursor++] = (byte) (value >>> 16);
            output[cursor++] = (byte) (value >>> 24);
            return cursor;
        }
    }
}
