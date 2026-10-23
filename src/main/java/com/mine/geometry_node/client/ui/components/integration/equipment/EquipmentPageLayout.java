package com.mine.geometry_node.client.ui.components.integration.equipment;

import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ScrollView;

/** Equipment-page geometry only; no inventory state, native rendering, or host-editor dependencies. */
final class EquipmentPageLayout extends EquipmentLayoutGroup {
    private final UiScrollView left;
    private final UiScrollView right;
    private final View divider;
    private final PaneContent equipment;
    private final PaneContent inventory;
    private final Workbench workbench;

    EquipmentPageLayout(Context context, UiTheme theme, Workbench workbench, View inventory) {
        super(context);
        this.workbench = workbench;
        equipment = new PaneContent(context, workbench, 390);
        this.inventory = new PaneContent(context, inventory, 460);
        left = pane(context, theme, equipment);
        right = pane(context, theme, this.inventory);
        divider = new View(context);
        ShapeDrawable line = new ShapeDrawable();
        line.setColor(theme.border().divider());
        divider.setBackground(line);
        // Physical left/right placement deliberately stays the same in RTL, like the prototype.
        addView(left);
        addView(divider);
        addView(right);
    }

    private static UiScrollView pane(Context context, UiTheme theme, PaneContent content) {
        UiScrollView scroll = new UiScrollView(context, theme.scroll());
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        return scroll;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = resolveSize(px(960), widthSpec);
        int height = resolveSize(px(520), heightSpec);
        boolean compact = width <= px(900);
        int horizontal = width <= px(640) ? 18 : compact ? 20 : 28;
        int vertical = width <= px(640) ? 18 : compact ? 20 : 24;
        equipment.setInsets(px(horizontal), px(vertical));
        inventory.setInsets(px(horizontal), px(vertical));
        if (workbench.setCompact(compact)) equipment.forceLayout();
        // Invalidate local measurement caches only when metrics change. forceLayout does not
        // request another traversal, unlike changing padding or layout parameters here.
        if (equipment.isLayoutRequested()) left.forceLayout();
        if (inventory.isLayoutRequested()) right.forceLayout();
        int line = Math.min(width, px(1));
        int leftWidth = Math.round((width - line) * .475f);
        int exactHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        left.measure(MeasureSpec.makeMeasureSpec(leftWidth, MeasureSpec.EXACTLY), exactHeight);
        divider.measure(MeasureSpec.makeMeasureSpec(line, MeasureSpec.EXACTLY), exactHeight);
        right.measure(MeasureSpec.makeMeasureSpec(width - line - leftWidth, MeasureSpec.EXACTLY), exactHeight);
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int split = left.getMeasuredWidth();
        int line = divider.getMeasuredWidth();
        left.layout(0, 0, split, b - t);
        divider.layout(split, 0, split + line, b - t);
        right.layout(split + line, 0, r - l, b - t);
    }

    /** Centers short content in its own viewport; tall content keeps its natural scroll extent. */
    private static final class PaneContent extends EquipmentLayoutGroup {
        private final View content;
        private final int maxWidth;
        private int horizontalInset;
        private int verticalInset;

        PaneContent(Context context, View content, int maxWidth) {
            super(context);
            this.content = content;
            this.maxWidth = px(maxWidth);
            addView(content);
        }

        void setInsets(int horizontal, int vertical) {
            if (horizontalInset == horizontal && verticalInset == vertical) return;
            horizontalInset = horizontal;
            verticalInset = vertical;
            forceLayout();
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int contentWidth = Math.max(0, Math.min(maxWidth, width - horizontalInset * 2));
            content.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            setMeasuredDimension(width, resolveSize(content.getMeasuredHeight() + verticalInset * 2 + px(32), heightSpec));
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int x = (r - l - content.getMeasuredWidth()) / 2;
            int spare = b - t - verticalInset * 2 - px(32) - content.getMeasuredHeight();
            int y = verticalInset + px(12) + Math.max(0, spare / 2);
            content.layout(x, y, x + content.getMeasuredWidth(), y + content.getMeasuredHeight());
        }
    }

    static final class Workbench extends EquipmentLayoutGroup {
        private final View preview;
        private final Groups groups;
        private int gap;

        Workbench(Context context, View preview, Groups groups) {
            super(context);
            this.preview = preview;
            this.groups = groups;
            addView(preview);
            addView(groups);
        }

        boolean setCompact(boolean compact) {
            int nextGap = px(compact ? 20 : 26);
            int nextGroupGap = px(compact ? 18 : 24);
            if (gap == nextGap && groups.gap == nextGroupGap) return false;
            gap = nextGap;
            groups.gap = nextGroupGap;
            groups.forceLayout();
            forceLayout();
            return true;
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            preview.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            groups.measure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            setMeasuredDimension(width, resolveSize(preview.getMeasuredHeight() + gap + groups.getMeasuredHeight(), heightSpec));
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int x = (r - l - preview.getMeasuredWidth()) / 2;
            preview.layout(x, 0, x + preview.getMeasuredWidth(), preview.getMeasuredHeight());
            int y = preview.getMeasuredHeight() + gap;
            groups.layout(0, y, r - l, y + groups.getMeasuredHeight());
        }
    }

    static final class Groups extends EquipmentLayoutGroup {
        private final View armor;
        private final View hands;
        private int gap;

        Groups(Context context, View armor, View hands) {
            super(context);
            this.armor = armor;
            this.hands = hands;
            addView(armor);
            addView(hands);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int available = Math.max(0, width - gap);
            int armorWidth = available * 2 / 3;
            int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            armor.measure(MeasureSpec.makeMeasureSpec(armorWidth, MeasureSpec.EXACTLY), unspecified);
            hands.measure(MeasureSpec.makeMeasureSpec(available - armorWidth, MeasureSpec.EXACTLY), unspecified);
            setMeasuredDimension(width, resolveSize(Math.max(armor.getMeasuredHeight(), hands.getMeasuredHeight()), heightSpec));
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            armor.layout(0, 0, armor.getMeasuredWidth(), armor.getMeasuredHeight());
            int x = armor.getMeasuredWidth() + gap;
            hands.layout(x, 0, x + hands.getMeasuredWidth(), hands.getMeasuredHeight());
        }
    }

    /** Bounds the actual native visibility anchor as well as its surrounding square. */
    static final class Square extends FrameLayout {
        private final int maxSize;

        Square(Context context, int maxSize) {
            super(context);
            this.maxSize = px(maxSize);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int size = resolveSize(maxSize, widthSpec);
            if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED)
                size = Math.min(size, MeasureSpec.getSize(heightSpec));
            int exact = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
            super.onMeasure(exact, exact);
        }
    }

    /** Horizontal equipment groups or native inventory rows; every cell is measured once per pass. */
    static final class SlotGrid extends EquipmentLayoutGroup {
        private final int columns;
        private final int gapDp;
        private final boolean squareCells;
        private int gap;
        private int cell;
        private int rowHeight;

        SlotGrid(Context context, int columns, int gapDp, boolean squareCells) {
            super(context);
            this.columns = columns;
            this.gapDp = gapDp;
            this.squareCells = squareCells;
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            gap = px(squareCells && width <= px(300) ? 3 : gapDp);
            cell = Math.max(0, (width - gap * (columns - 1)) / columns);
            int exact = MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY);
            rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                child.measure(exact, squareCells ? exact : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
            }
            int rows = (getChildCount() + columns - 1) / columns;
            setMeasuredDimension(width, resolveSize(Math.max(0, rows * (rowHeight + gap) - gap), heightSpec));
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                int x = i % columns * (cell + gap);
                int y = i / columns * (rowHeight + gap);
                child.layout(x, y, x + cell, y + child.getMeasuredHeight());
            }
        }
    }
}

/** Layouts use measured geometry directly, without changing layout parameters during a pass. */
abstract class EquipmentLayoutGroup extends ViewGroup {
    EquipmentLayoutGroup(Context context) { super(context); }
    @Override protected LayoutParams generateDefaultLayoutParams() { return new LayoutParams(-2, -2); }
    static int px(float value) { return UIUtils.dp2pxInt(value); }
}
