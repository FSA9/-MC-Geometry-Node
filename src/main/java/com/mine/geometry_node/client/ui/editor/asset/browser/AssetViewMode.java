package com.mine.geometry_node.client.ui.editor.asset.browser;

enum AssetViewMode {
    LIST("LIST", 0, 40, 14, 14, 0),
    ICON_SMALL("ICON_SMALL", 84, 70, 22, 11, 13),
    ICON_MEDIUM("ICON_MEDIUM", 108, 92, 32, 12, 19),
    ICON_LARGE("ICON_LARGE", 144, 120, 46, 13, 28);

    final String configValue;
    final float itemWidthDp;
    final float itemHeightDp;
    final float iconTextSizeDp;
    final float nameTextSizeDp;
    final int iconNameLimit;

    AssetViewMode(String configValue, float itemWidthDp, float itemHeightDp, float iconTextSizeDp, float nameTextSizeDp, int iconNameLimit) {
        this.configValue = configValue;
        this.itemWidthDp = itemWidthDp;
        this.itemHeightDp = itemHeightDp;
        this.iconTextSizeDp = iconTextSizeDp;
        this.nameTextSizeDp = nameTextSizeDp;
        this.iconNameLimit = iconNameLimit;
    }

    static AssetViewMode fromConfig(String value) {
        for (AssetViewMode mode : values()) {
            if (mode.configValue.equals(value)) return mode;
        }
        return LIST;
    }
}
