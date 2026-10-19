package com.mine.geometry_node.client.ui.editor.database.sidebar;

import com.mine.geometry_node.client.ui.components.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.layout.TagFlowLayout;
import com.mine.geometry_node.client.ui.components.display.UiTagChip;
import com.mine.geometry_node.client.ui.components.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.input.UiSearchSubmit;
import com.mine.geometry_node.client.ui.components.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.layout.UiPropertyRow;
import com.mine.geometry_node.client.ui.editor.database.DatabaseSelection;
import com.mine.geometry_node.client.ui.editor.database.DatabaseWidgets;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

import static com.mine.geometry_node.client.ui.editor.database.DatabaseWidgets.tr;

/** Inspector presentation only; values and commands are supplied by the selected data source. */
final class DatabaseInspectorViews {
    private static final int TEXT = UiTheme.Text.NORMAL;
    private static final int MUTED = UiTheme.Text.MUTED;
    private static final int BORDER = UiTheme.Control.INPUT_BORDER;

    private DatabaseInspectorViews() {}

    static View identity(Context context, String title, String subtitle, View icon, int accent) {
        LinearLayout identity = new LinearLayout(context);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        if (icon != null) {
            FrameLayout badge = new FrameLayout(context);
            badge.setBackground(surface(accent == 0 ? UiTheme.Surface.HEADER : 0x22000000 | (accent & 0x00FFFFFF), 0));
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(px(23), px(23), Gravity.CENTER);
            badge.addView(icon, iconParams);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(px(37), px(37));
            badgeParams.rightMargin = px(11);
            identity.addView(badge, badgeParams);
        }
        LinearLayout copy = vertical(context);
        TextView heading = text(context, title, 12, UiTheme.Text.STRONG);
        heading.setMaxLines(3);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setTooltipText(title);
        copy.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        if (subtitle != null && !subtitle.isBlank()) {
            TextView detail = text(context, subtitle, 11, MUTED);
            detail.setSingleLine(true);
            detail.setEllipsize(TextUtils.TruncateAt.END);
            detail.setTooltipText(subtitle);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
            detailParams.topMargin = px(4);
            copy.addView(detail, detailParams);
        }
        identity.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        return identity;
    }

    static View actions(Context context, List<DatabaseSelection.Action> actions) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < actions.size(); i++) {
            DatabaseSelection.Action action = actions.get(i);
            UiActionButton button = action(context, action.label(), action.run(), i == 0);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, px(30), 1);
            if (i > 0) params.leftMargin = px(6);
            row.addView(button, params);
        }
        return row;
    }

    static LinearLayout section(Context context, String title, String subtitle) {
        LinearLayout section = vertical(context);
        View divider = new View(context);
        divider.setBackground(surface(UiTheme.Control.INPUT_BORDER, 0));
        section.addView(divider, new LinearLayout.LayoutParams(-1, Math.max(1, px(1))));
        LinearLayout heading = new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(context, title, 11, UiTheme.Text.SECONDARY);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        heading.addView(name, new LinearLayout.LayoutParams(0, px(25), 1));
        boolean hasSubtitle = subtitle != null && !subtitle.isBlank();
        boolean longSubtitle = hasSubtitle && subtitle.length() > 16;
        if (hasSubtitle && !longSubtitle) {
            TextView detail = text(context, subtitle, 11, MUTED);
            detail.setSingleLine(true);
            detail.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0, px(25), 1);
            detailParams.leftMargin = px(6);
            detail.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            heading.addView(detail, detailParams);
        }
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = px(9);
        headingParams.bottomMargin = px(5);
        section.addView(heading, headingParams);
        if (longSubtitle) {
            TextView note = text(context, subtitle, 11, MUTED);
            LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
            noteParams.bottomMargin = px(9);
            section.addView(note, noteParams);
        }
        return section;
    }

    static View metadata(Context context, DatabaseSelection.Field field) {
        TextView key = ellipsized(context, field.label(), MUTED);
        TextView value = ellipsized(context, field.value(), TEXT);
        return new UiPropertyRow(context, key, value, copy(context, field.value()),
                new UiPropertyRow.Layout(66, 29, 6, 22, 0));
    }

    static View position(Context context, List<DatabaseSelection.Field> axes) {
        LinearLayout grid = new LinearLayout(context);
        int[] colors = {0xFFB58284, 0xFF9AAF82, 0xFF85A4C7};
        for (int i = 0; i < axes.size(); i++) {
            DatabaseSelection.Field axis = axes.get(i);
            LinearLayout card = vertical(context);
            card.setPadding(px(7), px(6), px(7), px(6));
            card.setBackground(surface(UiTheme.Surface.ROOT, BORDER));
            card.addView(text(context, axis.label(), 11, colors[i % colors.length]),
                    new LinearLayout.LayoutParams(-1, px(17)));
            TextView value = ellipsized(context, axis.value(), TEXT);
            value.setTypeface(Typeface.MONOSPACED);
            card.addView(value, new LinearLayout.LayoutParams(-1, px(20)));
            card.setContentDescription(axis.label() + ": " + axis.value());
            // A real coordinate value remains available even when the narrow card is ellipsized.
            card.setOnClickListener(view -> copyValue(axis.value()));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) params.leftMargin = px(5);
            grid.addView(card, params);
        }
        return grid;
    }

    static View tags(Context context, List<DatabaseSelection.Field> fields, DatabaseSelection.TagEditor editor,
                     Supplier<DatabaseSelection.TagEditor> latestEditor) {
        LinearLayout content = vertical(context);
        TextView feedback = text(context, editor == null ? "" : editor.message(), 11, MUTED);
        feedback.setVisibility(feedback.getText().length() == 0 ? View.GONE : View.VISIBLE);
        TagFlowLayout tags = new TagFlowLayout(context, 4, 4);
        for (DatabaseSelection.Field field : fields) {
            if (field.value() == null || field.value().isBlank()) continue;
            var tag = new UiTagChip(context, field.value());
            if (editor != null) {
                tag.setOnRemove(tr("remove_tag"), () -> {
                    var latest = latestEditor.get();
                    if (latest == null || !latest.enabled()) return;
                    try { latest.remove().accept(field.value()); }
                    catch (IllegalArgumentException | IllegalStateException failure) { showTagError(feedback); }
                });
                tag.setEnabled(editor.enabled());
            }
            tags.addView(tag, new TagFlowLayout.LayoutParams(-2, px(UiTagChip.HEIGHT_DP)));
        }
        content.addView(tags, new LinearLayout.LayoutParams(-1, -2));
        if (editor != null) {
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            UiTextInput input = new UiTextInput(context);
            input.setSingleLine(true);
            input.setHint(tr("tag_input"));
            input.setEnabled(editor.enabled());
            row.addView(input, new LinearLayout.LayoutParams(0, px(28), 1));
            UiActionButton add = action(context, tr("add_tag"), () -> {}, false);
            add.setEnabled(editor.enabled());
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-2, px(28));
            buttonParams.leftMargin = px(6);
            row.addView(add, buttonParams);
            UiSearchSubmit.bind(add, () -> {
                var latest = latestEditor.get();
                if (latest == null || !latest.enabled() || input.getText().toString().isBlank()) return;
                try {
                    latest.add().accept(input.getText().toString());
                    input.setText("");
                } catch (IllegalArgumentException | IllegalStateException failure) { showTagError(feedback); }
            }, input);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.topMargin = px(6);
            content.addView(row, rowParams);
        }
        LinearLayout.LayoutParams feedbackParams = new LinearLayout.LayoutParams(-1, -2);
        feedbackParams.topMargin = px(6);
        content.addView(feedback, feedbackParams);
        return content;
    }

    private static void showTagError(TextView feedback) {
        feedback.setText(tr("tag_update_invalid"));
        feedback.setVisibility(View.VISIBLE);
    }

    static View block(Context context, DatabaseSelection.Field field, boolean reference) {
        LinearLayout block = vertical(context);
        block.setPadding(px(10), px(9), px(10), px(9));
        block.setBackground(surface(reference ? UiTheme.Surface.PANEL : UiTheme.Surface.ROOT,
                reference ? UiTheme.Control.INPUT_BORDER : BORDER));
        boolean hasLabel = field.label() != null && !field.label().isBlank();
        if (hasLabel) {
            LinearLayout heading = new LinearLayout(context);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            heading.addView(ellipsized(context, field.label(), MUTED),
                    new LinearLayout.LayoutParams(0, px(22), 1));
            heading.addView(copy(context, field.value()), new LinearLayout.LayoutParams(px(22), px(22)));
            block.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        }
        TextView value = text(context, field.value(), 11, reference ? UiTheme.Text.SECONDARY : UiTheme.Text.NORMAL);
        value.setTypeface(Typeface.MONOSPACED);
        value.setTooltipText(field.value());
        value.setMaxLines(8);
        value.setEllipsize(TextUtils.TruncateAt.END);
        if (hasLabel) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = px(5);
            block.addView(value, params);
        } else {
            LinearLayout row = new LinearLayout(context);
            row.addView(value, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(copy(context, field.value()), new LinearLayout.LayoutParams(px(22), px(22)));
            block.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        if (reference) {
            UiActionButton copyReference = action(context,
                    Component.translatable("geometry_node.data_library.copy_uuid").getString(),
                    () -> copyValue(field.value()), false);
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, px(28));
            buttonParams.topMargin = px(10);
            block.addView(copyReference, buttonParams);
        }
        return block;
    }

    static View source(Context context, List<DatabaseSelection.Field> fields) {
        LinearLayout origin = new LinearLayout(context);
        View accent = new View(context);
        accent.setBackground(surface(UiTheme.Control.FOCUS, 0));
        origin.addView(accent, new LinearLayout.LayoutParams(px(2), -1));
        LinearLayout rows = vertical(context);
        rows.setPadding(px(8), 0, 0, 0);
        for (DatabaseSelection.Field field : fields) {
            rows.addView(metadata(context, field), new LinearLayout.LayoutParams(-1, -2));
        }
        origin.addView(rows, new LinearLayout.LayoutParams(0, -2, 1));
        return origin;
    }

    static TextView text(Context context, String value, float size, int color) {
        TextView label = UIUtils.createLockedTextView(context, value == null ? "" : value, size, color);
        label.setGravity(Gravity.CENTER_VERTICAL);
        return label;
    }

    static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static int px(float value) { return UIUtils.dp2pxInt(value); }

    private static TextView ellipsized(Context context, String text, int color) {
        TextView label = text(context, text, 11, color);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTooltipText(text);
        return label;
    }

    private static UiActionButton action(Context context, String text, Runnable run, boolean primary) {
        return DatabaseWidgets.button(context, text, run,
                primary ? UiActionButton.Role.PRIMARY : UiActionButton.Role.SECONDARY);
    }

    private static UiIconButton copy(Context context, String value) {
        UiIconButton copy = new UiIconButton(context,
                new SvgIconView(context, SvgIconView.Icon.COPY, UiTheme.Text.MUTED),
                new UiIconButton.Style(1, 0, 0, UiTheme.Control.ICON_HOVER, UiTheme.Control.ICON_PRESSED, 0, 0, 12, .48f))
                .tooltip(tr("copy_value"));
        copy.setOnClickListener(view -> copyValue(value));
        return copy;
    }

    private static void copyValue(String value) {
        if (Boolean.getBoolean("gn.standalone") || value == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) minecraft.execute(() -> minecraft.keyboardHandler.setClipboard(value));
    }

    private static ShapeDrawable surface(int color, int border) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        if (border != 0) drawable.setStroke(Math.max(1, px(1)), border);
        return drawable;
    }
}
