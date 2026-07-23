package com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog.FilePickerDialog;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintValueBinder;
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

public class PathHintRenderer implements UIHintRenderer {
    private static final float PICK_BUTTON_WIDTH = 22.0f;

    @Override
    public float getRequiredExtraRows(PortRow row) {
        return 1.0f;
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        String portId = row.leftPort().id();
        Object val = UIHintValueBinder.getValue(nodeData, row.leftPort());

        EditText input = new EditText(context);
        input.setText(val != null ? val.toString() : "");
        UIHintUtils.applyStandardInputStyle(input, PortType.PATH);

        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && editorContext != null) {
                UIHintValueBinder.commit(editorContext, nodeData, portId, input.getText().toString());
            }
        });

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setGravity(Gravity.CENTER_VERTICAL);
        wrapper.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));
        wrapper.addView(createPickButton(context, input, nodeData, portId, editorContext),
                new LinearLayout.LayoutParams(UIUtils.dp2pxInt(PICK_BUTTON_WIDTH), LinearLayout.LayoutParams.MATCH_PARENT));
        return wrapper;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        float startX = UIConstants.Node.LABEL_MARGIN_PORT;
        float endX = nodeWidth - UIConstants.Node.LABEL_MARGIN_PORT;
        float inputBoxHeight = UIHintUtils.getStandardInputHeight();
        boolean hasLabel = row.leftPort() != null || row.rightPort() != null;
        float topOffset = hasLabel ? UIConstants.Node.ROW_HEIGHT : 0.0f;
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

    private View createPickButton(Context context, EditText input, NodeData nodeData, String portId, EditorContext editorContext) {
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

        button.setOnClickListener(v -> {
            FilePickerDialog.showPath(button, input.getText().toString(), selectedPath -> {
                input.setText(selectedPath);
                UIHintValueBinder.commit(editorContext, nodeData, portId, selectedPath);
            });
        });
        return button;
    }
}
