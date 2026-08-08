package com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.editor.asset.dialog.FilePickerDialog;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.InlineActionButton;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintValueBinder;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;

public class PathHintRenderer implements UIHintRenderer {
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
                new LinearLayout.LayoutParams(InlineActionButton.widthPx(), InlineActionButton.heightPx()));
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
        InlineActionButton button = new InlineActionButton(context, "...");

        button.setOnClickListener(v -> {
            FilePickerDialog.showPath(button, input.getText().toString(), selectedPath -> {
                input.setText(selectedPath);
                UIHintValueBinder.commit(editorContext, nodeData, portId, selectedPath);
            });
        });
        return button;
    }
}
