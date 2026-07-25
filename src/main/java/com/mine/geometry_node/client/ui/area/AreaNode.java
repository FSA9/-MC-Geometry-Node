package com.mine.geometry_node.client.ui.area;

abstract class AreaNode {
    private AreaSplitNode mParent;

    AreaSplitNode parent() {
        return mParent;
    }

    void setParent(AreaSplitNode parent) {
        mParent = parent;
    }

    void dispose() {
    }
}
