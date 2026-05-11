package com.mine.geometry_node.client.ui.viewport.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
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
        String portId = row.leftPort().id();
        PortType expectedType = row.leftPort().type();
        Object val = nodeData.inputs.containsKey(portId) ? nodeData.inputs.get(portId) : row.leftPort().defaultValue();

        final Object finalOldVal = val;

        EditText et = new EditText(context);
        et.setText(val != null ? val.toString() : "");

        UIHintUtils.applyStandardInputStyle(et, expectedType);

        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && editorContext != null) {
                String currentText = et.getText().toString();
                Object parsedValue = currentText;

                try {
                    if (expectedType == PortType.INTEGER) parsedValue = Integer.parseInt(currentText);
                    else if (expectedType == PortType.FLOAT) parsedValue = Float.parseFloat(currentText);
                } catch (NumberFormatException e) {
                    et.setText(finalOldVal != null ? finalOldVal.toString() : "");
                    return;
                }

                Object oldVal = nodeData.inputs.get(portId);
                if (!parsedValue.equals(oldVal)) {
                    CmdChangeInputValue cmd = new CmdChangeInputValue(editorContext.getGraphController(), nodeData.id, portId, oldVal, parsedValue);
                    editorContext.getCommandManager().execute(cmd);
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

        // 直接调用工具类获取高度
        float inputBoxHeight = UIHintUtils.getStandardInputHeight();
        // 居中偏移也基于工具类的高度来算
        float verticalMargin = (UIConstants.Node.ROW_HEIGHT - inputBoxHeight) / 2.0f;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        int widthPx = UIUtils.dp2pxInt(endX - startX);
        int heightPx = UIUtils.dp2pxInt(inputBoxHeight);

        if (lp == null) {
            lp = new FrameLayout.LayoutParams(widthPx, heightPx);
        } else {
            lp.width = widthPx;
            lp.height = heightPx;
        }

        lp.gravity = Gravity.LEFT | Gravity.TOP;
        lp.leftMargin = UIUtils.dp2pxInt(startX);
        lp.topMargin = UIUtils.dp2pxInt(currentY + topOffset + verticalMargin);

        view.setLayoutParams(lp);
    }
}