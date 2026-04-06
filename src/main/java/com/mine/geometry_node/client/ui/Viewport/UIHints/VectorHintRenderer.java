package com.mine.geometry_node.client.ui.Viewport.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

public class VectorHintRenderer implements UIHintRenderer {

    // 【新增】：声明 Vector 控件需要额外 3 行的高度
    @Override
    public float getRequiredExtraRows(PortRow row) {
        return 3.0f;
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        // (保持原有代码不变)
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        String portId = row.leftPort().id();
        Object rawVal = nodeData.inputs.containsKey(portId) ? nodeData.inputs.get(portId) : row.leftPort().defaultValue();

        List<Number> vec = (rawVal instanceof List) ? (List<Number>) rawVal : List.of(0f, 0f, 0f);

        for (int i = 0; i < 3; i++) {
            final int index = i;
            EditText et = new EditText(context);
            et.setText(String.valueOf(vec.get(i).floatValue()));
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
                    try {
                        float parsedFloat = Float.parseFloat(et.getText().toString());
                        Object currentRaw = nodeData.inputs.containsKey(portId) ? nodeData.inputs.get(portId) : row.leftPort().defaultValue();
                        List<Number> currentVec = (currentRaw instanceof List) ? (List<Number>) currentRaw : List.of(0f, 0f, 0f);

                        if (currentVec.get(index).floatValue() != parsedFloat) {
                            List<Float> newList = new ArrayList<>();
                            for (int j = 0; j < 3; j++) {
                                newList.add(j == index ? parsedFloat : currentVec.get(j).floatValue());
                            }
                            CmdChangeInputValue cmd = new CmdChangeInputValue(
                                    editorContext.getGraphController(), nodeData.id, portId, currentRaw, newList);
                            editorContext.getCommandManager().execute(cmd);
                        }
                    } catch (NumberFormatException e) {
                        Object fallbackRaw = nodeData.inputs.containsKey(portId) ? nodeData.inputs.get(portId) : row.leftPort().defaultValue();
                        List<Number> fallbackVec = (fallbackRaw instanceof List) ? (List<Number>) fallbackRaw : List.of(0f, 0f, 0f);
                        et.setText(String.valueOf(fallbackVec.get(index).floatValue()));
                    }
                }
            });
        }
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
        lp.height = (UIConstants.Node.ROW_HEIGHT) * 3;
        lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;
        lp.leftMargin = (int) startX;
        lp.topMargin = (int) currentY + (int) topOffset;
        view.setLayoutParams(lp);
    }
}