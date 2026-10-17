package com.mine.geometry_node.client.ui.components.valuepreview;

import com.mine.geometry_node.client.runtime.marker.ClientMarkerRenderer;
import com.mine.geometry_node.client.ui.components.common.SvgIconView;
import com.mine.geometry_node.client.runtime.marker.ClientMarkerRendererRegistry;
import com.mine.geometry_node.core.engine.system.marker.MarkerType;
import com.mine.geometry_node.core.engine.system.marker.MarkerTypeRegistry;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

/** Passive preview delegated to the same registered renderer as the in-game HUD. */
public final class MarkerTypePreview extends FrameLayout {
    private final View icon;
    public MarkerTypePreview(Context context, String typeId) {
        super(context);
        MarkerType type = MarkerTypeRegistry.INSTANCE.get(typeId);
        ClientMarkerRenderer renderer = type == null ? null : ClientMarkerRendererRegistry.get(type.rendererId());
        int color = type == null ? 0xFF55CFFF : type.color();
        icon = renderer == null
                ? new SvgIconView(context,
                        "/assets/geometry_node/icons/markers/default.svg", color)
                : renderer.createPreview(context, color);
        addView(icon, new FrameLayout.LayoutParams(0, 0, Gravity.CENTER));
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int size = Math.round(Math.min(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec)) * 0.62f);
        icon.getLayoutParams().width = size;
        icon.getLayoutParams().height = size;
        super.onMeasure(widthSpec, heightSpec);
    }
}
