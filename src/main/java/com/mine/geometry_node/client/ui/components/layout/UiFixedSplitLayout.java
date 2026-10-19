package com.mine.geometry_node.client.ui.components.layout;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;

/** One independently sized edge pane and one flexible pane. Requested sizes use project dp,
 * survive temporary space constraints, and never become percentages of the parent. */
public final class UiFixedSplitLayout extends LinearLayout {
    public enum Edge { FIRST, LAST }

    private final boolean horizontal;
    private final Edge edge;
    private final float minimumDp, flexibleMinimumDp;
    private final ThinResizableDivider divider;
    private final java.util.List<Runnable> userStateListeners = new java.util.ArrayList<>();
    private float preferredExtentDp;
    private View fixedPane, flexiblePane;
    private boolean collapsed;
    private int collapsedExtentPx;
    private float pendingExtentPx;

    public UiFixedSplitLayout(Context context, ResizableDivider.Orientation axis, Edge edge,
                              float preferredDp, float minimumDp, float flexibleMinimumDp) {
        super(context);
        if (edge == null || !Float.isFinite(preferredDp) || !Float.isFinite(minimumDp)
                || !Float.isFinite(flexibleMinimumDp) || minimumDp < 0 || flexibleMinimumDp < 0) {
            throw new IllegalArgumentException("Invalid split sizes");
        }
        horizontal = axis != ResizableDivider.Orientation.VERTICAL;
        this.edge = edge;
        this.minimumDp = Math.max(0, minimumDp);
        this.flexibleMinimumDp = Math.max(0, flexibleMinimumDp);
        preferredExtentDp = Math.max(this.minimumDp, preferredDp);
        setOrientation(horizontal ? HORIZONTAL : VERTICAL);
        setBaselineAligned(false);
        divider = new ThinResizableDivider(context, horizontal ? ResizableDivider.Orientation.HORIZONTAL
                : ResizableDivider.Orientation.VERTICAL, this::resizePane);
    }

    public void setPanes(View first, View last) {
        if (fixedPane != null) throw new IllegalStateException("Split panes already installed");
        fixedPane = edge == Edge.FIRST ? first : last;
        flexiblePane = edge == Edge.FIRST ? last : first;
        addView(first, paneParams(first == flexiblePane));
        addView(divider);
        addView(last, paneParams(last == flexiblePane));
    }

    public View fixedPane() { return fixedPane; }
    public View flexiblePane() { return flexiblePane; }
    public ThinResizableDivider divider() { return divider; }
    public float preferredExtentDp() { return preferredExtentDp; }

    /** Restore owner-managed logical dp. Nonfinite values are ignored; values below the minimum
     * are clamped. Layout constraints never overwrite this preference. No user event is emitted. */
    public void restorePreferredExtentDp(float value) {
        if (!Float.isFinite(value)) return;
        float next = Math.max(minimumDp, value);
        if (next == preferredExtentDp) return;
        preferredExtentDp = next;
        requestLayout();
    }

    /** User drag changes only; read preferredExtentDp() in the callback and let the owner store it.
     * Close on the UI thread to unsubscribe. Restore, collapse and measurement are silent. */
    public AutoCloseable addUserStateListener(Runnable listener) {
        java.util.Objects.requireNonNull(listener);
        Runnable subscription = listener::run;
        userStateListeners.add(subscription);
        return () -> userStateListeners.remove(subscription);
    }

    /** Collapse to a tab rail without replacing the expanded size. */
    public void setCollapsed(boolean value, int extentPx) {
        if (collapsed == value && collapsedExtentPx == extentPx) return;
        collapsed = value;
        collapsedExtentPx = Math.max(0, extentPx);
        requestLayout();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (fixedPane != null) {
            boolean visible = fixedPane.getVisibility() != GONE;
            divider.setVisibility(visible && !collapsed ? VISIBLE : GONE);
            int gutter = divider.getVisibility() == GONE ? 0 : UIUtils.dp2pxInt(8);
            LayoutParams dividerParams = (LayoutParams) divider.getLayoutParams();
            if (horizontal) dividerParams.width = gutter; else dividerParams.height = gutter;
            int spec = horizontal ? widthSpec : heightSpec;
            int padding = horizontal ? getPaddingLeft() + getPaddingRight() : getPaddingTop() + getPaddingBottom();
            int available = MeasureSpec.getMode(spec) == MeasureSpec.UNSPECIFIED
                    ? UIUtils.dp2pxInt(preferredExtentDp + flexibleMinimumDp)
                    : Math.max(0, MeasureSpec.getSize(spec) - padding - gutter);
            int extent = collapsed ? Math.min(available, collapsedExtentPx)
                    : clampExtent(UIUtils.dp2pxInt(preferredExtentDp), available);
            pendingExtentPx = extent;
            LayoutParams fixedParams = (LayoutParams) fixedPane.getLayoutParams();
            if (horizontal) fixedParams.width = extent; else fixedParams.height = extent;
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    private int clampExtent(int requested, int available) {
        int maximum = Math.max(0, available - UIUtils.dp2pxInt(flexibleMinimumDp));
        int minimum = Math.min(UIUtils.dp2pxInt(minimumDp), maximum);
        return Math.max(minimum, Math.min(maximum, requested));
    }

    private void resizePane(float delta) {
        if (!Float.isFinite(delta) || fixedPane == null || collapsed || fixedPane.getVisibility() == GONE) return;
        int available = horizontal ? getWidth() - getPaddingLeft() - getPaddingRight() - divider.getWidth()
                : getHeight() - getPaddingTop() - getPaddingBottom() - divider.getHeight();
        int requested = clampExtent(Math.round(pendingExtentPx + (edge == Edge.FIRST ? delta : -delta)), Math.max(0, available));
        if (requested == Math.round(pendingExtentPx)) return;
        pendingExtentPx = requested;
        preferredExtentDp = requested / UIUtils.dp2px(1);
        requestLayout();
        for (Runnable listener : java.util.List.copyOf(userStateListeners)) listener.run();
    }

    private LayoutParams paneParams(boolean flexible) {
        return new LayoutParams(horizontal ? 0 : LayoutParams.MATCH_PARENT,
                horizontal ? LayoutParams.MATCH_PARENT : 0, flexible ? 1 : 0);
    }
}
