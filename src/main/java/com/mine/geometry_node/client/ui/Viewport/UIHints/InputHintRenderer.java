package com.mine.geometry_node.client.ui.Viewport.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeProperty;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;

public class InputHintRenderer implements UIHintRenderer {

    // 【新增】：声明 Input 控件需要额外 1 行的高度
    @Override
    public float getRequiredExtraRows(PortRow row) {
        return 1.0f;
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        // (保持原有代码完全不变)
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

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        EditText et = new EditText(context);
        et.setText(val != null ? val.toString() : "");
        et.setTextColor(UIConstants.CLR_GRAY_LABEL);
        et.setTextSize(UIConstants.Node.TEXT_SIZE_LABEL);

        et.setGravity(icyllis.modernui.view.Gravity.RIGHT | icyllis.modernui.view.Gravity.CENTER_VERTICAL);
        et.setBackground(new ColorDrawable(0xFF252525));
        et.setPadding(12, 0, 12, 0);

        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UIConstants.Node.ROW_HEIGHT - 4
        );
        etParams.bottomMargin = 4;
        container.addView(et, etParams);

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
        return container;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        // (保持原有代码不变)
        float startX = UIConstants.Node.LABEL_MARGIN_PORT;
        float endX = nodeWidth - UIConstants.Node.LABEL_MARGIN_PORT;

        boolean hasLabel = row.leftPort() != null || row.rightPort() != null;
        float topOffset = hasLabel ? UIConstants.Node.ROW_HEIGHT : 0;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        lp.width = (int) (endX - startX);
        lp.height = UIConstants.Node.ROW_HEIGHT;
        lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;
        lp.leftMargin = (int) startX;
        lp.topMargin = (int) currentY + (int) topOffset;
        view.setLayoutParams(lp);
    }
}