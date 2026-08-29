package com.mine.geometry_node.client.ui.components.nativepreview;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A Minecraft-native nativepreview rendered by the viewport's shared raw render pass.
 */
public interface ViewportNativePreview {
    long getNativePreviewOrder();

    void renderNativePreview(
            GuiGraphicsExtractor graphics,
            float deltaTick,
            double guiScale,
            float alpha
    );
}
