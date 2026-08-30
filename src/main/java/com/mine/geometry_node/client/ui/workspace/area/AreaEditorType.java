package com.mine.geometry_node.client.ui.workspace.area;

import com.mine.geometry_node.client.ui.components.common.SvgIconView;

public enum AreaEditorType {
    GRAPH_EDITOR("图编辑器", SvgIconView.Icon.GRAPH_EDITOR),
    ASSET_BROWSER("资产浏览器", SvgIconView.Icon.ASSET_LIBRARY),
    TERMINAL("终端", SvgIconView.Icon.TERMINAL),
    PERFORMANCE("性能监视器", SvgIconView.Icon.PERFORMANCE);

    private final String mDisplayName;
    private final SvgIconView.Icon mIcon;

    AreaEditorType(String displayName, SvgIconView.Icon icon) {
        mDisplayName = displayName;
        mIcon = icon;
    }

    public String displayName() {
        return mDisplayName;
    }

    public SvgIconView.Icon icon() {
        return mIcon;
    }
}
