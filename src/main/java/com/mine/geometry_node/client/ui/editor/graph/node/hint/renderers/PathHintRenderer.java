package com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers;


import com.mine.geometry_node.client.feature.graph.edit.EditorContext;
import com.mine.geometry_node.client.ui.components.integration.asset.dialog.FilePickerDialog;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.InlineActionButton;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintUtils;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintValueBinder;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
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

        EditText input = UIHintUtils.createInput(context);
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
        UIHintUtils.layoutExtraRowInput(view, row, currentY, nodeWidth);
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
