package com.mine.geometry_node.client.config;

@FunctionalInterface
public interface ConfigChangeListener {
    void onConfigChanged(AppConfig config);
}
