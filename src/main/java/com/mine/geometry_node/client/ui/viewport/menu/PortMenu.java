package com.mine.geometry_node.client.ui.viewport.menu;

import com.mine.geometry_node.client.ui.UICommand.commands.CmdRenamePort;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.UINode;
import com.mine.geometry_node.client.ui.viewport.interaction.InteractionContext;
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

    /**
     * 呼出端口重命名面板
     */
    public static void show(InteractionContext context, UINode node, String portId, float screenX, float screenY) {
        // 1. 确定端口的 category 和默认名称
        String category = "inputs";
        boolean found = false;
        String defaultName = "";

        for (PortRow row : node.getNodeDef().rows()) {
            if (row.leftPort() != null && row.leftPort().id().equals(portId)) {
                category = row.leftPort().type() == PortType.EXECUTION ? "execution" : "inputs";
                defaultName = row.leftPort().displayName().getString();
                found = true;
                break;
            }
            if (row.rightPort() != null && row.rightPort().id().equals(portId)) {
                category = row.rightPort().type() == PortType.EXECUTION ? "execution" : "outputs";
                defaultName = row.rightPort().displayName().getString();
                found = true;
                break;
            }
        }
        if (!found) return;

        // 获取当前的有效名称
        String oldName = node.getNodeData().getEffectivePortName(category, portId, defaultName);
        final String finalCategory = category;

        // 2. 构建全屏遮罩，点击背景关闭
        Context uiContext = context.getUIContext();
        FrameLayout popupOverlay = new FrameLayout(uiContext);
        popupOverlay.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        popupOverlay.setOnClickListener(v -> ((ViewGroup) popupOverlay.getParent()).removeView(popupOverlay));

        // 3. 构建面板实体
        LinearLayout panel = new LinearLayout(uiContext);
        panel.setOrientation(LinearLayout.VERTICAL);
        ShapeDrawable bg = new ShapeDrawable();
        bg.setColor(0xFF2B2B2B); // 深灰底色
        bg.setCornerRadius(UIUtils.dp2px(6));
        panel.setBackground(bg);
        panel.setPadding(UIUtils.dp2pxInt(10), UIUtils.dp2pxInt(10), UIUtils.dp2pxInt(10), UIUtils.dp2pxInt(10));
        panel.setOnClickListener(v -> {}); // 阻断点击事件，防止点面板也关闭

        // 标题
        TextView title = new TextView(uiContext);
        title.setText("重命名端口");
        title.setTextColor(0xFFDDDDDD);
        title.setTextSize(14);
        panel.addView(title);

        // 输入框
        EditText input = new EditText(uiContext);
        input.setText(oldName);
        input.setTextColor(UIConstants.CLR_WHITE);
        ShapeDrawable inputBg = new ShapeDrawable();
        inputBg.setColor(0xFF1E1E1E);
        inputBg.setCornerRadius(UIUtils.dp2px(4));
        input.setBackground(inputBg);

        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                UIUtils.dp2pxInt(180), UIUtils.dp2pxInt(30)
        );
        inputLp.topMargin = UIUtils.dp2pxInt(8);
        panel.addView(input, inputLp);

        // 确定按钮
        Button confirmBtn = new Button(uiContext);
        confirmBtn.setText("确定 (Confirm)");
        ShapeDrawable btnBg = new ShapeDrawable();
        btnBg.setColor(0xFF4A90E2); // 蓝色
        btnBg.setCornerRadius(UIUtils.dp2px(4));
        confirmBtn.setBackground(btnBg);
        confirmBtn.setTextColor(UIConstants.CLR_WHITE);

        confirmBtn.setOnClickListener(v -> {
            String newName = input.getText().toString().trim();
            if (!newName.equals(oldName)) {
                CmdRenamePort cmd = new CmdRenamePort(
                        context.getEditorContext().getGraphController(),
                        node.getNodeData().id, finalCategory, portId, oldName, newName
                );
                context.getEditorContext().getCommandManager().execute(cmd);
            }
            ((ViewGroup) popupOverlay.getParent()).removeView(popupOverlay);
        });

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30)
        );
        btnLp.topMargin = UIUtils.dp2pxInt(12);
        panel.addView(confirmBtn, btnLp);

        // 4. 定位并添加到视图
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
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