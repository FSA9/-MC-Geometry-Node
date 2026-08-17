package com.mine.geometry_node.client.model.render.backend.host.geometry;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.List;

/** Resolves the runtime Minecraft ENTITY layout without leaking client classes into the pure packer contract. */
public final class MinecraftEntityLayoutFactory {
    private MinecraftEntityLayoutFactory() {}

    public static RuntimeContract current() {
        VertexFormat format = DefaultVertexFormat.ENTITY;
        List<VertexFormatElement> expected = List.of(VertexFormatElement.POSITION, VertexFormatElement.COLOR,
                VertexFormatElement.UV0, VertexFormatElement.UV1, VertexFormatElement.UV2, VertexFormatElement.NORMAL);
        if (!format.getElements().equals(expected)) {
            throw new IllegalStateException("unsupported vanilla ENTITY element order: " + format.getElements());
        }
        require(VertexFormatElement.POSITION, VertexFormatElement.Type.FLOAT, false, 3);
        require(VertexFormatElement.COLOR, VertexFormatElement.Type.UBYTE, true, 4);
        require(VertexFormatElement.UV0, VertexFormatElement.Type.FLOAT, false, 2);
        require(VertexFormatElement.UV1, VertexFormatElement.Type.SHORT, false, 2);
        require(VertexFormatElement.UV2, VertexFormatElement.Type.SHORT, false, 2);
        require(VertexFormatElement.NORMAL, VertexFormatElement.Type.BYTE, true, 3);
        return new RuntimeContract(new VanillaEntityGeometryPacker.EntityLayout(format.getVertexSize(),
                format.getOffset(VertexFormatElement.POSITION), format.getOffset(VertexFormatElement.COLOR),
                format.getOffset(VertexFormatElement.UV0), format.getOffset(VertexFormatElement.UV1),
                format.getOffset(VertexFormatElement.UV2), format.getOffset(VertexFormatElement.NORMAL)),
                OverlayTexture.NO_OVERLAY);
    }

    private static void require(VertexFormatElement element, VertexFormatElement.Type type,
                                boolean normalized, int count) {
        if (element.type() != type || element.normalized() != normalized || element.count() != count) {
            throw new IllegalStateException("unsupported vanilla ENTITY element: " + element);
        }
    }

    public record RuntimeContract(VanillaEntityGeometryPacker.EntityLayout layout, int noOverlay) {}
}
