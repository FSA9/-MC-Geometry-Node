package com.mine.geometry_node.client.runtime.marker;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mine.geometry_node.client.ui.components.common.SvgIconView;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;

@FunctionalInterface
public interface ClientMarkerRenderer {
    void render(GuiGraphicsExtractor graphics, MarkerRenderContext context);

    /** Optional addon preview. Default remains a neutral location symbol. */
    default View createPreview(Context context, int color) {
        return new SvgIconView(context,
                "/assets/geometry_node/icons/markers/default.svg", color);
    }
}
