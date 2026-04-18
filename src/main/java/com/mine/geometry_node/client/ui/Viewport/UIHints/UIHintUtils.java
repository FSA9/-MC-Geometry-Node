package com.mine.geometry_node.client.ui.Viewport.UIHints;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.UIUtils;
import com.mine.geometry_node.client.ui.persistence.ConfigManager;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;

public class UIHintUtils {

    public static void applyStandardInputStyle(EditText et) {
        et.setTextColor(UIConstants.CLR_GRAY_LABEL);
        et.setTextSize(UIConstants.Node.TEXT_SIZE_LABEL);
        et.setGravity(icyllis.modernui.view.Gravity.RIGHT | icyllis.modernui.view.Gravity.CENTER_VERTICAL);

        ShapeDrawable bgDrawable = new ShapeDrawable();
        bgDrawable.setColor(0xFF252525);
        bgDrawable.setCornerRadius(UIUtils.dp2px(ConfigManager.INSTANCE.getConfig().node.cornerRadius));
        bgDrawable.setStroke(UIUtils.dp2pxInt(1), 0xFF333333);

        et.setBackground(bgDrawable);
        et.setPadding(UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(2), UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(2));
    }

    public static LinearLayout.LayoutParams getStandardInputLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(UIConstants.Node.ROW_HEIGHT - 4)
        );
        lp.bottomMargin = UIUtils.dp2pxInt(4);
        return lp;
    }

    public static float getSafeVectorComponent(Object rawVal, int index) {
        if (rawVal instanceof java.util.List<?> list && index < list.size()) {
            Object item = list.get(index);
            if (item instanceof Number) return ((Number) item).floatValue();
        }
        return 0f;
    }
}