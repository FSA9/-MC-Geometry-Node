package com.mine.geometry_node.client.ui.persistence.session;

public final class EditorSessionState {
    public static final int CURRENT_VERSION = 1;

    public int version = CURRENT_VERSION;
    public AreaState layout;

    public static final class AreaState {
        public String kind = "leaf";
        public String editorType = "GRAPH_EDITOR";
        public String direction;
        public float ratio = 0.5f;
        public AreaState first;
        public AreaState second;
        public GraphEditorState graphEditor = new GraphEditorState();
        public AssetBrowserState assetBrowser = new AssetBrowserState();
        public TerminalState terminal = new TerminalState();
    }

    public static final class GraphEditorState {
        public float outlinerWeight = 0.2f;
    }

    public static final class AssetBrowserState {
        public String location = "LOCAL";
        public String localPath = "";
        public String remotePath = "";
        public float navigationWeight = 0.2f;
    }

    public static final class TerminalState {
        public int tabCount = 1;
        public int activeTab = 0;
    }
}
