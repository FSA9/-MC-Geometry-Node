package com.mine.geometry_node.client.ui.Viewport.UIHints;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.persistence.ConfigManager;
import com.mine.geometry_node.core.node.port.PortType;

import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;

public class UIHintUtils {

    // 【修改】增加 expectedType 参数
    public static void applyStandardInputStyle(EditText et, PortType expectedType) {
        et.setTextColor(UIConstants.CLR_GRAY_LABEL);
        et.setTextSize(UIConstants.Node.TEXT_SIZE_LABEL);
        et.setGravity(icyllis.modernui.view.Gravity.RIGHT | icyllis.modernui.view.Gravity.CENTER_VERTICAL);

        // 【新增】强制单行，彻底屏蔽回车键产生的换行符
        et.setSingleLine(true);

        ShapeDrawable bgDrawable = new ShapeDrawable();
        bgDrawable.setColor(0xFF252525);
        bgDrawable.setCornerRadius(UIUtils.dp2px(ConfigManager.INSTANCE.getConfig().node.cornerRadius));
        bgDrawable.setStroke(UIUtils.dp2pxInt(1), 0xFF333333);

        et.setBackground(bgDrawable);
        et.setPadding(UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(2), UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(2));

        // 【新增】基于端点类型，挂载输入过滤器拦截非法字符
        if (expectedType == PortType.INTEGER) {
            // 整数：仅允许可选的负号开头，后续全为数字
            et.addTextChangedListener(createRegexWatcher(et, "^-?\\d*$"));
        } else if (expectedType == PortType.FLOAT || expectedType == PortType.XYZ) {
            // 浮点数：允许可选的负号，数字，以及最多一个小数点
            et.addTextChangedListener(createRegexWatcher(et, "^-?\\d*\\.?\\d*$"));
        }
    }

    // 【新增】生成正则拦截器的工厂方法
    private static TextWatcher createRegexWatcher(EditText et, String regex) {
        return new TextWatcher() {
            private String previousText = "";
            private boolean isRestoring = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (!isRestoring) {
                    previousText = s.toString();
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isRestoring) return;

                String current = s.toString();
                // 允许为空，允许输入单个 "-" (等待后续输入数字)，否则必须满足正则
                if (!current.isEmpty() && !current.equals("-") && !current.matches(regex)) {
                    isRestoring = true;
                    et.setText(previousText);
                    // 恢复光标到末尾，防止回退后光标跳到最前面
                    et.setSelection(previousText.length());
                    isRestoring = false;
                }
            }
        };
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