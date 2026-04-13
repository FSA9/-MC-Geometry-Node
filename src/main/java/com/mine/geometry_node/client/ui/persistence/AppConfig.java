package com.mine.geometry_node.client.ui.persistence;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {
    public int version = 1;
    public AssetBrowserConfig assetBrowser = new AssetBrowserConfig();
    public ViewportConfig viewport = new ViewportConfig();
    public NodeConfig node = new NodeConfig();

    public static class AssetBrowserConfig {
        // 快捷访问路径
        public List<String> quickAccessPaths = new ArrayList<>();
    }

    public static class ViewportConfig {
        public int gridSize = 15;
    }

    public static class NodeConfig {
        public float cornerRadius = 1.5f;
    }
}