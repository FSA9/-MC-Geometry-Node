package com.mine.geometry_node.client.ui.persistence.config;

@FunctionalInterface
public interface ConfigChangeListener {
    void onConfigChanged(AppConfig config);
}
