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

    public AppConfig copy() {
        AppConfig copy = new AppConfig();
        copy.assetBrowser = assetBrowser != null ? assetBrowser.copy() : null;
        copy.viewport = viewport != null ? viewport.copy() : null;
        copy.node = node != null ? node.copy() : null;
        copy.keyBindings = keyBindings != null ? keyBindings.copy() : null;
        return copy;
    }

    public static class AssetBrowserConfig {
        public List<String> quickAccessPaths = new ArrayList<>();
        public List<String> favoriteGraphPaths = new ArrayList<>();
        public String viewMode = "LIST";
        public boolean rightSidebarVisible = true;
        public float rightSidebarWeight = 0.2f;
        public String rightSidebarTab = "properties";

        AssetBrowserConfig copy() {
            AssetBrowserConfig copy = new AssetBrowserConfig();
            copy.quickAccessPaths = quickAccessPaths != null ? new ArrayList<>(quickAccessPaths) : null;
            copy.favoriteGraphPaths = favoriteGraphPaths != null ? new ArrayList<>(favoriteGraphPaths) : null;
            copy.viewMode = viewMode;
            copy.rightSidebarVisible = rightSidebarVisible;
            copy.rightSidebarWeight = rightSidebarWeight;
            copy.rightSidebarTab = rightSidebarTab;
            return copy;
        }
    }

    public static class ViewportConfig {
        public int gridSize = 15;
        public boolean snapToGrid = false;
        public boolean showGridAndAxis = true;
        public boolean rightSidebarVisible = true;
        public float rightSidebarWeight = 0.2f;
        public String rightSidebarTab = "properties";

        ViewportConfig copy() {
            ViewportConfig copy = new ViewportConfig();
            copy.gridSize = gridSize;
            copy.snapToGrid = snapToGrid;
            copy.showGridAndAxis = showGridAndAxis;
            copy.rightSidebarVisible = rightSidebarVisible;
            copy.rightSidebarWeight = rightSidebarWeight;
            copy.rightSidebarTab = rightSidebarTab;
            return copy;
        }
    }

    public static class NodeConfig {
        public float cornerRadius = 1.5f;

        NodeConfig copy() {
            NodeConfig copy = new NodeConfig();
            copy.cornerRadius = cornerRadius;
            return copy;
        }
    }

    public static class KeyBindingsConfig {
        public GlobalKeyBindingsConfig global = new GlobalKeyBindingsConfig();
        public ViewportKeyBindingsConfig viewport = new ViewportKeyBindingsConfig();
        public ShopEditorKeyBindingsConfig shopEditor = new ShopEditorKeyBindingsConfig();

        KeyBindingsConfig copy() {
            KeyBindingsConfig copy = new KeyBindingsConfig();
            copy.global = global != null ? global.copy() : null;
            copy.viewport = viewport != null ? viewport.copy() : null;
            copy.shopEditor = shopEditor != null ? shopEditor.copy() : null;
            return copy;
        }
    }

    public static class GlobalKeyBindingsConfig {
        public String undo = "CTRL+Z";
        public String redo = "CTRL+Y";
        public String save = "CTRL+S";
        public String copy = "CTRL+C";
        public String paste = "CTRL+V";
        public String cut = "CTRL+X";
        public String delete = "DELETE";
        public String rename = "F2";

        GlobalKeyBindingsConfig copy() {
            GlobalKeyBindingsConfig copy = new GlobalKeyBindingsConfig();
            copy.undo = undo;
            copy.redo = redo;
            copy.save = save;
            copy.copy = this.copy;
            copy.paste = paste;
            copy.cut = cut;
            copy.delete = delete;
            copy.rename = rename;
            return copy;
        }
    }

    public static class ViewportKeyBindingsConfig {
        public String delete = "DELETE";
        public String toggleSnapToGrid = "SHIFT+TAB";
        public String toggleGridAndAxis = "SHIFT+ALT+Z";
        public String groupIntoFrame = "CTRL+J";
        public String groupIntoNodeGroup = "CTRL+G";
        public String moveSelection = "G";
        public String toggleRightSidebar = "N";

        ViewportKeyBindingsConfig copy() {
            ViewportKeyBindingsConfig copy = new ViewportKeyBindingsConfig();
            copy.delete = delete;
            copy.toggleSnapToGrid = toggleSnapToGrid;
            copy.toggleGridAndAxis = toggleGridAndAxis;
            copy.groupIntoFrame = groupIntoFrame;
            copy.groupIntoNodeGroup = groupIntoNodeGroup;
            copy.moveSelection = moveSelection;
            copy.toggleRightSidebar = toggleRightSidebar;
            return copy;
        }
    }

    public static class ShopEditorKeyBindingsConfig {
        public String clearSlot = "CTRL+LEFT_CLICK";

        ShopEditorKeyBindingsConfig copy() {
            ShopEditorKeyBindingsConfig copy = new ShopEditorKeyBindingsConfig();
            copy.clearSlot = clearSlot;
            return copy;
        }
    }
}
