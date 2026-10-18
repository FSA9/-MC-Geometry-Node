package com.mine.geometry_node.client.ui.components.menu;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Matrix;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ScrollView;

/** Arbitrary-content popup composed from the shared overlay and bounded scrolling panel. */
public final class UiPopover extends UiPopupOverlay {
    private final ScrollView panel;
    private final int width;
    private final int maxHeight;

    /** Size from the required child width in pixels, after individual UI dimensions are rounded. */
    public static UiPopover forContentWidth(Context context, View content, int contentWidthPx, int maxHeightDp) {
        return new UiPopover(context, content, contentWidthPx, maxHeightDp, true);
    }

    public UiPopover(Context context, View content, int widthDp) {
        this(context, content, widthDp, 0);
    }

    /** A zero maximum height uses the available host height. */
    public UiPopover(Context context, View content, int widthDp, int maxHeightDp) {
        this(context, content, widthDp, maxHeightDp, false);
    }

    private UiPopover(Context context, View content, int requestedWidth, int maxHeightDp, boolean contentWidth) {
        super(context);
        if (requestedWidth <= 0 || maxHeightDp < 0) throw new IllegalArgumentException("Invalid popup dimensions");
        maxHeight = UIUtils.dp2pxInt(maxHeightDp);
        panel = UiMenuPanelLayout.createPanel(context, content);
        width = contentWidth ? requestedWidth + panel.getPaddingLeft() + panel.getPaddingRight()
                : UIUtils.dp2pxInt(requestedWidth);
        addView(panel, new FrameLayout.LayoutParams(width, -2));
    }

    public void showAtRoot(View owner, FrameLayout host, float rootX, float rootY) {
        dismiss();
        if (owner == null || host == null || getParent() != null || host.isInLayout()
                || host.getWidth() <= 0 || host.getHeight() <= 0) return;
        float[] point = {rootX, rootY};
        if (!UIUtils.rootToLocal(host, rootX, rootY, new Matrix(), point)) return;
        FrameLayout.LayoutParams params = UiMenuPanelLayout.measurePanel(panel, width,
                host.getWidth(), host.getHeight(), maxHeight);
        params.leftMargin = UiMenuPanelLayout.clampStart(point[0], params.width, host.getWidth());
        params.topMargin = UiMenuPanelLayout.clampStart(point[1], params.height, host.getHeight());
        panel.setLayoutParams(params);
        attachOverlay(owner, host);
    }

    /** Refit dynamic contents while retaining the current local position and viewport bounds. */
    public void refreshSize() {
        if (!isOpen() || !(getParent() instanceof ViewGroup host)) return;
        FrameLayout.LayoutParams previous = (FrameLayout.LayoutParams) panel.getLayoutParams();
        FrameLayout.LayoutParams params = UiMenuPanelLayout.measurePanel(panel, width,
                host.getWidth(), host.getHeight(), maxHeight);
        params.leftMargin = UiMenuPanelLayout.clampStart(previous.leftMargin, params.width, host.getWidth());
        params.topMargin = UiMenuPanelLayout.clampStart(previous.topMargin, params.height, host.getHeight());
        panel.setLayoutParams(params);
        panel.scrollTo(0, 0);
    }

    public void showBelow(View anchor, FrameLayout host) {
        if (anchor == null) return;
        Matrix matrix = new Matrix();
        anchor.transformMatrixToGlobal(matrix);
        float[] point = {0, anchor.getHeight()};
        matrix.mapPoint(point);
        showAtRoot(anchor, host, point[0], point[1]);
    }

    @Override protected boolean containsPopup(float x, float y) {
        return x >= panel.getLeft() && x < panel.getRight() && y >= panel.getTop() && y < panel.getBottom();
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (oldW > 0 && oldH > 0 && (w != oldW || h != oldH)) dismiss();
    }
}
