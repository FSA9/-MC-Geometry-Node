package com.mine.geometry_node.client.ui.viewport.node.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.util.Objects;

public class InputHintRenderer implements UIHintRenderer {
    private static final float EXPAND_BUTTON_WIDTH = 16.0f;

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
                Object parsedValue = parseValue(et.getText().toString(), expectedType);
                if (parsedValue == null && (expectedType == PortType.INTEGER || expectedType == PortType.FLOAT)) {
                    et.setText(finalOldVal != null ? finalOldVal.toString() : "");
                    return;
                }

                Object oldVal = nodeData.inputs.get(portId);
                if (!Objects.equals(parsedValue, oldVal)) {
                    CmdChangeInputValue cmd = new CmdChangeInputValue(editorContext.getGraphController(), nodeData.id, portId, oldVal, parsedValue);
                    editorContext.getCommandManager().execute(cmd);
                }
            }
        });

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setGravity(Gravity.CENTER_VERTICAL);

        wrapper.addView(et, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));
        wrapper.addView(createExpandButton(context, et, nodeData, portId, expectedType, editorContext),
                new LinearLayout.LayoutParams(UIUtils.dp2pxInt(EXPAND_BUTTON_WIDTH), LinearLayout.LayoutParams.MATCH_PARENT));
        return wrapper;
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

    private View createExpandButton(Context context, EditText input, NodeData nodeData, String portId, PortType expectedType,
                                    EditorContext editorContext) {
        TextView button = new TextView(context);
        button.setText("...");
        button.setGravity(Gravity.CENTER);
        button.setTextColor(0xFFBFC7D5);
        button.setTextSize(UIConstants.Node.TEXT_SIZE_LABEL);

        ShapeDrawable bg = new ShapeDrawable();
        bg.setColor(0xFF30343B);
        bg.setCornerRadius(UIUtils.dp2px(2.0f));
        bg.setStroke(UIUtils.dp2pxInt(1), 0xFF424956);
        button.setBackground(bg);

        button.setOnClickListener(v -> ExpandedTextInputOverlay.show(
                context,
                button,
                editorContext,
                nodeData,
                portId,
                expectedType,
                input.getText().toString()
        ));
        return button;
    }

    private Object parseValue(String text, PortType expectedType) {
        try {
            if (expectedType == PortType.INTEGER) {
                return Integer.parseInt(text);
            }
            if (expectedType == PortType.FLOAT) {
                return Float.parseFloat(text);
            }
            return text;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
