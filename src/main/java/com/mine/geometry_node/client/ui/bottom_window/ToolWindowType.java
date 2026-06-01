package com.mine.geometry_node.client.ui.bottom_window;

/**
 * 底部工具窗口的类型定义
 */
public enum ToolWindowType {

    ASSET_BROWSER("资产浏览器", "\uD83D\uDCC1"),
    TERMINAL("终端", "\uD83D\uDCBB"),
    PERFORMANCE("性能监视器", "\uD83D\uDCC8");

    private final String displayName;
    private final String iconChar;

    ToolWindowType(String displayName, String iconChar) {
        this.displayName = displayName;
        this.iconChar = iconChar;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIconChar() {
        return iconChar;
    }
}