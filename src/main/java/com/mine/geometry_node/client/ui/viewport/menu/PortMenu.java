package com.mine.geometry_node.client.ui.viewport.menu;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.interaction.InteractionContext;
import com.mine.geometry_node.client.ui.viewport.visual.NodeVisualAdapter;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

public class PortMenu {

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
        popupOverlay.setOnClickListener(v -> ((ViewGroup) popupOverlay.getParent()).removeView(popupOverlay));

        LinearLayout panel = new LinearLayout(uiContext);
        panel.setOrientation(LinearLayout.VERTICAL);
        ShapeDrawable bg = new ShapeDrawable();
        bg.setColor(0xFF2B2B2B);
        bg.setCornerRadius(UIUtils.dp2px(6));
        panel.setBackground(bg);
        panel.setPadding(UIUtils.dp2pxInt(10), UIUtils.dp2pxInt(10), UIUtils.dp2pxInt(10), UIUtils.dp2pxInt(10));
        panel.setOnClickListener(v -> {});

        TextView title = new TextView(uiContext);
        title.setText("重命名端口");
        title.setTextColor(0xFFDDDDDD);
        title.setTextSize(14);
        panel.addView(title);

        EditText input = new EditText(uiContext);
        input.setText(oldName);
        input.setTextColor(UIConstants.CLR_WHITE);
        ShapeDrawable inputBg = new ShapeDrawable();
        inputBg.setColor(0xFF1E1E1E);
        inputBg.setCornerRadius(UIUtils.dp2px(4));
        input.setBackground(inputBg);

        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(180), UIUtils.dp2pxInt(30));
        inputLp.topMargin = UIUtils.dp2pxInt(8);
        panel.addView(input, inputLp);

        Button confirmBtn = new Button(uiContext);
        confirmBtn.setText("确定 (Confirm)");
        ShapeDrawable btnBg = new ShapeDrawable();
        btnBg.setColor(0xFF4A90E2);
        btnBg.setCornerRadius(UIUtils.dp2px(4));
        confirmBtn.setBackground(btnBg);
        confirmBtn.setTextColor(UIConstants.CLR_WHITE);

        confirmBtn.setOnClickListener(v -> {
            String newName = input.getText().toString().trim();
            if (!newName.equals(oldName)) {
                // 架构优化：直接抛出意图，断开对 CmdRenamePort 的直接依赖
                context.requestRenamePort(node.getNodeData().id, finalCategory, portId, oldName, newName);
            }
            ((ViewGroup) popupOverlay.getParent()).removeView(popupOverlay);
        });

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30));
        btnLp.topMargin = UIUtils.dp2pxInt(12);
        panel.addView(confirmBtn, btnLp);

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        panelLp.leftMargin = (int) screenX;
        panelLp.topMargin = (int) screenY;
        popupOverlay.addView(panel, panelLp);

        if (context instanceof ViewGroup vg) {
            vg.addView(popupOverlay);
            input.requestFocus();
            input.setSelection(0, oldName.length());
        }
    }
}
