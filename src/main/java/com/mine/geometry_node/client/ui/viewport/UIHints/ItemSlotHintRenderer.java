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

    @Override
    public float getRequiredExtraRows(PortRow row) {
        // 物品槽稍微大一点，需要占用 2 行的高度
        return 1.5f;
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        String portId = row.leftPort().id();

        // 创建我们自定义的物品槽控件（第三步实现）
        UIItemSlot slotView = new UIItemSlot(context, nodeData, portId, editorContext);
        return slotView;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        // 设定槽位在节点中的大小 (比如 28x28 dp)
        int slotSizePx = UIUtils.dp2pxInt(28);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(slotSizePx, slotSizePx);
        // 居中放置
        lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        lp.topMargin = UIUtils.dp2pxInt(currentY + 4);

        view.setLayoutParams(lp);
    }
}