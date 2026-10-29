package com.mine.geometry_node.client.ui.components.base.menu;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;

import static com.mine.geometry_node.client.ui.components.base.drawable.UiDrawables.rect;

/** Shared popup panel and host limits; callers retain their anchor and cascade policies. */
public final class UiMenuPanelLayout {
    private UiMenuPanelLayout() {}

    public static UiScrollView createPanel(Context context, View content) {
        return createPanel(context, content, UiThemes.EDITOR);
    }

    public static UiScrollView createPanel(Context context, View content, UiTheme theme) {
        java.util.Objects.requireNonNull(theme, "theme");
        UiScrollView panel = new UiScrollView(context, theme.scroll());
        panel.setOnClickListener(view -> {});
        panel.setBackground(rect(theme.surface().popup(), 4, 1, theme.border().strong()));
        panel.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        int padding = UIUtils.dp2pxInt(7);
        int side = Math.max(padding, panel.getVerticalScrollbarWidth()) + Math.max(1, UIUtils.dp2pxInt(4));
        panel.setPadding(side, padding, side, padding);
        panel.addView(content, new ViewGroup.LayoutParams(-1, -2));
        return panel;
    }

    /** All sizes are local pixels, already rounded by UIUtils. Zero maxHeight uses the host. */
    public static FrameLayout.LayoutParams measurePanel(View panel, int preferredWidthPx,
                                                        int availableWidthPx, int availableHeightPx,
                                                        int maxHeightPx) {
        int width = Math.max(0, Math.min(preferredWidthPx, availableWidthPx));
        int height = Math.max(0, availableHeightPx);
        if (maxHeightPx > 0) height = Math.min(height, maxHeightPx);
        panel.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        return new FrameLayout.LayoutParams(width, Math.min(height, panel.getMeasuredHeight()));
    }

    public static int clampStart(float requested, int panelExtent, int hostExtent) {
        return Math.round(Math.clamp(requested, 0, Math.max(0, hostExtent - panelExtent)));
    }
}
