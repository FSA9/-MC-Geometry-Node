package com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.functions.color.ColorRamp;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

public class CustomHintRenderer implements UIHintRenderer {
    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        if (row == null || !ColorRamp.GRADIENT_INPUT.equals(row.customWidgetId())) {
            return null;
        }
        return new ColorRampView(context, nodeData, editorContext);
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        float startX = UIConstants.Node.LABEL_MARGIN_PORT;
        float endX = nodeWidth - UIConstants.Node.LABEL_MARGIN_PORT;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        int widthPx = UIUtils.dp2pxInt(endX - startX);
        int heightPx = UIUtils.dp2pxInt(ColorRampView.HEIGHT_DP);

        if (lp == null) {
            lp = new FrameLayout.LayoutParams(widthPx, heightPx);
        } else {
            lp.width = widthPx;
            lp.height = heightPx;
        }

        lp.gravity = Gravity.LEFT | Gravity.TOP;
        lp.leftMargin = UIUtils.dp2pxInt(startX);
        lp.topMargin = UIUtils.dp2pxInt(currentY);

        view.setLayoutParams(lp);
    }

    @Override
    public float getRequiredExtraRows(PortRow row) {
        if (row != null && ColorRamp.GRADIENT_INPUT.equals(row.customWidgetId())) {
            return Math.max(1.0f, ColorRampView.HEIGHT_DP / (float) UIConstants.Node.ROW_HEIGHT);
        }
        return UIHintRenderer.super.getRequiredExtraRows(row);
    }
}
