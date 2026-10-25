package com.mine.geometry_node.client.ui.components.integration.faction;

import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.composite.overlay.ExpandedTextInputOverlay;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.core.node.value.RichTextValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;
import java.util.function.Consumer;
import static com.mine.geometry_node.client.ui.components.integration.faction.FactionUi.*;

/** Formatted preview and the existing rich-text editor; no HTML or translation-key data. */
public final class FactionRichField extends LinearLayout {
    public enum Appearance { FORM, INLINE }
    private final UiDisplayText preview;
    private final UiIconButton edit;
    private RichTextValue value = RichTextValue.EMPTY;
    private boolean editable = true;

    public FactionRichField(Context context, UiTheme theme, String description, boolean multiline,
                            Consumer<RichTextValue> submit) {
        this(context, theme, description, multiline, Appearance.FORM, submit);
    }
    public FactionRichField(Context context, UiTheme theme, String description, boolean multiline,
                            Appearance appearance, Consumer<RichTextValue> submit) {
        super(context); setGravity(appearance == Appearance.FORM ? Gravity.TOP : Gravity.CENTER_VERTICAL); setBaselineAligned(false);
        preview = new UiDisplayText(context, "", UiMetrics.TEXT_BODY, theme.text().normal());
        preview.setSingleLine(!multiline);
        if (appearance == Appearance.FORM) {
            var background = fill(theme.control().input());
            background.setStroke(Math.max(1, px(UiMetrics.BORDER)), theme.control().inputBorder());
            preview.setBackground(background);
            preview.setPadding(px(10), px(7), px(10), px(7));
            preview.setMinimumHeight(px(multiline ? 116 : 36));
        }
        addView(preview, new LayoutParams(0, -2, 1));
        edit = UiIconButton.createThemed(context, theme, SvgIconView.Icon.MORE_HORIZONTAL,
                description, appearance == Appearance.FORM ? UiIconButton.Preset.TOOLBAR : UiIconButton.Preset.INLINE);
        int size = appearance == Appearance.FORM ? 34 : 30;
        LayoutParams action = new LayoutParams(px(size), px(size)); action.leftMargin = px(8);
        addView(edit, action);
        edit.setOnClickListener(view -> {
            if (!editable) return;
            ExpandedTextInputOverlay.showRichText(context, edit, value, next -> {
                if (editable) submit.accept(next);
            }, null, theme);
        });
    }
    public void refresh(RichTextValue next) {
        value = next == null ? RichTextValue.EMPTY : next;
        preview.setText(FactionTextViews.display(value));
        preview.setTooltipText(value.plain());
    }
    public void setEditable(boolean enabled) { editable = enabled; edit.setEnabled(enabled); }
}
