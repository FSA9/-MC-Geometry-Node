package com.mine.geometry_node.client.ui.Viewport.UIHints;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.persistence.ConfigManager;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;

public class UIHintUtils {

    /**
     * 应用标准的节点输入框样式
     */
    public static void applyStandardInputStyle(EditText et) {
        et.setTextColor(UIConstants.CLR_GRAY_LABEL);
        et.setTextSize(UIConstants.Node.TEXT_SIZE_LABEL);
        et.setGravity(icyllis.modernui.view.Gravity.RIGHT | icyllis.modernui.view.Gravity.CENTER_VERTICAL);

        ShapeDrawable bgDrawable = new ShapeDrawable();
        bgDrawable.setColor(0xFF252525);
        bgDrawable.setCornerRadius(ConfigManager.INSTANCE.getConfig().node.cornerRadius * UIConstants.mDensity);
        bgDrawable.setStroke((int) (UIConstants.mDensity), 0xFF333333);

        et.setBackground(bgDrawable);
        et.setPadding(8, 2, 8, 2);
    }

    /**
     * 获取标准的输入框排版参数
     */
    public static LinearLayout.LayoutParams getStandardInputLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UIConstants.Node.ROW_HEIGHT - 4
        );
        lp.bottomMargin = 4;
        return lp;
    }

    /**
     * 安全地解析 Vector 列表中的数值，防止越界或类型转换异常
     */
    public static float getSafeVectorComponent(Object rawVal, int index) {
        if (rawVal instanceof java.util.List<?> list && index < list.size()) {
            Object item = list.get(index);
            if (item instanceof Number) {
                return ((Number) item).floatValue();
            }
        }
        return 0f;
    }
}