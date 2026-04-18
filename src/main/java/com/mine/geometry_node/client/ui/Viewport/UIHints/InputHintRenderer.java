package com.mine.geometry_node.client.ui.Viewport.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeProperty;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;

public class InputHintRenderer implements UIHintRenderer {

    @Override
    public float getRequiredExtraRows(PortRow row) {
        return 1.0f;
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        String propKey = row.hintParams() != null ? (String) row.hintParams().get("properties") : null;
        Object val = null;
        PortType expectedType = PortType.ANY;

        if (propKey != null) {
            val = nodeData.properties.get(propKey);
        } else if (row.leftPort() != null) {
            val = nodeData.inputs.containsKey(row.leftPort().id()) ? nodeData.inputs.get(row.leftPort().id()) : row.leftPort().defaultValue();
            expectedType = row.leftPort().type();
        }

        final Object finalOldVal = val;
        final PortType finalExpectedType = expectedType;

        // 【优化】直接使用 EditText，不使用 LinearLayout 包裹，避免 Margin 冲突
        EditText et = new EditText(context);
        et.setText(val != null ? val.toString() : "");

        UIHintUtils.applyStandardInputStyle(et, finalExpectedType);

        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && editorContext != null) {
                String currentText = et.getText().toString();
                Object parsedValue = currentText;

                try {
                    if (finalExpectedType == PortType.INTEGER) {
                        parsedValue = Integer.parseInt(currentText);
                    } else if (finalExpectedType == PortType.FLOAT) {
                        parsedValue = Float.parseFloat(currentText);
                    }
                } catch (NumberFormatException e) {
                    et.setText(finalOldVal != null ? finalOldVal.toString() : "");
                    return;
                }

                if (propKey != null) {
                    Object oldVal = nodeData.properties.get(propKey);
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
        float startX = UIConstants.Node.LABEL_MARGIN_PORT;
        float endX = nodeWidth - UIConstants.Node.LABEL_MARGIN_PORT;
        boolean hasLabel = row.leftPort() != null || row.rightPort() != null;
        float topOffset = hasLabel ? UIConstants.Node.ROW_HEIGHT : 0;

        // 【居中核心算法】输入框高度比行高小 4，剩余空间均分给上下 Margin
        float inputBoxHeight = UIConstants.Node.ROW_HEIGHT - 4.0f;
        float verticalMargin = (UIConstants.Node.ROW_HEIGHT - inputBoxHeight) / 2.0f;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(UIUtils.dp2pxInt(endX - startX), UIUtils.dp2pxInt(inputBoxHeight));
        } else {
            lp.width = UIUtils.dp2pxInt(endX - startX);
            lp.height = UIUtils.dp2pxInt(inputBoxHeight);
        }

        lp.gravity = Gravity.LEFT | Gravity.TOP;
        lp.leftMargin = UIUtils.dp2pxInt(startX);

        // 绝对完美的 Y 轴对齐：当前行 Y 坐标起点 + 行偏移 + 居中留白
        lp.topMargin = UIUtils.dp2pxInt(currentY + topOffset + verticalMargin);

        view.setLayoutParams(lp);
    }
}