package com.mine.geometry_node.client.ui.Viewport.UIHints;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeProperty;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.nodes.PortRow;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.CheckBox;
import icyllis.modernui.widget.FrameLayout.LayoutParams;

public class CheckBoxHintRenderer implements UIHintRenderer {
    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        String propKey = row.hintParams() != null ? (String) row.hintParams().get("property_key") : null;
        Object val = null;
        if (propKey != null) {
            val = nodeData.properties.get(propKey);
        } else if (row.leftPort() != null) {
            val = nodeData.inputs.containsKey(row.leftPort().id()) ? nodeData.inputs.get(row.leftPort().id()) : row.leftPort().defaultValue();
        }

        CheckBox cb = new CheckBox(context);
        cb.setChecked(String.valueOf(val).equalsIgnoreCase("true"));
        cb.setBackground(null);
        cb.setMinimumWidth(0);
        cb.setMinimumHeight(0);
        cb.setPadding(0, 0, 0, 0);

        cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (editorContext != null) {
                if (propKey != null) {
                    Object oldVal = nodeData.properties.get(propKey);
                    if (!Boolean.valueOf(isChecked).equals(oldVal)) {
                        CmdChangeProperty cmd = new CmdChangeProperty(editorContext.getGraphController(), nodeData.id, propKey, oldVal, isChecked);
                        editorContext.getCommandManager().execute(cmd);
                    }
                } else if (row.leftPort() != null) {
                    String portId = row.leftPort().id();
                    Object oldVal = nodeData.inputs.get(portId);
                    if (!Boolean.valueOf(isChecked).equals(oldVal)) {
                        CmdChangeInputValue cmd = new CmdChangeInputValue(editorContext.getGraphController(), nodeData.id, portId, oldVal, isChecked);
                        editorContext.getCommandManager().execute(cmd);
                    }
                }
            } else { // 兜底逻辑
                if (propKey != null) nodeData.properties.put(propKey, isChecked);
                else if (row.leftPort() != null) nodeData.inputs.put(row.leftPort().id(), isChecked);
            }
        });
        return cb;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        // 提前测量一下控件获取真实宽高，为垂直居中做准备
        view.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );
        int cbHeight = view.getMeasuredHeight() > 0 ? view.getMeasuredHeight() : 16;

        LayoutParams lp = (LayoutParams) view.getLayoutParams();
        lp.width = LayoutParams.WRAP_CONTENT;
        lp.height = LayoutParams.WRAP_CONTENT;
        lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;

        // 1. 勾选框的左 padding 设为 x
        lp.leftMargin = UIConstants.Node.LABEL_MARGIN_PORT - 5;
        // 2. 垂直居中偏移 (行高减去控件高度除以2)
        lp.topMargin = (int) currentY + Math.max(0, (UIConstants.Node.ROW_HEIGHT - cbHeight) / 2);
        view.setLayoutParams(lp);
    }
}
