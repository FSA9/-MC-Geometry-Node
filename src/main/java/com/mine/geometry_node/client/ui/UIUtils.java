package com.mine.geometry_node.client.ui;

/**
 * 坐标与单位转换工具类
 */
public class UIUtils {

    /**
     * 将逻辑坐标 (dp) 转换为物理像素 (px)
     */
    public static float dp2px(float dp) {
        return dp * UIConstants.mDensity;
    }

    /**
     * 将逻辑坐标 (dp) 转换为物理像素 (px) - 返回整数
     */
    public static int dp2pxInt(float dp) {
        return Math.round(dp * UIConstants.mDensity);
    }

    /**
     * 将物理像素 (px) 转换为逻辑坐标 (dp)
     */
    public static float px2dp(float px) {
        return px / UIConstants.mDensity;
    }
}