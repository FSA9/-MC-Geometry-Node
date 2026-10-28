package com.mine.geometry_node.client.ui.components.composite.table;

import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.R;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.view.View;

/** One native-state row surface for cells, virtual cards and specialized gesture hosts. */
public final class UiTableRowAppearance {
    private UiTableRowAppearance() {}

    public static void apply(View view, int index, boolean selected, UiTheme theme) {
        view.setSelected(selected);
        view.setMinimumHeight(UIUtils.dp2pxInt(UiTableStyle.ROW_HEIGHT));
        Surface surface = view.getBackground() instanceof Surface existing && existing.theme == theme
                ? existing : new Surface(theme);
        surface.setAlternate(index % 2 != 0);
        if (view.getBackground() != surface) view.setBackground(surface);
    }

    public static void drawDivider(Canvas canvas, View view, UiTheme theme) {
        Paint paint = Paint.obtain();
        paint.setColor(UiTableStyle.dividerColor(theme));
        canvas.drawRect(0, view.getHeight() - Math.max(1, UIUtils.dp2px(1)), view.getWidth(), view.getHeight(), paint);
        paint.recycle();
    }

    private static final class Surface extends ShapeDrawable {
        private final UiTheme theme;
        private Boolean alternate;
        Surface(UiTheme theme) {
            this.theme = theme;
            setCornerRadius(0);
            setStroke(Math.max(1, UIUtils.dp2pxInt(1)), new ColorStateList(new int[][] {
                    {R.attr.state_enabled, R.attr.state_focused}, {}
            }, new int[]{theme.border().focus(), 0}));
        }
        void setAlternate(boolean alternate) {
            if (this.alternate != null && this.alternate == alternate) return;
            this.alternate = alternate;
            int base = UiTableStyle.rowColor(alternate, theme);
            setColor(new ColorStateList(new int[][] {
                    {-R.attr.state_enabled, R.attr.state_selected}, {-R.attr.state_enabled},
                    {R.attr.state_selected, R.attr.state_pressed}, {R.attr.state_selected, R.attr.state_hovered},
                    {R.attr.state_selected}, {R.attr.state_pressed}, {R.attr.state_hovered}, {}
            }, new int[]{theme.selection().fill(), base, theme.selection().pressed(), theme.selection().hover(),
                    theme.selection().fill(), theme.control().iconPressed(), theme.control().iconHover(), base}));
        }
        @Override public void draw(Canvas canvas) {
            super.draw(canvas);
            var bounds = getBounds();
            Paint paint = Paint.obtain();
            paint.setColor(UiTableStyle.dividerColor(theme));
            canvas.drawRect(bounds.left, bounds.bottom - Math.max(1, UIUtils.dp2px(1)), bounds.right, bounds.bottom, paint);
            paint.recycle();
        }
    }
}
