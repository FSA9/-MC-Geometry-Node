package com.mine.geometry_node.client.ui.viewport.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils; // 引入工具类
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;

import com.mine.geometry_node.core.node.port.PortType;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

public class VectorHintRenderer implements UIHintRenderer {

    @Override
    public float getRequiredExtraRows(PortRow row) {
        return 3.0f;
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        String portId = row.leftPort().id();
        Object rawVal = nodeData.inputs.containsKey(portId) ? nodeData.inputs.get(portId) : row.leftPort().defaultValue();

        for (int i = 0; i < 3; i++) {
            final int index = i;
            EditText et = new EditText(context);

            float currentVal = UIHintUtils.getSafeVectorComponent(rawVal, index);
            et.setText(String.valueOf(currentVal));

            UIHintUtils.applyStandardInputStyle(et, PortType.FLOAT);
            container.addView(et, UIHintUtils.getStandardInputLayoutParams());

            et.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && editorContext != null) {
                    try {
                        float parsedFloat = Float.parseFloat(et.getText().toString());
                        Object currentRaw = nodeData.inputs.containsKey(portId) ? nodeData.inputs.get(portId) : row.leftPort().defaultValue();
                        float oldComponent = UIHintUtils.getSafeVectorComponent(currentRaw, index);

                        if (oldComponent != parsedFloat) {
                            List<Float> newList = new ArrayList<>();
                            for (int j = 0; j < 3; j++) {
                                newList.add(j == index ? parsedFloat : UIHintUtils.getSafeVectorComponent(currentRaw, j));
                            }
                            CmdChangeInputValue cmd = new CmdChangeInputValue(
                                    editorContext.getGraphController(), nodeData.id, portId, currentRaw, newList);
                            editorContext.getCommandManager().execute(cmd);
                        }
                    } catch (NumberFormatException e) {
                        Object fallbackRaw = nodeData.inputs.containsKey(portId) ? nodeData.inputs.get(portId) : row.leftPort().defaultValue();
                        et.setText(String.valueOf(UIHintUtils.getSafeVectorComponent(fallbackRaw, index)));
                    }
                }
            });
        }
        return container;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        float startX = UIConstants.Node.LABEL_MARGIN_PORT;
        float endX = nodeWidth - UIConstants.Node.LABEL_MARGIN_PORT;

        boolean hasLabel = row.leftPort() != null || row.rightPort() != null;
        float topOffset = hasLabel ? UIConstants.Node.ROW_HEIGHT : 0;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();

        int targetWidth = UIUtils.dp2pxInt(endX - startX);
        int targetHeight = FrameLayout.LayoutParams.WRAP_CONTENT;

        if (lp == null) {
            lp = new FrameLayout.LayoutParams(targetWidth, targetHeight);
        } else {
            lp.width = targetWidth;
            lp.height = targetHeight;
        }

        lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;
        lp.leftMargin = UIUtils.dp2pxInt(startX);

        // 外部容器直接顶着当前行的起始点即可，因为 LinearLayout 内部的子项已经自带了居中的上下 Margin
        lp.topMargin = UIUtils.dp2pxInt(currentY + topOffset);

        view.setLayoutParams(lp);
    }
}