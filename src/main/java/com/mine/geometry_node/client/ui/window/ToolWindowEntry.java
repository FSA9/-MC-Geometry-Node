package com.mine.geometry_node.client.ui.window;

import com.mine.geometry_node.client.ui.common.VectorIconView;

public interface ToolWindowEntry {
    String getDisplayName();

    VectorIconView.Kind getIconKind();
}
