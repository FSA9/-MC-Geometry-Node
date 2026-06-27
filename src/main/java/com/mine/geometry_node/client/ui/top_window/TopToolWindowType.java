package com.mine.geometry_node.client.ui.top_window;

import com.mine.geometry_node.client.ui.common.VectorIconView;
import com.mine.geometry_node.client.ui.window.ToolWindowEntry;

public enum TopToolWindowType implements ToolWindowEntry {
    GRAPH_EDITOR("图编辑器", VectorIconView.Kind.NODE_GRAPH);

    private final String displayName;
    private final VectorIconView.Kind iconKind;

    TopToolWindowType(String displayName, VectorIconView.Kind iconKind) {
        this.displayName = displayName;
        this.iconKind = iconKind;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public VectorIconView.Kind getIconKind() {
        return iconKind;
    }
}
