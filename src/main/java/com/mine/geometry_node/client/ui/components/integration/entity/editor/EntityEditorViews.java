package com.mine.geometry_node.client.ui.components.integration.entity.editor;

import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.network.chat.Component;

import static com.mine.geometry_node.client.ui.components.base.drawable.UiDrawables.solid;

/** Entity editor presentation only; capability and persistence stay outside this helper. */
final class EntityEditorViews {
    private EntityEditorViews() {}
    /** Completion feedback survives disposal of the editor that initiated the operation. */
    static void reportFailure(String targetName, String message) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        minecraft.execute(() -> minecraft.gui.getChat().addClientSystemMessage(
                Component.translatable("geometry_node.entity_editor.title", targetName)
                        .append(": ").append(Component.translatable(message))
                        .append(" ").append(Component.translatable("geometry_node.entity_editor.reload_required"))));
    }
    static int px(float dp) { return UIUtils.dp2pxInt(dp); }
    static String tr(String key, Object... args) { return Component.translatable("geometry_node.entity_editor." + key, args).getString(); }
    static String message(String key) {
        if (key == null || key.isEmpty()) return "";
        return Component.translatable(key.startsWith("geometry_node.") ? key : "geometry_node." + key).getString();
    }
    static LinearLayout column(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static LinearLayout row(Context c) { LinearLayout v = new LinearLayout(c); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    static LinearLayout.LayoutParams size(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : px(w), h < 0 ? h : px(h)); }
    static UiDisplayText text(Context c, String value, int color) {
        UiDisplayText v = new UiDisplayText(c, value, UiMetrics.TEXT_BODY, color); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    static UiActionButton button(Context c, UiTheme theme, String key, Runnable action) {
        return button(c, theme, key, UiActionButton.Role.SECONDARY, action);
    }
    static UiActionButton button(Context c, UiTheme theme, String key, UiActionButton.Role role, Runnable action) {
        UiActionButton v = new UiActionButton(c, theme, tr(key), role, UiActionButton.Density.NORMAL,
                UiActionButton.Style.forTheme(theme));
        UIUtils.setLockedTextSize(v, UiMetrics.TEXT_BODY);
        v.setOnClickListener(ignored -> action.run()); return v;
    }
    static UiActionButton categoryButton(Context context, UiTheme theme, String label, Runnable action) {
        UiActionButton button = new UiActionButton(context, theme, label, UiActionButton.Role.QUIET,
                UiActionButton.Density.NORMAL, UiActionButton.Style.forTheme(theme));
        UIUtils.setLockedTextSize(button, UiMetrics.TEXT_BODY);
        button.setOnClickListener(ignored -> action.run());
        button.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        button.setPadding(px(10), 0, px(10), 0);
        button.setMinHeight(0); button.setMinimumHeight(0); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setSingleLine(true);
        button.setStyle(new UiActionButton.Style(0, 0, theme.border().subtle(),
                UiMetrics.DISABLED_ALPHA, theme.border().focus()));
        return button;
    }
    static UiTextInput input(Context c, UiTheme theme) {
        UiTextInput v = new UiTextInput(c, UiTextInput.Style.forTheme(theme, UiMetrics.TEXT_BODY)); v.setSingleLine(true); return v;
    }
    static void watch(UiTextInput v, Runnable action) {
        v.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable value) { action.run(); }
        });
    }
    static View divider(Context c, UiTheme theme) { View v = new View(c); v.setBackground(solid(theme.border().subtle())); return v; }

    /** Protect the action row; status gets the remainder, or its own line in a narrow host. */
    static final class Footer extends LinearLayout {
        private final UiDisplayText status;
        private final LinearLayout actions;

        Footer(Context context, UiDisplayText status, UiActionButton... buttons) {
            super(context);
            this.status = status;
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(px(16), px(12), px(16), px(12));
            status.setSingleLine(true);
            status.setEllipsize(TextUtils.TruncateAt.END);
            status.setMinWidth(0);
            status.setMinimumWidth(0);
            actions = row(context);
            actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            for (UiActionButton button : buttons) {
                button.setSingleLine(true);
                button.setEllipsize(TextUtils.TruncateAt.END);
                button.setMinWidth(px(88));
                button.setMinimumWidth(px(88));
                button.setMinHeight(0);
                button.setMinimumHeight(0);
                button.setPadding(px(12), 0, px(12), 0);
                LinearLayout.LayoutParams params = size(-2, 32);
                if (actions.getChildCount() > 0) params.leftMargin = px(8);
                actions.addView(button, params);
            }
            LinearLayout.LayoutParams words = size(0, 32);
            words.weight = 1;
            words.rightMargin = px(8);
            addView(status, words);
            addView(actions, size(-2, 32));
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int available = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE
                    : Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
            int maxButtonWidth = Math.max(px(88), (available - px(8) * (actions.getChildCount() - 1))
                    / actions.getChildCount());
            for (int i = 0; i < actions.getChildCount(); i++) {
                UiActionButton button = (UiActionButton) actions.getChildAt(i);
                if (button.getMaxWidth() != maxButtonWidth) button.setMaxWidth(maxButtonWidth);
            }
            actions.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(px(32), MeasureSpec.EXACTLY));
            boolean stacked = MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED
                    && MeasureSpec.getSize(widthSpec) < getPaddingLeft() + getPaddingRight()
                    + actions.getMeasuredWidth() + px(8) + px(96);
            int orientation = stacked ? VERTICAL : HORIZONTAL;
            if (getOrientation() != orientation) {
                setOrientation(orientation);
                LinearLayout.LayoutParams words = size(stacked ? -1 : 0, stacked ? 24 : 32);
                words.weight = stacked ? 0 : 1;
                words.rightMargin = stacked ? 0 : px(8);
                words.bottomMargin = stacked ? px(8) : 0;
                status.setLayoutParams(words);
                actions.setLayoutParams(size(stacked ? -1 : -2, 32));
            }
            super.onMeasure(widthSpec, heightSpec);
        }
    }
}
