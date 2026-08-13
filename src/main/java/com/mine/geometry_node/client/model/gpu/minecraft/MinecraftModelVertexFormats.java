package com.mine.geometry_node.client.model.gpu.minecraft;

import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

public final class MinecraftModelVertexFormats {
    // VertexFormatElement.register is process-global. Pipeline variants must reuse these elements.
    private static final VertexFormatElement JOINTS = VertexFormatElement.register(
            12, 0, VertexFormatElement.Type.FLOAT, false, 4);
    private static final VertexFormatElement WEIGHTS = VertexFormatElement.register(
            13, 0, VertexFormatElement.Type.FLOAT, false, 4);

    private MinecraftModelVertexFormats() {}

    public static VertexFormat create(ModelVertexLayout layout) {
        VertexFormat.Builder builder = VertexFormat.builder();
        int rawStride = 0;
        for (ModelVertexLayoutElement element : layout.elements()) {
            builder.add(attributeName(element.semantic()), nativeElement(element));
            rawStride += element.componentType().byteSize() * element.componentCount();
        }
        int expectedStride = (rawStride + 3) & ~3;
        if (expectedStride > rawStride) builder.padding(expectedStride - rawStride);
        VertexFormat result = builder.build();
        if (result.getVertexSize() != expectedStride) {
            throw new IllegalStateException("Minecraft vertex format stride differs from the model GPU layout");
        }
        return result;
    }

    private static String attributeName(ModelAttributeSemantic semantic) {
        return switch (semantic.kind()) {
            case POSITION -> "Position";
            case NORMAL -> "Normal";
            case TEXCOORD -> "UV0";
            case COLOR -> "Color";
            case JOINTS -> "Joints";
            case WEIGHTS -> "Weights";
            case TANGENT -> throw unsupported(semantic);
        };
    }

    private static VertexFormatElement nativeElement(ModelVertexLayoutElement element) {
        return switch (element.semantic().kind()) {
            case POSITION -> require(element, ModelComponentType.FLOAT32, 3, false, VertexFormatElement.POSITION);
            case NORMAL -> require(element, ModelComponentType.INT8, 3, true, VertexFormatElement.NORMAL);
            case TEXCOORD -> require(element, ModelComponentType.FLOAT32, 2, false, VertexFormatElement.UV0);
            case COLOR -> require(element, ModelComponentType.UINT8, 4, true, VertexFormatElement.COLOR);
            case JOINTS -> require(element, ModelComponentType.FLOAT32, 4, false, JOINTS);
            case WEIGHTS -> require(element, ModelComponentType.FLOAT32, 4, false, WEIGHTS);
            case TANGENT -> throw unsupported(element.semantic());
        };
    }

    private static IllegalArgumentException unsupported(ModelAttributeSemantic semantic) {
        return new IllegalArgumentException("Minecraft model backend cannot consume vertex attribute " + semantic);
    }

    private static VertexFormatElement require(ModelVertexLayoutElement actual, ModelComponentType type,
                                               int count, boolean normalized, VertexFormatElement nativeElement) {
        if (actual.componentType() != type || actual.componentCount() != count || actual.normalized() != normalized) {
            throw new IllegalArgumentException("layout is not in the canonical M3 GPU format: " + actual.semantic());
        }
        return nativeElement;
    }

}
