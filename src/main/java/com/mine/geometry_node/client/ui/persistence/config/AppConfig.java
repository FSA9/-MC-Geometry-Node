package com.mine.geometry_node.client.ui.persistence.config;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {
    public AssetBrowserConfig assetBrowser = new AssetBrowserConfig();
    public ViewportConfig viewport = new ViewportConfig();
    public NodeConfig node = new NodeConfig();
    public KeyBindingsConfig keyBindings = new KeyBindingsConfig();

    public static AppConfig defaults() {
        return new AppConfig();
    }

    public static class AssetBrowserConfig {
        public List<String> quickAccessPaths = new ArrayList<>();
        public List<String> favoriteGraphPaths = new ArrayList<>();
        public String viewMode = "LIST";
    }

    public static class ViewportConfig {
        public int gridSize = 15;
        public boolean snapToGrid = false;
        public boolean showGridAndAxis = true;
    }

    public static class NodeConfig {
        public float cornerRadius = 1.5f;
    }

    public static class KeyBindingsConfig {
        public GlobalKeyBindingsConfig global = new GlobalKeyBindingsConfig();
        public ViewportKeyBindingsConfig viewport = new ViewportKeyBindingsConfig();
        public ShopEditorKeyBindingsConfig shopEditor = new ShopEditorKeyBindingsConfig();
    }

    public static class GlobalKeyBindingsConfig {
        public String undo = "CTRL+Z";
        public String redo = "CTRL+Y";
        public String save = "CTRL+S";
        public String copy = "CTRL+C";
        public String paste = "CTRL+V";
    }

    public static class ViewportKeyBindingsConfig {
        public String delete = "DELETE";
        public String toggleSnapToGrid = "SHIFT+TAB";
        public String toggleGridAndAxis = "SHIFT+ALT+Z";
        public String groupIntoFrame = "CTRL+J";
        public String groupIntoNodeGroup = "CTRL+G";
        public String moveSelection = "G";
    }

    public static class ShopEditorKeyBindingsConfig {
        public String clearSlot = "CTRL+LEFT_CLICK";
    }
}
