package com.mine.geometry_node.client.ui.utils;

import com.mine.geometry_node.client.ui.UIConstants;
import icyllis.modernui.core.Context;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.widget.TextView;

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

    /**
     * 创建一个彻底无视游戏 GUI 比例的 TextView
     * 强行锁定其物理像素尺寸
     */
    public static TextView createLockedTextView(Context context, String text, float dpSize, int colorHex) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp2px(dpSize));
        if (colorHex != 0) {
            tv.setTextColor(colorHex);
        }
        return tv;
    }
}