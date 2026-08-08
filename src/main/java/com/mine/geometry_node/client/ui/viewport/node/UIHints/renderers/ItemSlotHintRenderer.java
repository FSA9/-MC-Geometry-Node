package com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

public class ItemSlotHintRenderer implements UIHintRenderer {
    private static final float SLOT_SIZE_DP = UIConstants.Node.ROW_HEIGHT * 2.0f;
    private static final float CONTENT_HEIGHT_DP = TemplateEditorHintLayout.contentHeightDp(SLOT_SIZE_DP);
    private static final float EXTRA_ROWS = CONTENT_HEIGHT_DP / UIConstants.Node.ROW_HEIGHT;

    @Override
    public float getRequiredExtraRows(PortRow row) {
        return EXTRA_ROWS;
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        String portId = row.leftPort().id();

        UIItemSlot slotView = new UIItemSlot(context, nodeData, portId, editorContext);
        slotView.setOpenEditorOnClick(false);
        return new TemplateEditorHintLayout(
                context,
                slotView,
                SLOT_SIZE_DP,
                false,
                slotView::openTemplateEditor
        );
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        boolean hasLabel = row.leftPort() != null || row.rightPort() != null;
        float topOffset = hasLabel ? UIConstants.Node.ROW_HEIGHT : 0.0f;
        float startX = UIConstants.Node.LABEL_MARGIN_PORT;
        float endX = nodeWidth - UIConstants.Node.LABEL_MARGIN_PORT;

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                UIUtils.dp2pxInt(endX - startX),
                UIUtils.dp2pxInt(CONTENT_HEIGHT_DP)
        );
        lp.gravity = Gravity.LEFT | Gravity.TOP;
        lp.leftMargin = UIUtils.dp2pxInt(startX);
        lp.topMargin = UIUtils.dp2pxInt(currentY + topOffset);

        view.setLayoutParams(lp);
    }
}
