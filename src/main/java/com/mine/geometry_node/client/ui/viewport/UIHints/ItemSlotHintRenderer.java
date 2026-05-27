package com.mine.geometry_node.client.ui.viewport.UIHints;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

public class ItemSlotHintRenderer implements UIHintRenderer {
    private static final float SLOT_SIZE_DP = 28.0f;
    private static final float EXTRA_ROWS = 2.0f;

    @Override
    public float getRequiredExtraRows(PortRow row) {
        return EXTRA_ROWS;
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        String portId = row.leftPort().id();

        UIItemSlot slotView = new UIItemSlot(context, nodeData, portId, editorContext);
        return slotView;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        int slotSizePx = UIUtils.dp2pxInt(SLOT_SIZE_DP);
        boolean hasLabel = row.leftPort() != null || row.rightPort() != null;
        float topOffset = hasLabel ? UIConstants.Node.ROW_HEIGHT : 0.0f;
        float hintAreaHeight = UIConstants.Node.ROW_HEIGHT * EXTRA_ROWS;
        float topGap = Math.max(0.0f, (hintAreaHeight - SLOT_SIZE_DP) / 2.0f);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(slotSizePx, slotSizePx);
        lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        lp.topMargin = UIUtils.dp2pxInt(currentY + topOffset + topGap);

        view.setLayoutParams(lp);
    }
}
