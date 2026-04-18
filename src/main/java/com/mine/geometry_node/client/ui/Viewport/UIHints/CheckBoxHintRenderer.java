package com.mine.geometry_node.client.ui.Viewport.UIHints;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeProperty;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.CheckBox;
import icyllis.modernui.widget.FrameLayout.LayoutParams;

public class CheckBoxHintRenderer implements UIHintRenderer {
    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        String propKey = row.hintParams() != null ? (String) row.hintParams().get("properties") : null;
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
                        editorContext.getCommandManager().execute(new CmdChangeProperty(editorContext.getGraphController(), nodeData.id, propKey, oldVal, isChecked));
                    }
                } else if (row.leftPort() != null) {
                    String portId = row.leftPort().id();
                    Object oldVal = nodeData.inputs.get(portId);
                    if (!Boolean.valueOf(isChecked).equals(oldVal)) {
                        editorContext.getCommandManager().execute(new CmdChangeInputValue(editorContext.getGraphController(), nodeData.id, portId, oldVal, isChecked));
                    }
                }
            } else {
                if (propKey != null) nodeData.properties.put(propKey, isChecked);
                else if (row.leftPort() != null) nodeData.inputs.put(row.leftPort().id(), isChecked);
            }
        });
        return cb;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        view.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int cbHeightPx = view.getMeasuredHeight() > 0 ? view.getMeasuredHeight() : UIUtils.dp2pxInt(16);
        float cbHeightDp = UIUtils.px2dp(cbHeightPx);

        LayoutParams lp = (LayoutParams) view.getLayoutParams();
        lp.width = LayoutParams.WRAP_CONTENT;
        lp.height = LayoutParams.WRAP_CONTENT;
        lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;

        lp.leftMargin = UIUtils.dp2pxInt(UIConstants.Node.LABEL_MARGIN_PORT - 5);
        lp.topMargin = UIUtils.dp2pxInt(currentY + Math.max(0, (UIConstants.Node.ROW_HEIGHT - cbHeightDp) / 2));
        view.setLayoutParams(lp);
    }
}