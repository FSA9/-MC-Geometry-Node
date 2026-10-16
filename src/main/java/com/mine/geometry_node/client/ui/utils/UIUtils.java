package com.mine.geometry_node.client.ui.utils;

import com.mine.geometry_node.client.ui.UIConstants;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Matrix;
import icyllis.modernui.view.View;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;

/**
 * 坐标与单位转换工具类
 */
public class UIUtils {
    private static final float REFERENCE_HEIGHT_PX = 1080.0f;

    /**
     * Keeps project UI sizing independent from Minecraft's GUI scale.
     */
    public static void syncFixedDensity() {
        float physicalHeight = REFERENCE_HEIGHT_PX;
        if (!Boolean.getBoolean("gn.standalone")) {
            physicalHeight = Minecraft.getInstance().getWindow().getHeight();
        }
        UIConstants.mDensity = Math.max(0.01f, physicalHeight / REFERENCE_HEIGHT_PX);
    }

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
     * 将 ModernUI 事件的根像素坐标转换到目标 View；这里不再进行 dp/px 换算。
     * matrix 和 point 由调用方复用，避免鼠标移动时分配对象。
     */
    public static boolean rootToLocal(View target, float rootX, float rootY,
                                      Matrix matrix, float[] point) {
        if (!Float.isFinite(rootX) || !Float.isFinite(rootY)) return false;
        matrix.setIdentity();
        target.transformMatrixToGlobal(matrix);
        if (!matrix.invert()) return false;
        point[0] = rootX;
        point[1] = rootY;
        matrix.mapPoint(point);
        return Float.isFinite(point[0]) && Float.isFinite(point[1]);
    }

    /**
     * 创建一个彻底无视游戏 GUI 比例的 TextView
     * 强行锁定其物理像素尺寸
     */
    public static TextView createLockedTextView(Context context, String text, float dpSize, int colorHex) {
        TextView tv = new TextView(context);
        tv.setText(text);
        setLockedTextSize(tv, dpSize);
        if (colorHex != 0) {
            tv.setTextColor(colorHex);
        }
        return tv;
    }

    public static void setLockedTextSize(TextView view, float dpSize) {
        if (view == null) return;
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp2px(dpSize));
    }
}
