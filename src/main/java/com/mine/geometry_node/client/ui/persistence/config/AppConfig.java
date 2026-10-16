package com.mine.geometry_node.client.ui.persistence.config;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {
    public AssetBrowserConfig assetBrowser = new AssetBrowserConfig();
    public ViewportConfig viewport = new ViewportConfig();
    public GameViewConfig gameView = new GameViewConfig();
    public NodeConfig node = new NodeConfig();
    public PreviewCacheConfig previewCache = new PreviewCacheConfig();
    public KeyBindingsConfig keyBindings = new KeyBindingsConfig();

    public static AppConfig defaults() {
        return new AppConfig();
    }

    public AppConfig copy() {
        AppConfig copy = new AppConfig();
        copy.assetBrowser = assetBrowser != null ? assetBrowser.copy() : null;
        copy.viewport = viewport != null ? viewport.copy() : null;
        copy.gameView = gameView != null ? gameView.copy() : null;
        copy.node = node != null ? node.copy() : null;
        copy.previewCache = previewCache != null ? previewCache.copy() : null;
        copy.keyBindings = keyBindings != null ? keyBindings.copy() : null;
        return copy;
    }

    public static class AssetBrowserConfig {
        public List<String> quickAccessPaths = new ArrayList<>();
        public List<String> favoriteAssetPaths = new ArrayList<>();
        public String viewMode = "LIST";
        public boolean rightSidebarVisible = true;
        public float rightSidebarWeight = 0.2f;
        public String rightSidebarTab = "properties";

        AssetBrowserConfig copy() {
            AssetBrowserConfig copy = new AssetBrowserConfig();
            copy.quickAccessPaths = quickAccessPaths != null ? new ArrayList<>(quickAccessPaths) : null;
            copy.favoriteAssetPaths = favoriteAssetPaths != null ? new ArrayList<>(favoriteAssetPaths) : null;
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

    public static class GameViewConfig {
        public int backgroundFps = 20;

        GameViewConfig copy() {
            GameViewConfig copy = new GameViewConfig();
            copy.backgroundFps = backgroundFps;
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

    public static class PreviewCacheConfig {
        public int maxSizeMiB = 64;
        public String location = ".cache/geometry_node/asset_previews";

        PreviewCacheConfig copy() {
            PreviewCacheConfig copy = new PreviewCacheConfig();
            copy.maxSizeMiB = maxSizeMiB;
            copy.location = location;
            return copy;
        }
    }

    public static class KeyBindingsConfig {
        public GlobalKeyBindingsConfig global = new GlobalKeyBindingsConfig();
        public ViewportKeyBindingsConfig viewport = new ViewportKeyBindingsConfig();
        public GameViewKeyBindingsConfig gameView = new GameViewKeyBindingsConfig();
        public ShopEditorKeyBindingsConfig shopEditor = new ShopEditorKeyBindingsConfig();

        KeyBindingsConfig copy() {
            KeyBindingsConfig copy = new KeyBindingsConfig();
            copy.global = global != null ? global.copy() : null;
            copy.viewport = viewport != null ? viewport.copy() : null;
            copy.gameView = gameView != null ? gameView.copy() : null;
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
        public String selectAll = "A";
        public String delete = "DELETE";
        public String toggleSnapToGrid = "SHIFT+TAB";
        public String toggleGridAndAxis = "SHIFT+ALT+Z";
        public String groupIntoFrame = "CTRL+J";
        public String groupIntoNodeGroup = "CTRL+G";
        public String moveSelection = "G";
        public String toggleRightSidebar = "N";

        ViewportKeyBindingsConfig copy() {
            ViewportKeyBindingsConfig copy = new ViewportKeyBindingsConfig();
            copy.selectAll = selectAll;
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

    public static class GameViewKeyBindingsConfig {
        public String fly = "SHIFT+GRAVE_ACCENT";
        public String toggleProjection = "KP_5";
        public String toggleSidebar = "N";

        public String forward = "W";
        public String backward = "S";
        public String left = "A";
        public String right = "D";
        public String up = "SPACE";
        public String down = "CTRL";
        public String exit = "ESCAPE";

        GameViewKeyBindingsConfig copy() {
            GameViewKeyBindingsConfig copy = new GameViewKeyBindingsConfig();
            copy.fly = fly;
            copy.toggleProjection = toggleProjection;
            copy.toggleSidebar = toggleSidebar;
            copy.forward = forward;
            copy.backward = backward;
            copy.left = left;
            copy.right = right;
            copy.up = up;
            copy.down = down;
            copy.exit = exit;
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
