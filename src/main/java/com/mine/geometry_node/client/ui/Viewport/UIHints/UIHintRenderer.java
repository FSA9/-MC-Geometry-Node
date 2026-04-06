package com.mine.geometry_node.client.ui.Viewport.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;

public interface UIHintRenderer {
    View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext);
    void updateLayout(View view, PortRow row, float currentY, int nodeWidth);

    default float getRequiredExtraRows(PortRow row) {
        return 0.0f;
    }
}
