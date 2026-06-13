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
    private static final int PANEL_W_DP = 220;
    private static final int PANEL_PADDING_DP = 8;
    private static final int EDGE_MARGIN_DP = 6;
    private static final int INPUT_H_DP = 28;
    private static final int ITEM_H_DP = 24;
    private static final int ITEM_RADIUS_DP = 4;
    private static final int SEARCH_RADIUS_DP = 5;

    private static final int COLOR_PANEL_BG = 0xFF2B2B2B;
    private static final int COLOR_PANEL_BORDER = 0xFF151515;
    private static final int COLOR_INPUT_BG = 0xFF1E1E1E;
    private static final int COLOR_INPUT_BORDER = 0xFF3A3A3A;
    private static final int COLOR_DIVIDER = 0xFF171717;
    private static final int COLOR_SECTION_TEXT = 0xFF777777;
    private static final int COLOR_ACTION_TEXT = 0xFF8FC7FF;
    private static final int COLOR_MUTED_TEXT = 0xFF999999;
    private static final int COLOR_HOVER_BG = 0xFF3A4652;

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
        panel.setBackground(rect(COLOR_PANEL_BG, UIConstants.ViewPort.NodeMenu.BORDER_RADIUS + 5, 1, COLOR_PANEL_BORDER));
        panel.setOnClickListener(v -> {});

        TextView section = sectionLabel(uiContext, "重命名端口");
        panel.addView(section, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));

        EditText input = new EditText(uiContext);
        input.setText(oldName);
        input.setHint("名称");
        input.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR_SEARCH);
        input.setHintTextColor(COLOR_SECTION_TEXT);
        input.setSingleLine(true);
        input.setTextSize(0, UIUtils.dp2px(12));
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setPadding(dp(10), 0, dp(10), 0);
        input.setBackground(rect(COLOR_INPUT_BG, SEARCH_RADIUS_DP, 1, COLOR_INPUT_BORDER));

        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(INPUT_H_DP));
        inputLp.bottomMargin = dp(8);
        panel.addView(input, inputLp);

        addDivider(uiContext, panel);

        panel.addView(menuItem(uiContext, "应用", COLOR_ACTION_TEXT, v -> {
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
        }), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ITEM_H_DP)));

        panel.addView(menuItem(uiContext, "取消", COLOR_MUTED_TEXT, v -> close(context, popupOverlay)),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ITEM_H_DP)));

        if (context instanceof ViewGroup parent) {
            popupOverlay.addView(panel, createPanelLayout(parent, screenX, screenY));
            parent.addView(popupOverlay);
            input.requestFocus();
            input.setSelection(0, oldName.length());
        }
    }

    private static FrameLayout.LayoutParams createPanelLayout(ViewGroup parent, float screenX, float screenY) {
        int panelW = dp(PANEL_W_DP);
        int panelH = dp(104);
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

    private static TextView sectionLabel(Context context, String text) {
        TextView view = label(context, text, 10f, COLOR_SECTION_TEXT, Gravity.CENTER_VERTICAL);
        view.setPadding(dp(10), 0, dp(10), 0);
        return view;
    }

    private static TextView menuItem(Context context, String text, int color, View.OnClickListener listener) {
        TextView view = label(context, text, 12f, color, Gravity.CENTER_VERTICAL);
        view.setPadding(dp(10), 0, dp(10), 0);
        view.setOnClickListener(listener);
        view.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                view.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR_HOVER);
                view.setBackground(rect(COLOR_HOVER_BG, ITEM_RADIUS_DP));
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                view.setTextColor(color);
                view.setBackground(null);
            }
            return false;
        });
        return view;
    }

    private static void addDivider(Context context, LinearLayout panel) {
        View divider = new View(context);
        divider.setBackground(rect(COLOR_DIVIDER, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(0, dp(6), 0, dp(6));
        panel.addView(divider, lp);
    }

    private static void close(InteractionContext context, FrameLayout overlay) {
        if (overlay.getParent() instanceof ViewGroup parent) {
            parent.removeView(overlay);
        }
        context.requestViewportFocus();
    }

    private static ShapeDrawable rect(int color, float radiusDp) {
        return rect(color, radiusDp, 0, 0);
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
}
