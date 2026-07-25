package com.mine.geometry_node.client.ui.area;

import com.mine.geometry_node.client.ui.common.VectorIconView;

public enum AreaEditorType {
    GRAPH_EDITOR("图编辑器", VectorIconView.Kind.NODE_GRAPH),
    ASSET_BROWSER("资产浏览器", VectorIconView.Kind.FOLDER),
    TERMINAL("终端", VectorIconView.Kind.TERMINAL),
    PERFORMANCE("性能监视器", VectorIconView.Kind.CHART);

    private final String mDisplayName;
    private final VectorIconView.Kind mIconKind;

    AreaEditorType(String displayName, VectorIconView.Kind iconKind) {
        mDisplayName = displayName;
        mIconKind = iconKind;
    }

    public String displayName() {
        return mDisplayName;
    }

    public VectorIconView.Kind iconKind() {
        return mIconKind;
    }
}
