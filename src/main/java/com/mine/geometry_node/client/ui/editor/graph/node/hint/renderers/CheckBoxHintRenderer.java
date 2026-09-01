package com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.components.common.UiCheckBox;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintUtils;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintValueBinder;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.definition.port.PortRow;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

public class CheckBoxHintRenderer implements UIHintRenderer {
    @Override
    public float getRequiredExtraRows(PortRow row) {
        return 0.0f; // CheckBox 通常与 Label 同行，不需要额外行
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        String portId = row.leftPort().id();
        Object val = UIHintValueBinder.getValue(nodeData, row.leftPort());

        boolean initialCheck = String.valueOf(val).equalsIgnoreCase("true");

        UiCheckBox cb = new UiCheckBox(context);
        cb.setChecked(initialCheck);
        cb.setOnCheckedChangeListener((checkBox, isChecked) ->
                UIHintValueBinder.commit(editorContext, nodeData, portId, isChecked));
        return cb;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        int cbSizePx = UIUtils.dp2pxInt(UIHintUtils.getStandardInputHeight());

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cbSizePx, cbSizePx);
        lp.gravity = Gravity.LEFT | Gravity.TOP;
        lp.leftMargin = UIUtils.dp2pxInt(UIConstants.Node.LABEL_MARGIN_PORT);

        float verticalMargin = (UIConstants.Node.ROW_HEIGHT - UIHintUtils.getStandardInputHeight()) / 2.0f;
        lp.topMargin = UIUtils.dp2pxInt(currentY + verticalMargin);

        view.setLayoutParams(lp);
    }
}
