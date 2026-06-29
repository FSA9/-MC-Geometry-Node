package com.mine.geometry_node.client.ui.shortcut;

public enum KeyScope {
    GLOBAL("global"),
    VIEWPORT("viewport"),
    ASSET_LIBRARY("assetLibrary"),
    SHOP_EDITOR("shopEditor");

    private final String mConfigKey;

    KeyScope(String configKey) {
        mConfigKey = configKey;
    }

    public String configKey() {
        return mConfigKey;
    }
}
