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
    private static final VertexFormatElement TANGENT = VertexFormatElement.register(
            14, 0, VertexFormatElement.Type.BYTE, true, 4);
    private static final VertexFormatElement[] EXTRA_UV = {
            VertexFormatElement.register(15, 0, VertexFormatElement.Type.FLOAT, false, 2),
            VertexFormatElement.register(16, 0, VertexFormatElement.Type.FLOAT, false, 2),
            VertexFormatElement.register(17, 0, VertexFormatElement.Type.FLOAT, false, 2),
            VertexFormatElement.register(18, 0, VertexFormatElement.Type.FLOAT, false, 2)
    };

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
            case TEXCOORD -> "UV" + semantic.setIndex();
            case COLOR -> "Color";
            case JOINTS -> "Joints";
            case WEIGHTS -> "Weights";
            case TANGENT -> "Tangent";
        };
    }

    private static VertexFormatElement nativeElement(ModelVertexLayoutElement element) {
        return switch (element.semantic().kind()) {
            case POSITION -> require(element, ModelComponentType.FLOAT32, 3, false, VertexFormatElement.POSITION);
            case NORMAL -> require(element, ModelComponentType.INT8, 3, true, VertexFormatElement.NORMAL);
            case TEXCOORD -> require(element, ModelComponentType.FLOAT32, 2, false,
                    element.semantic().setIndex() == 0 ? VertexFormatElement.UV0
                            : extraUv(element.semantic().setIndex()));
            case COLOR -> require(element, ModelComponentType.UINT8, 4, true, VertexFormatElement.COLOR);
            case JOINTS -> require(element, ModelComponentType.FLOAT32, 4, false, JOINTS);
            case WEIGHTS -> require(element, ModelComponentType.FLOAT32, 4, false, WEIGHTS);
            case TANGENT -> require(element, ModelComponentType.INT8, 4, true, TANGENT);
        };
    }

    private static VertexFormatElement extraUv(int setIndex) {
        if (setIndex < 1 || setIndex > EXTRA_UV.length) throw unsupported(
                ModelAttributeSemantic.indexed(ModelAttributeSemantic.Kind.TEXCOORD, setIndex));
        return EXTRA_UV[setIndex - 1];
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
