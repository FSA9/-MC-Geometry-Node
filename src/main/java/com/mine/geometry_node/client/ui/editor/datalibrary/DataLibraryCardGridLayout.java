package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.view.MeasureSpec;

/**
 * Responsive grid used by the data library for value cards.
 *
 * <p>The old library rendered every entry as a full width row.  That makes
 * previews (entities, item stacks and block states) awkwardly wide and also
 * causes every editor to invent its own spacing.  This container owns the
 * geometry instead: children have a shared minimum width, fixed gutter and
 * equal column widths.  Cards remain ordinary views, so existing click and
 * drag behaviour is unaffected.</p>
 */
public final class DataLibraryCardGridLayout extends ViewGroup {
    /** Blender-like compact defaults. */
    public static final int DEFAULT_MIN_CARD_WIDTH_DP = 220;
    public static final int DEFAULT_GUTTER_DP = 6;
    public static final int DEFAULT_PADDING_DP = 4;

    private int minCardWidth;
    private int gutter;
    private int contentPadding;
    private int columnCount = 1;

    public DataLibraryCardGridLayout(icyllis.modernui.core.Context context) {
        this(context, DEFAULT_MIN_CARD_WIDTH_DP, DEFAULT_GUTTER_DP, DEFAULT_PADDING_DP);
    }

    public DataLibraryCardGridLayout(icyllis.modernui.core.Context context,
                                     int minCardWidthDp,
                                     int gutterDp,
                                     int paddingDp) {
        super(context);
        minCardWidth = dp(minCardWidthDp);
        gutter = dp(gutterDp);
        contentPadding = dp(paddingDp);
        setWillNotDraw(true);
    }

    public int getColumnCount() {
        return columnCount;
    }

    public int getMinCardWidth() {
        return minCardWidth;
    }

    public void setMinCardWidthDp(int value) {
        int next = dp(Math.max(1, value));
        if (next == minCardWidth) return;
        minCardWidth = next;
        requestLayout();
    }

    public void setGutterDp(int value) {
        int next = dp(Math.max(0, value));
        if (next == gutter) return;
        gutter = next;
        requestLayout();
    }

    public void setContentPaddingDp(int value) {
        int next = dp(Math.max(0, value));
        if (next == contentPadding) return;
        contentPadding = next;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int available = Math.max(0, width - contentPadding * 2);
        columnCount = Math.max(1, (available + gutter) / Math.max(1, minCardWidth + gutter));
        // Keep cards at their stable width.  Stretching them to consume the
        // whole row makes their geometry visibly jump while the editor split
        // is being dragged.  A narrower fallback is only used when the host
        // itself is narrower than one card.
        int cardWidth = Math.max(1, Math.min(minCardWidth,
                available - gutter * Math.max(0, columnCount - 1)));

        int rows = (getChildCount() + columnCount - 1) / columnCount;
        int[] rowHeights = new int[rows];
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.measure(MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int row = i / columnCount;
            rowHeights[row] = Math.max(rowHeights[row], child.getMeasuredHeight());
        }
        int desiredHeight = contentPadding * 2;
        for (int height : rowHeights) desiredHeight += height;
        if (rows > 1) desiredHeight += gutter * (rows - 1);
        int resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolvedHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int available = Math.max(0, width - contentPadding * 2);
        int cardWidth = Math.max(1, Math.min(minCardWidth,
                available - gutter * Math.max(0, columnCount - 1)));
        int y = contentPadding;
        int[] rowHeights = new int[(getChildCount() + columnCount - 1) / columnCount];
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            rowHeights[i / columnCount] = Math.max(rowHeights[i / columnCount], child.getMeasuredHeight());
        }
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int row = i / columnCount;
            int column = i % columnCount;
            int x = contentPadding + column * (cardWidth + gutter);
            child.layout(x, y, x + cardWidth, y + child.getMeasuredHeight());
            if (column == columnCount - 1 || i == getChildCount() - 1) y += rowHeights[row] + gutter;
        }
    }

    private static int dp(int value) {
        return UIUtils.dp2pxInt(value);
    }
}
