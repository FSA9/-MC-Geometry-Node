package com.mine.geometry_node.client.ui.workspace.persistence;

import java.util.ArrayList;
import java.util.List;

public final class EditorSessionState {
    public static final int CURRENT_VERSION = 2;

    public int version = CURRENT_VERSION;
    public AreaState layout;

    public static final class AreaState {
        public String ownerId = java.util.UUID.randomUUID().toString();
        @com.google.gson.annotations.JsonAdapter(EditorStateSlotsAdapter.class)
        public java.util.Map<String, java.util.Map<String, StateSlot>> slots = new java.util.LinkedHashMap<>();
        public String kind = "leaf";
        public String editorType = "GRAPH_EDITOR";
        public String direction;
        public float ratio = 0.5f;
        public AreaState first;
        public AreaState second;
        // Read-only v1 migration inputs; adapters clear these before v2 snapshots.
        public AssetBrowserState assetBrowser;
        public TerminalState terminal;
    }

    public static final class StateSlot {
        transient com.google.gson.JsonElement rawEnvelope;
        public int version;
        public com.google.gson.JsonElement value;
        public StateSlot() {}
        public StateSlot(int version, com.google.gson.JsonElement value) {
            this.version = version;
            this.value = value;
        }
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
        public List<TerminalTabState> tabs = new ArrayList<>();
    }

    public static final class TerminalTabState {
        public String id = "";
        public String title = "";
        public String mode = "COMMAND";
        public String profileId = "";
    }
}
