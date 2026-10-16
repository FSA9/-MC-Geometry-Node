package com.mine.geometry_node.client.input.binding;

public enum KeyScope {
    GLOBAL("global"),
    VIEWPORT("viewport"),
    GAME_VIEW("gameView"),
    GAME_VIEW_FLIGHT("gameViewFlight"),
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
