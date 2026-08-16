package com.mine.geometry_node.client.model.gpu;

import com.mine.geometry_node.core.engine.system.model.domain.*;

import java.io.ByteArrayOutputStream;
import java.util.*;

public final class ModelGpuUploadPlanner {
    public ModelGpuUploadPlan plan(ModelDefinition definition, List<DecodedModelImage> images) {
        Objects.requireNonNull(definition, "definition");
        if (images == null || images.size() != definition.images().size()) {
            throw new IllegalArgumentException("decoded image count must match the model definition");
        }
        Map<ModelVertexLayout, GroupBuilder> groups = new LinkedHashMap<>();
        Map<PrimitiveKey, PrimitivePlacement> placements = new HashMap<>();
        for (int meshIndex = 0; meshIndex < definition.meshes().size(); meshIndex++) {
            List<ModelPrimitive> primitives = definition.meshes().get(meshIndex).primitives();
            for (int primitiveIndex = 0; primitiveIndex < primitives.size(); primitiveIndex++) {
                ModelPrimitive primitive = primitives.get(primitiveIndex);
                AttributeProjection projection = gpuLayout(primitive,
                        definition.materials().get(primitive.materialIndex()));
                ModelVertexLayout layout = projection.layout();
                GroupBuilder group = groups.computeIfAbsent(layout, ignored -> new GroupBuilder(layout, groups.size()));
                placements.put(new PrimitiveKey(meshIndex, primitiveIndex), group.append(primitive, projection));
            }
        }

        List<ModelGpuDrawRange> draws = new ArrayList<>();
        for (int nodeIndex = 0; nodeIndex < definition.nodes().size(); nodeIndex++) {
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
        private final ByteArrayOutputStream vertices = new ByteArrayOutputStream();
        private final ByteArrayOutputStream indices = new ByteArrayOutputStream();
        private int vertexCount;
        private int indexCount;

        private GroupBuilder(ModelVertexLayout layout, int groupIndex) {
            this.layout = layout;
            this.groupIndex = groupIndex;
            this.rawStride = layout.elements().stream()
                    .mapToInt(element -> Math.multiplyExact(element.componentType().byteSize(), element.componentCount()))
                    .sum();
            this.stride = align4(rawStride);
        }

        private PrimitivePlacement append(ModelPrimitive primitive, AttributeProjection projection) {
            int vertexBase = vertexCount;
            Map<ModelAttributeSemantic, ModelVertexAttribute> attributes = primitive.attributes();
            Map<ModelAttributeSemantic, byte[]> attributeData = new HashMap<>();
            for (ModelVertexAttribute attribute : attributes.values()) attributeData.put(attribute.semantic(), attribute.data());
            for (int vertex = 0; vertex < primitive.vertexCount(); vertex++) {
                for (ModelVertexLayoutElement element : layout.elements()) {
                    ModelAttributeSemantic sourceSemantic = projection.sources().get(element.semantic());
                    ModelVertexAttribute source = attributes.get(sourceSemantic);
                    if (source == null) throw new IllegalArgumentException("primitive does not match its GPU vertex layout");
                    writeElement(vertices, source, attributeData.get(sourceSemantic), vertex);
                }
                for (int padding = rawStride; padding < stride; padding++) vertices.write(0);
            }

            int firstIndex = indexCount;
            for (int i = 0; i < primitive.indices().indexCount(); i++) {
                long adjusted = Math.addExact(primitive.indices().indexAt(i), Integer.toUnsignedLong(vertexBase));
                if (adjusted > 0xFFFF_FFFFL) throw new IllegalArgumentException("combined model index exceeds uint32");
                writeUint32(indices, adjusted);
            }
            vertexCount = Math.addExact(vertexCount, primitive.vertexCount());
            indexCount = Math.addExact(indexCount, primitive.indices().indexCount());
            return new PrimitivePlacement(groupIndex, firstIndex, projection.physicalUvSlots());
        }

        private ModelGpuLayoutGroupPlan build() {
            return new ModelGpuLayoutGroupPlan(layout, stride, vertexCount, vertices.toByteArray(), indices.toByteArray());
        }

        private static void writeElement(ByteArrayOutputStream output, ModelVertexAttribute source,
                                         byte[] sourceData, int vertex) {
            int sourceElementSize = Math.multiplyExact(source.componentType().byteSize(), source.componentCount());
            int offset = Math.multiplyExact(vertex, sourceElementSize);
            switch (source.semantic().kind()) {
                case POSITION, TEXCOORD -> output.write(sourceData, offset, sourceElementSize);
                case NORMAL, TANGENT -> {
                    for (int component = 0; component < source.componentCount(); component++) {
                        float value = float32(sourceData, offset + component * 4);
                        output.write((byte) Math.round(Math.max(-1.0F, Math.min(1.0F, value)) * 127.0F));
                    }
                }
                case COLOR -> {
                    for (int component = 0; component < 4; component++) {
                        float value = component < source.componentCount()
                                ? float32(sourceData, offset + component * 4)
                                : 1.0F;
                        output.write(Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 255.0F));
                    }
                }
                case JOINTS -> {
                    for (int component = 0; component < 4; component++) {
                        output.writeBytes(floatBytes(unsignedComponent(sourceData,
                                offset + component * source.componentType().byteSize(), source.componentType())));
                    }
                }
                case WEIGHTS -> {
                    for (int component = 0; component < 4; component++) {
                        float value = source.componentType() == ModelComponentType.FLOAT32
                                ? float32(sourceData, offset + component * Float.BYTES)
                                : normalizedUnsignedComponent(sourceData,
                                offset + component * source.componentType().byteSize(), source.componentType());
                        output.writeBytes(floatBytes(value));
                    }
                }
            }
        }

        private static float float32(byte[] data, int offset) {
            int bits = (data[offset] & 0xFF) | (data[offset + 1] & 0xFF) << 8
                    | (data[offset + 2] & 0xFF) << 16 | data[offset + 3] << 24;
            return Float.intBitsToFloat(bits);
        }

        private static byte[] floatBytes(float value) {
            int bits = Float.floatToRawIntBits(value);
            return new byte[]{(byte) bits, (byte) (bits >>> 8), (byte) (bits >>> 16), (byte) (bits >>> 24)};
        }

        private static long unsignedComponent(byte[] data, int offset, ModelComponentType type) {
            return switch (type) {
                case UINT8 -> Byte.toUnsignedLong(data[offset]);
                case UINT16 -> Integer.toUnsignedLong((data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8));
                default -> throw new IllegalArgumentException("joint indices must be unsigned integers");
            };
        }

        private static float normalizedUnsignedComponent(byte[] data, int offset, ModelComponentType type) {
            return switch (type) {
                case UINT8 -> unsignedComponent(data, offset, type) / 255.0F;
                case UINT16 -> unsignedComponent(data, offset, type) / 65535.0F;
                default -> throw new IllegalArgumentException("weights must be float or normalized unsigned integers");
            };
        }

        private static int align4(int value) {
            return Math.addExact(value, 3) & ~3;
        }

        private static void writeUint32(ByteArrayOutputStream output, long value) {
            output.write((int) value & 0xFF);
            output.write((int) (value >>> 8) & 0xFF);
            output.write((int) (value >>> 16) & 0xFF);
            output.write((int) (value >>> 24) & 0xFF);
        }
    }
}
