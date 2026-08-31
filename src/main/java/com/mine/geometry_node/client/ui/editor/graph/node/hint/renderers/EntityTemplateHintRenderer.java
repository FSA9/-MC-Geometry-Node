package com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

public final class EntityTemplateHintRenderer implements UIHintRenderer {
    private static final float PREVIEW_SIZE_DP = UIConstants.Node.ROW_HEIGHT * 6.0f;
    private static final float CONTENT_HEIGHT_DP = TemplateEditorHintLayout.contentHeightDp(PREVIEW_SIZE_DP);
    private static final float EXTRA_ROWS = CONTENT_HEIGHT_DP / UIConstants.Node.ROW_HEIGHT;

    @Override
    public float getRequiredExtraRows(PortRow row) {
        return EXTRA_ROWS;
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        if (row.leftPort() == null) return null;
        UIEntityTemplatePreview preview = new UIEntityTemplatePreview(
                context,
                nodeData,
                row.leftPort().id(),
                editorContext,
                UIEntityTemplatePreview.RotationMode.HORIZONTAL
        );
        return new TemplateEditorHintLayout(
                context,
                preview,
                PREVIEW_SIZE_DP,
                true,
                preview::openTemplateEditor
        );
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        float topOffset = row.leftPort() != null || row.rightPort() != null ? UIConstants.Node.ROW_HEIGHT : 0.0f;
        float startX = UIConstants.Node.LABEL_MARGIN_PORT;
        float endX = nodeWidth - UIConstants.Node.LABEL_MARGIN_PORT;

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                UIUtils.dp2pxInt(endX - startX),
                UIUtils.dp2pxInt(CONTENT_HEIGHT_DP)
        );
        params.gravity = Gravity.LEFT | Gravity.TOP;
        params.leftMargin = UIUtils.dp2pxInt(startX);
        params.topMargin = UIUtils.dp2pxInt(currentY + topOffset);
        view.setLayoutParams(params);
    }
}
