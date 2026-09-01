package com.mine.geometry_node.client.ui.workspace.area;

import com.mine.geometry_node.client.ui.components.common.SvgIconView;

public enum AreaEditorType {
    GRAPH_EDITOR(SvgIconView.Icon.GRAPH_EDITOR),
    ASSET_BROWSER(SvgIconView.Icon.ASSET_LIBRARY),
    DATA_LIBRARY(SvgIconView.Icon.DATA_LIBRARY),
    TERMINAL(SvgIconView.Icon.TERMINAL),
    PERFORMANCE(SvgIconView.Icon.PERFORMANCE);

    private final SvgIconView.Icon mIcon;

    AreaEditorType(SvgIconView.Icon icon) {
        mIcon = icon;
    }

    public String translationKey() {
        return switch (this) {
            case GRAPH_EDITOR -> "geometry_node.workspace.area.graph_editor";
            case ASSET_BROWSER -> "geometry_node.workspace.area.asset_browser";
            case DATA_LIBRARY -> "geometry_node.workspace.area.data_library";
            case TERMINAL -> "geometry_node.workspace.area.terminal";
            case PERFORMANCE -> "geometry_node.workspace.area.performance";
        };
    }

    public SvgIconView.Icon icon() {
        return mIcon;
    }
}
