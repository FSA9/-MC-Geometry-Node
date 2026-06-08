package com.mine.geometry_node.client.ui.viewport.menu;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionId;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionRequest;
import com.mine.geometry_node.client.ui.viewport.interaction.InteractionContext;
import com.mine.geometry_node.client.ui.viewport.node.NodeVisualAdapter;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

public class PortMenu {
    private static final int PANEL_W_DP = 230;
    private static final int PANEL_PADDING_DP = 10;
    private static final int EDGE_MARGIN_DP = 6;
    private static final int INPUT_H_DP = 30;
    private static final int ACTION_H_DP = 28;

    private static final int COLOR_PANEL_BG = 0xF02B2D33;
    private static final int COLOR_PANEL_BORDER = 0xFF15171B;
    private static final int COLOR_INPUT_BG = 0xFF181B20;
    private static final int COLOR_INPUT_BORDER = 0xFF3A404A;
    private static final int COLOR_LABEL = 0xFF8F98A6;
    private static final int COLOR_TEXT = 0xFFE7EAF0;
    private static final int COLOR_BUTTON = 0xFF3E4652;
    private static final int COLOR_BUTTON_PRIMARY = 0xFF4B7FBD;

    public static void show(InteractionContext context, NodeVisualAdapter node, String portId, float screenX, float screenY) {
        String category = "inputs";
        boolean found = false;
        String defaultName = "";

        for (PortRow row : node.getNodeDef().rows()) {
            if (row.leftPort() != null && row.leftPort().id().equals(portId)) {
                category = row.leftPort().type() == PortType.EXECUTION ? "exec_inputs" : "inputs";
                defaultName = row.leftPort().displayName().getString();
                found = true;
                break;
            }
            if (row.rightPort() != null && row.rightPort().id().equals(portId)) {
                category = row.rightPort().type() == PortType.EXECUTION ? "exec_outputs" : "outputs";
                defaultName = row.rightPort().displayName().getString();
                found = true;
                break;
            }
        }
        if (!found) return;

        String oldName = node.getNodeData().getEffectivePortName(category, portId, defaultName);
        final String finalCategory = category;

        Context uiContext = context.getUIContext();
        FrameLayout popupOverlay = new FrameLayout(uiContext);
        popupOverlay.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        popupOverlay.setOnClickListener(v -> close(context, popupOverlay));

        LinearLayout panel = new LinearLayout(uiContext);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP));
        panel.setBackground(rect(COLOR_PANEL_BG, 6.0f, 1, COLOR_PANEL_BORDER));
        panel.setOnClickListener(v -> {});

        TextView title = label(uiContext, "重命名端口", 13f, COLOR_TEXT, Gravity.CENTER_VERTICAL);
        panel.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));

        TextView nameLabel = label(uiContext, "名称", 10f, COLOR_LABEL, Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18));
        labelLp.topMargin = dp(4);
        panel.addView(nameLabel, labelLp);

        EditText input = new EditText(uiContext);
        input.setText(oldName);
        input.setTextColor(UIConstants.CLR_WHITE);
        input.setSingleLine(true);
        input.setTextSize(0, UIUtils.dp2px(12));
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setPadding(dp(9), 0, dp(9), 0);
        input.setBackground(rect(COLOR_INPUT_BG, 4.0f, 1, COLOR_INPUT_BORDER));

        panel.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(INPUT_H_DP)));

        LinearLayout actions = new LinearLayout(uiContext);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ACTION_H_DP));
        actionsLp.topMargin = dp(12);
        panel.addView(actions, actionsLp);

        TextView cancel = button(uiContext, "取消", COLOR_BUTTON, v -> close(context, popupOverlay));
        TextView apply = button(uiContext, "应用", COLOR_BUTTON_PRIMARY, v -> {
            String newName = input.getText().toString().trim();
            if (!newName.equals(oldName)) {
                context.getActionSink().performAction(
                        ViewportActionId.RENAME_PORT,
                        ViewportActionRequest.builder()
                                .nodeId(node.getNodeData().id)
                                .port(finalCategory, portId)
                                .rename(oldName, newName)
                                .build()
                );
            }
            close(context, popupOverlay);
        });

        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(dp(68), ViewGroup.LayoutParams.MATCH_PARENT);
        cancelLp.rightMargin = dp(8);
        actions.addView(cancel, cancelLp);
        actions.addView(apply, new LinearLayout.LayoutParams(dp(68), ViewGroup.LayoutParams.MATCH_PARENT));

        if (context instanceof ViewGroup parent) {
            popupOverlay.addView(panel, createPanelLayout(parent, screenX, screenY));
            parent.addView(popupOverlay);
            input.requestFocus();
            input.setSelection(0, oldName.length());
        }
    }

    private static FrameLayout.LayoutParams createPanelLayout(ViewGroup parent, float screenX, float screenY) {
        int panelW = dp(PANEL_W_DP);
        int panelH = dp(130);
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

    private static TextView button(Context context, String text, int color, View.OnClickListener listener) {
        TextView view = label(context, text, 12f, COLOR_TEXT, Gravity.CENTER);
        view.setBackground(rect(color, 4.0f, 1, 0x553C4658));
        view.setOnClickListener(listener);
        view.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                view.setTextColor(UIConstants.CLR_WHITE);
                view.setBackground(rect(lighten(color, 0.13f), 4.0f, 1, 0x664D5B70));
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                view.setTextColor(COLOR_TEXT);
                view.setBackground(rect(color, 4.0f, 1, 0x553C4658));
            }
            return false;
        });
        return view;
    }

    private static void close(InteractionContext context, FrameLayout overlay) {
        if (overlay.getParent() instanceof ViewGroup parent) {
            parent.removeView(overlay);
        }
        context.requestViewportFocus();
    }

    private static ShapeDrawable rect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeWidthDp > 0) {
            drawable.setStroke(dp(strokeWidthDp), strokeColor);
        }
        return drawable;
    }

    private static int dp(float value) {
        return UIUtils.dp2pxInt(value);
    }

    private static int lighten(int color, float amount) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.min(255, Math.round(r + (255 - r) * amount));
        g = Math.min(255, Math.round(g + (255 - g) * amount));
        b = Math.min(255, Math.round(b + (255 - b) * amount));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
