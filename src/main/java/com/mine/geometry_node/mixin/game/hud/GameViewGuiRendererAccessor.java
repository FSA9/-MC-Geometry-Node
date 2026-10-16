package com.mine.geometry_node.mixin.game.hud;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Temporarily reuse the registered GUI renderer for the isolated in-game HUD. */
@Mixin(GuiRenderer.class)
public interface GameViewGuiRendererAccessor {
    @Accessor("renderState")
    GuiRenderState geometryNode$getRenderState();

    @Mutable
    @Accessor("renderState")
    void geometryNode$setRenderState(GuiRenderState state);

    @Accessor("vertexBuffers")
    Map<VertexFormat, MappableRingBuffer> geometryNode$getVertexBuffers();

    @Mutable
    @Accessor("vertexBuffers")
    void geometryNode$setVertexBuffers(Map<VertexFormat, MappableRingBuffer> buffers);
}
