package com.mine.geometry_node.client.ui.editor.graph.menu;

import static com.mine.geometry_node.client.ui.components.base.drawable.UiDrawables.rect;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.editor.graph.action.ViewportActionId;
import com.mine.geometry_node.client.ui.editor.graph.action.ViewportActionRequest;
import com.mine.geometry_node.client.ui.editor.graph.frame.FrameVisualAdapter;
import com.mine.geometry_node.client.ui.editor.graph.interaction.InteractionContext;
import com.mine.geometry_node.core.node.document.FrameData;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

public final class FrameMenu {
    private static final int PANEL_W_DP = 230;
    private static final int PANEL_PADDING_DP = 10;
    private static final int EDGE_MARGIN_DP = 6;
    private static final int INPUT_H_DP = 30;
    private static final int ACTION_H_DP = 28;
    private static final int SWATCH_SIZE_DP = 20;

    private static final int COLOR_PANEL_BG = (UiThemes.EDITOR.surface().popup() & 0x00FFFFFF) | 0xF0000000;
    private static final int COLOR_PANEL_BORDER = UiThemes.EDITOR.border().strong();
    private static final int COLOR_LABEL = UiThemes.EDITOR.text().muted();
    private static final int COLOR_TEXT = UiThemes.EDITOR.text().normal();

    // User-selectable document colors: these swatches are content, not a theme palette.
    private static final int[] PRESET_COLORS = {
            0xFF556677,
            0xFF4A90E2,
            0xFF4FA36C,
            0xFFD2A040,
            0xFFC66B5D,
            0xFF8C6ED8,
            0xFF42A7B8,
            0xFFB86AA8
    };

    private FrameMenu() {}

    public static void show(InteractionContext context, FrameVisualAdapter frame, float screenX, float screenY) {
        if (context == null || frame == null || !(context instanceof ViewGroup parent)) return;

        Context uiContext = context.getUIContext();
        FrameData frameData = frame.getFrameData();
        int initialColor = ensureOpaque(frameData.color);

        GraphMenuOverlay popupOverlay = new GraphMenuOverlay(context,
                () -> GraphMenuOverlay.containsIdentity(context.getAllFrameVisuals(), frame));
        popupOverlay.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        popupOverlay.setOnClickListener(v -> popupOverlay.close(true));

        LinearLayout panel = new LinearLayout(uiContext);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP));
        panel.setBackground(rect(COLOR_PANEL_BG, 6.0f, 1, COLOR_PANEL_BORDER));
        panel.setOnClickListener(v -> {});

        TextView header = label(uiContext, "图框设置", 13f, COLOR_TEXT, Gravity.CENTER_VERTICAL);
        panel.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));

        TextView titleLabel = label(uiContext, "标题", 10f, COLOR_LABEL, Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleLabelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18));
        titleLabelLp.topMargin = dp(4);
        panel.addView(titleLabel, titleLabelLp);

        EditText titleInput = new UiTextInput(uiContext, new UiTextInput.Style(11, 8,
                UiMetrics.RADIUS_INPUT, UiMetrics.BORDER, UiThemes.EDITOR.text().normal(), UiThemes.EDITOR.text().hint(),
                UiThemes.EDITOR.control().input(), UiThemes.EDITOR.control().inputBorder()));
        String oldTitle = frameData.title != null ? frameData.title : "";
        titleInput.setText(oldTitle);
        titleInput.setSingleLine(true);
        titleInput.setTextColor(UiThemes.EDITOR.text().strong());
        titleInput.setTextSize(0, UIUtils.dp2px(12));
        titleInput.setGravity(Gravity.CENTER_VERTICAL);
        titleInput.setPadding(dp(9), 0, dp(9), 0);
        panel.addView(titleInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(INPUT_H_DP)));

        TextView colorLabel = label(uiContext, "颜色", 10f, COLOR_LABEL, Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams colorLabelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18));
        colorLabelLp.topMargin = dp(10);
        panel.addView(colorLabel, colorLabelLp);

        GraphColorPalette swatches = new GraphColorPalette(uiContext, initialColor, PRESET_COLORS);
        panel.addView(swatches, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(SWATCH_SIZE_DP + 4)));

        LinearLayout actions = new LinearLayout(uiContext);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ACTION_H_DP));
        actionsLp.topMargin = dp(12);
        panel.addView(actions, actionsLp);

        UiActionButton cancel = UiActionButton.create(uiContext, "取消", UiActionButton.Role.SECONDARY,
                v -> popupOverlay.close(true));
        UiActionButton apply = UiActionButton.create(uiContext, "应用", UiActionButton.Role.PRIMARY, v -> {
            if (!popupOverlay.isCurrent()) return;
            String newTitle = titleInput.getText().toString().trim();
            if (newTitle.isEmpty()) newTitle = "New Frame";
            if (!newTitle.equals(oldTitle) || swatches.selectedColor() != initialColor) {
                context.getActionSink().performAction(
                        ViewportActionId.SET_FRAME_PROPERTY,
                        ViewportActionRequest.builder()
                                .frameId(frame.getFrameId())
                                .title(newTitle)
                                .color(swatches.selectedColor())
                                .build()
                );
            }
            popupOverlay.close(true);
        });

        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(dp(68), ViewGroup.LayoutParams.MATCH_PARENT);
        cancelLp.rightMargin = dp(8);
        actions.addView(cancel, cancelLp);
        actions.addView(apply, new LinearLayout.LayoutParams(dp(68), ViewGroup.LayoutParams.MATCH_PARENT));

        popupOverlay.addView(panel, createPanelLayout(parent, screenX, screenY));
        parent.addView(popupOverlay);
        popupOverlay.bindLifetime();
        titleInput.requestFocus();
        titleInput.setSelection(0, oldTitle.length());
    }

    private static FrameLayout.LayoutParams createPanelLayout(ViewGroup parent, float screenX, float screenY) {
        int panelW = dp(PANEL_W_DP);
        int panelH = dp(190);
        int edge = dp(EDGE_MARGIN_DP);

        int targetX = (int) screenX;
        int targetY = (int) screenY;
        if (parent.getWidth() > 0 && targetX + panelW + edge > parent.getWidth()) {
            targetX = Math.max(edge, parent.getWidth() - panelW - edge);
        }
        if (parent.getHeight() > 0 && targetY + panelH + edge > parent.getHeight()) {
            int aboveY = (int) screenY - panelH;
            targetY = aboveY >= edge ? aboveY : Math.max(edge, parent.getHeight() - panelH - edge);
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(panelW, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Math.max(edge, targetX);
        lp.topMargin = Math.max(edge, targetY);
        return lp;
    }

    private static TextView label(Context context, String text, float sizeDp, int color, int gravity) {
        TextView view = UIUtils.createLockedTextView(context, text, sizeDp, color);
        view.setGravity(gravity);
        view.setSingleLine(true);
        return view;
    }


    private static int dp(float value) {
        return UIUtils.dp2pxInt(value);
    }

    private static int ensureOpaque(int color) {
        return color | 0xFF000000;
    }

}
