package com.mine.geometry_node.client.ui.components.composite.table;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.FrameLayout;

/** Shared table presentation, independent of the rows' domain and interaction model. */
public final class UiTableStyle {
    public static final int HEADER_HEIGHT = 36;
    public static final int CELL_TEXT_SIZE_DP = 12;
    public static final int RESULT_BAR_HEIGHT = 38;
    public static final int STATUS_BAR_HEIGHT = 28;
    public static final int STATUS_TEXT_SIZE_DP = 11;
    public static final int CHECKBOX_SIZE = 16;
    public static final int SELECTION_COLUMN_WIDTH = 32;
    public static final int ROW_HEIGHT = 44;
    public static final int DETAILED_HEADER_HEIGHT = HEADER_HEIGHT;
    public static final int DETAILED_ROW_HEIGHT = 66;
    public static final int MIN_COLUMN_WIDTH = 24;
    public static final int ROW_NUMBER_WIDTH = 36;

    /** Display-order index, independent of data identity, sorting and editable data columns. */
    public static UiTableColumns.Column rowNumberColumn() {
        return new UiTableColumns.Column("row_number", ROW_NUMBER_WIDTH, ROW_NUMBER_WIDTH, false);
    }

    public static UiDisplayText rowNumberCell(Context context, int number) {
        return rowNumberCell(context, number, UiThemes.EDITOR);
    }

    public static UiDisplayText rowNumberCell(Context context, int number, UiTheme theme) {
        UiDisplayText cell = cell(context, number > 0 ? Integer.toString(number) : "#", theme.text().muted());
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(0, 0, 0, 0);
        return cell;
    }

    public static UiTableColumns.Column selectionColumn() {
        return new UiTableColumns.Column("selection", SELECTION_COLUMN_WIDTH, SELECTION_COLUMN_WIDTH, false);
    }

    /** Shared alignment and hit target for header and body selection cells. */
    public static FrameLayout selectionCell(Context context, UiCheckBox checkbox) {
        FrameLayout host = new FrameLayout(context);
        int size = UIUtils.dp2pxInt(CHECKBOX_SIZE);
        host.addView(checkbox, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        host.setOnClickListener(view -> { if (checkbox.isEnabled()) checkbox.performClick(); });
        return host;
    }

    private UiTableStyle() {}

    /** Table footer shared by query results and catalog pages. Height is STATUS_BAR_HEIGHT. */
    public static UiDisplayText statusBar(Context context, String text, UiTheme theme) {
        UiDisplayText status = new UiDisplayText(context, text, STATUS_TEXT_SIZE_DP, theme.text().muted());
        status.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        status.setPadding(UIUtils.dp2pxInt(14), 0, UIUtils.dp2pxInt(14), 0);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        ShapeDrawable background = new ShapeDrawable(); background.setColor(theme.surface().panel());
        status.setBackground(background);
        return status;
    }

    public static UiTableColumns.Column column(String id, float preferredDp) {
        return new UiTableColumns.Column(id, preferredDp, MIN_COLUMN_WIDTH, true);
    }

    public static UiTableRow header(Context context, UiTableColumns columns) {
        return header(context, columns, UiThemes.EDITOR);
    }

    public static UiTableRow header(Context context, UiTableColumns columns, UiTheme theme) {
        UiTableRow row = new UiTableRow(context, columns, theme);
        row.setHeader(true);
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(theme.surface().header());
        row.setBackground(background);
        return row;
    }

    public static UiDisplayText cell(Context context, String text, UiTheme theme) {
        return cell(context, text, theme.text().normal());
    }

    public static UiDisplayText cell(Context context, String text, int color) {
        UiDisplayText cell = new UiDisplayText(context, text, CELL_TEXT_SIZE_DP, color);
        cell.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        cell.setPadding(UIUtils.dp2pxInt(9), 0, UIUtils.dp2pxInt(6), 0);
        cell.setSingleLine(true);
        cell.setEllipsize(TextUtils.TruncateAt.END);
        return cell;
    }

    public static UiActionButton headerCell(Context context, String text, Runnable action) {
        return headerCell(context, text, action, UiThemes.EDITOR);
    }

    public static UiActionButton headerCell(Context context, String text, Runnable action, UiTheme theme) {
        UiActionButton button = rowButton(context, theme);
        button.setText(text);
        button.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        button.setPadding(UIUtils.dp2pxInt(9), 0, UIUtils.dp2pxInt(6), 0);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        UIUtils.setLockedTextSize(button, CELL_TEXT_SIZE_DP);
        button.setPalette(rowPalette(false, theme.surface().header(), theme));
        if (action != null) button.setOnClickListener(view -> action.run());
        return button;
    }

    public static UiActionButton rowButton(Context context) {
        return rowButton(context, UiThemes.EDITOR);
    }

    public static UiActionButton rowButton(Context context, UiTheme theme) {
        UiActionButton button = new UiActionButton(context, theme, "", UiActionButton.Role.INLINE,
                UiActionButton.Density.COMPACT,
                new UiActionButton.Style(0, 0, 0, UiMetrics.DISABLED_ALPHA, theme.border().focus()));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    public static int rowColor(boolean alternate) {
        return rowColor(alternate, UiThemes.EDITOR);
    }

    public static int rowColor(boolean alternate, UiTheme theme) {
        return alternate ? theme.surface().root() : theme.surface().canvas();
    }

    public static UiActionButton.Palette rowPalette(boolean selected, int base) {
        return rowPalette(selected, base, UiThemes.EDITOR);
    }

    public static UiActionButton.Palette rowPalette(boolean selected, int base, UiTheme theme) {
        return UiActionButton.Palette.row(theme, selected, base);
    }

    public static int dividerColor(UiTheme theme) {
        return theme.border().divider();
    }
}
