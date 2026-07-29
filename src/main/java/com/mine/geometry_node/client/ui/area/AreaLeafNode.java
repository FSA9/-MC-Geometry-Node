package com.mine.geometry_node.client.ui.area;

import icyllis.modernui.core.Context;

import java.util.EnumMap;
import java.util.Map;

final class AreaLeafNode extends AreaNode {
    private final Map<AreaEditorType, AreaEditorWindow> mWindows = new EnumMap<>(AreaEditorType.class);
    private AreaEditorType mEditorType;

    AreaLeafNode(AreaEditorType editorType) {
        mEditorType = editorType == null ? AreaEditorType.GRAPH_EDITOR : editorType;
    }

    AreaEditorType editorType() {
        return mEditorType;
    }

    void setEditorType(AreaEditorType editorType) {
        if (editorType != null) {
            mEditorType = editorType;
        }
    }

    AreaEditorWindow window(Context context, AreaEditorRegistry registry) {
        return mWindows.computeIfAbsent(mEditorType, type -> registry.create(context, type));
    }

    void swapContentsWith(AreaLeafNode other) {
        if (other == null || other == this) {
            return;
        }

        AreaEditorType editorType = mEditorType;
        mEditorType = other.mEditorType;
        other.mEditorType = editorType;

        Map<AreaEditorType, AreaEditorWindow> windows = new EnumMap<>(mWindows);
        mWindows.clear();
        mWindows.putAll(other.mWindows);
        other.mWindows.clear();
        other.mWindows.putAll(windows);
    }

    @Override
    void dispose() {
        for (AreaEditorWindow window : mWindows.values()) {
            if (window != null) {
                window.onHide();
            }
        }
        mWindows.clear();
    }
}
