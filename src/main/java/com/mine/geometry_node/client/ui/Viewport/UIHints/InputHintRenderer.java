package com.mine.geometry_node.client.ui.Viewport.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeProperty;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;

public class InputHintRenderer implements UIHintRenderer {
    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        String propKey = row.hintParams() != null ? (String) row.hintParams().get("properties") : null;

        Object val = null;
        PortType expectedType = PortType.ANY; // 记录期待的类型

        if (propKey != null) {
            val = nodeData.properties.get(propKey);
            // 这里假设 property 也有类型约束逻辑，暂时简化
        } else if (row.leftPort() != null) {
            val = nodeData.inputs.containsKey(row.leftPort().id()) ? nodeData.inputs.get(row.leftPort().id()) : row.leftPort().defaultValue();
            expectedType = row.leftPort().type();
        }

        final Object finalOldVal = val;
        final PortType finalExpectedType = expectedType;

        EditText et = new EditText(context);
        et.setText(val != null ? val.toString() : "");
        et.setTextColor(UIConstants.CLR_GRAY_LABEL);
        et.setTextSize(UIConstants.Node.TEXT_SIZE_LABEL);

        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && editorContext != null) {
                String currentText = et.getText().toString();
                Object parsedValue = currentText; // 默认作为字符串

                // --- 核心校验逻辑 ---
                try {
                    if (finalExpectedType == PortType.INTEGER) {
                        parsedValue = Integer.parseInt(currentText);
                    } else if (finalExpectedType == PortType.FLOAT) {
                        parsedValue = Float.parseFloat(currentText);
                    }
                } catch (NumberFormatException e) {
                    // 校验失败！还原为旧值并放弃提交
                    et.setText(finalOldVal != null ? finalOldVal.toString() : "");
                    return;
                }

                // 比较与执行命令
                if (propKey != null) {
                    Object oldVal = nodeData.properties.get(propKey);
                    // 注意：数值比较最好用 equals 而不是直接比字符串
                    if (!parsedValue.equals(oldVal)) {
                        CmdChangeProperty cmd = new CmdChangeProperty(editorContext.getGraphController(), nodeData.id, propKey, oldVal, parsedValue);
                        editorContext.getCommandManager().execute(cmd);
                    }
                } else if (row.leftPort() != null) {
                    String portId = row.leftPort().id();
                    Object oldVal = nodeData.inputs.get(portId);
                    if (!parsedValue.equals(oldVal)) {
                        CmdChangeInputValue cmd = new CmdChangeInputValue(editorContext.getGraphController(), nodeData.id, portId, oldVal, parsedValue);
                        editorContext.getCommandManager().execute(cmd);
                    }
                }
            }
        });
        return et;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
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