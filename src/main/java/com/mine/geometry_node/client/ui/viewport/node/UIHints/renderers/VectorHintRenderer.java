package com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.NumericInputSpec;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintUtils;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;

import com.mine.geometry_node.core.node.port.PortType;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;

public class VectorHintRenderer implements UIHintRenderer {

    @Override
    public float getRequiredExtraRows(PortRow row) {
        return 3.0f;
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        NumericInputSpec componentSpec = NumericInputSpec.from(row, PortType.FLOAT);

        for (int i = 0; i < 3; i++) {
            container.addView(
                    NumericInputView.vectorComponent(context, nodeData, row.leftPort(), i, componentSpec, editorContext),
                    UIHintUtils.getStandardInputLayoutParams()
            );
        }
        return container;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        float startX = UIConstants.Node.LABEL_MARGIN_PORT;
        float endX = nodeWidth - UIConstants.Node.LABEL_MARGIN_PORT;

        boolean hasLabel = row.leftPort() != null || row.rightPort() != null;
        float topOffset = hasLabel ? UIConstants.Node.ROW_HEIGHT : 0;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();

        int targetWidth = UIUtils.dp2pxInt(endX - startX);
        int targetHeight = FrameLayout.LayoutParams.WRAP_CONTENT;

        if (lp == null) {
            lp = new FrameLayout.LayoutParams(targetWidth, targetHeight);
        } else {
            lp.width = targetWidth;
            lp.height = targetHeight;
        }

        lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;
        lp.leftMargin = UIUtils.dp2pxInt(startX);

        // 外部容器直接顶着当前行的起始点即可，因为 LinearLayout 内部的子项已经自带了居中的上下 Margin
        lp.topMargin = UIUtils.dp2pxInt(currentY + topOffset);

        view.setLayoutParams(lp);
    }
}
