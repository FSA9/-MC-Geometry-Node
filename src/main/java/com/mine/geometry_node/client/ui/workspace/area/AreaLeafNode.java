package com.mine.geometry_node.client.ui.workspace.area;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.ui.persistence.session.EditorSessionState;
import icyllis.modernui.core.Context;

import java.util.EnumMap;
import java.util.Map;

final class AreaLeafNode extends AreaNode {
    private final Map<AreaEditorType, AreaEditorWindow> mWindows = new EnumMap<>(AreaEditorType.class);
    private AreaEditorType mEditorType;
    private EditorSessionState.AreaState mSessionState;

    AreaLeafNode(AreaEditorType editorType) {
        this(createSessionState(editorType));
    }

    AreaLeafNode(EditorSessionState.AreaState sessionState) {
        mSessionState = sessionState == null ? createSessionState(AreaEditorType.GRAPH_EDITOR) : sessionState;
        mEditorType = parseEditorType(mSessionState.editorType);
        mSessionState.kind = "leaf";
        mSessionState.editorType = mEditorType.name();
        if (mSessionState.graphEditor == null) {
            mSessionState.graphEditor = new EditorSessionState.GraphEditorState();
        }
        if (mSessionState.assetBrowser == null) {
            mSessionState.assetBrowser = new EditorSessionState.AssetBrowserState();
        }
        if (mSessionState.terminal == null) {
            mSessionState.terminal = new EditorSessionState.TerminalState();
        }
    }

    AreaEditorType editorType() {
        return mEditorType;
    }

    void setEditorType(AreaEditorType editorType) {
        if (editorType != null) {
            mEditorType = editorType;
            mSessionState.editorType = editorType.name();
        }
    }

    AreaEditorWindow window(Context context, AreaEditorRegistry registry, Runnable sessionChanged) {
        return mWindows.computeIfAbsent(
                mEditorType,
                type -> registry.create(context, type, mSessionState, sessionChanged));
    }

    EditorSessionState.AreaState sessionState() {
        return mSessionState;
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

        EditorSessionState.AreaState sessionState = mSessionState;
        mSessionState = other.mSessionState;
        other.mSessionState = sessionState;
    }

    @Override
    void dispose() {
        try {
            for (AreaEditorWindow window : mWindows.values()) {
                if (window == null) {
                    continue;
                }
                try {
                    window.onDispose();
                } catch (RuntimeException error) {
                    GeometryNode.LOGGER.error("Failed to dispose an area editor window", error);
                }
            }
        } finally {
            mWindows.clear();
        }
    }

    private static EditorSessionState.AreaState createSessionState(AreaEditorType editorType) {
        AreaEditorType safeType = editorType == null ? AreaEditorType.GRAPH_EDITOR : editorType;
        EditorSessionState.AreaState state = new EditorSessionState.AreaState();
        state.editorType = safeType.name();
        return state;
    }

    private static AreaEditorType parseEditorType(String name) {
        try {
            return AreaEditorType.valueOf(name);
        } catch (RuntimeException ignored) {
            return AreaEditorType.GRAPH_EDITOR;
        }
    }
}
