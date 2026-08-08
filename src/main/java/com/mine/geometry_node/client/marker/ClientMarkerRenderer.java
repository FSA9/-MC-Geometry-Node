package com.mine.geometry_node.client.marker;

import net.minecraft.client.gui.GuiGraphicsExtractor;

@FunctionalInterface
public interface ClientMarkerRenderer {
    void render(GuiGraphicsExtractor graphics, MarkerRenderContext context);
}
