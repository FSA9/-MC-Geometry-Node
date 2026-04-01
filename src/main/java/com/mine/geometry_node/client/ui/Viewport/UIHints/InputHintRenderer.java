package com.mine.geometry_node.client.ui.Viewport.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeProperty;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;

public class InputHintRenderer implements UIHintRenderer {
    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        String propKey = row.hintParams() != null ? (String) row.hintParams().get("property_key") : null;

        // 核心修复：优先读取已保存的值
        Object val = null;
        if (propKey != null) {
            val = nodeData.properties.get(propKey);
        } else if (row.leftPort() != null) {
            val = nodeData.inputs.containsKey(row.leftPort().id()) ? nodeData.inputs.get(row.leftPort().id()) : row.leftPort().defaultValue();
        }

        EditText et = new EditText(context);
        et.setText(val != null ? val.toString() : "");
        et.setTextColor(UIConstants.CLR_GRAY_LABEL);
        et.setTextSize(UIConstants.Node.TEXT_SIZE_LABEL);

        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && editorContext != null) {
                String currentText = et.getText().toString();
                if (propKey != null) {
                    Object oldVal = nodeData.properties.get(propKey);
                    if (!currentText.equals(oldVal)) {
                        CmdChangeProperty cmd = new CmdChangeProperty(editorContext.getGraphController(), nodeData.id, propKey, oldVal, currentText);
                        editorContext.getCommandManager().execute(cmd);
                    }
                } else if (row.leftPort() != null) {
                    String portId = row.leftPort().id();
                    Object oldVal = nodeData.inputs.get(portId);
                    if (!currentText.equals(oldVal)) {
                        CmdChangeInputValue cmd = new CmdChangeInputValue(editorContext.getGraphController(), nodeData.id, portId, oldVal, currentText);
                        editorContext.getCommandManager().execute(cmd);
                    }
                }
            }
        });
        return et;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        applyStandardHintLayout(view, row, currentY, nodeWidth);
    }

    private static void applyStandardHintLayout(View view, PortRow row, float currentY, int nodeWidth) {
        float startX = (row.leftPort() != null) ? (nodeWidth * 0.45f) : UIConstants.Node.LABEL_MARGIN_PORT;
        float endX = nodeWidth - ((row.rightPort() != null) ? UIConstants.Node.ROW_HEIGHT : UIConstants.Node.LABEL_MARGIN_PORT);

        float targetWidth = endX - startX;
        if (targetWidth < 10f) targetWidth = 10f;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        lp.width = (int) targetWidth;
        lp.height = UIConstants.Node.ROW_HEIGHT - 6;
        lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;
        lp.leftMargin = (int) startX;
        lp.topMargin = (int) currentY + 3;
        view.setLayoutParams(lp);
    }
}
