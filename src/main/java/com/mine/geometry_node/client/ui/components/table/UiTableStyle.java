package com.mine.geometry_node.client.ui.components.table;

import com.mine.geometry_node.client.ui.components.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.display.UiDisplayText;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;

/** Shared table presentation, independent of the rows' domain and interaction model. */
public final class UiTableStyle {
    public static final int HEADER_HEIGHT = 30;
    public static final int ROW_HEIGHT = 44;
    public static final int MIN_COLUMN_WIDTH = 24;
    public static final int DIVIDER_COLOR = UiTheme.Control.INPUT_BORDER;
    public static final int ROW_NUMBER_WIDTH = 36;

    /** Display-order index, independent of data identity, sorting and editable data columns. */
    public static UiTableColumns.Column rowNumberColumn() {
        return new UiTableColumns.Column("row_number", ROW_NUMBER_WIDTH, ROW_NUMBER_WIDTH, false);
    }

    public static UiDisplayText rowNumberCell(Context context, int number) {
        UiDisplayText cell = cell(context, number > 0 ? Integer.toString(number) : "#", UiTheme.Text.MUTED);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(0, 0, 0, 0);
        return cell;
    }

    private UiTableStyle() {}

    public static UiTableColumns.Column column(String id, float preferredDp) {
        return new UiTableColumns.Column(id, preferredDp, MIN_COLUMN_WIDTH, true);
    }

    public static UiTableRow header(Context context, UiTableColumns columns) {
        UiTableRow row = new UiTableRow(context, columns);
        row.setHeader(true);
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(UiTheme.Surface.HEADER);
        row.setBackground(background);
        return row;
    }

    public static UiDisplayText cell(Context context, String text, int color) {
        UiDisplayText cell = new UiDisplayText(context, text, 12, color);
        cell.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        cell.setPadding(UIUtils.dp2pxInt(9), 0, UIUtils.dp2pxInt(6), 0);
        cell.setSingleLine(true);
        cell.setEllipsize(TextUtils.TruncateAt.END);
        return cell;
    }

    public static UiActionButton headerCell(Context context, String text, Runnable action) {
        UiActionButton button = rowButton(context);
        button.setText(text);
        button.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        button.setPadding(UIUtils.dp2pxInt(9), 0, UIUtils.dp2pxInt(6), 0);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        UIUtils.setLockedTextSize(button, 12);
        button.setPalette(rowPalette(false, UiTheme.Surface.HEADER));
        if (action != null) button.setOnClickListener(view -> action.run());
        return button;
    }

    public static UiActionButton rowButton(Context context) {
        UiActionButton button = new UiActionButton(context, "", UiActionButton.Role.INLINE,
                UiActionButton.Density.COMPACT,
                new UiActionButton.Style(0, 0, 0, UiTheme.Metrics.DISABLED_ALPHA));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    public static int rowColor(boolean alternate) {
        return alternate ? UiTheme.Surface.ROOT : UiTheme.Surface.CANVAS;
    }

    public static UiActionButton.Palette rowPalette(boolean selected, int base) {
        return new UiActionButton.Palette(selected ? UiTheme.Control.CHECKED : base,
                selected ? UiTheme.Control.FOCUS : UiTheme.Control.ICON_HOVER,
                selected ? UiTheme.Control.CHECKED : UiTheme.Control.ICON_PRESSED, UiTheme.Text.NORMAL);
    }
}
