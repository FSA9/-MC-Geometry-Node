package com.mine.geometry_node.client.ui.editor.graph.node.hint;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.core.node.port.PortType;

import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.world.phys.Vec3;

public class UIHintUtils {

    public static final float INPUT_HEIGHT_OFFSET = 2.0f;

    public static float getStandardInputHeight() {
        return UIConstants.Node.ROW_HEIGHT - INPUT_HEIGHT_OFFSET;
    }

    public static void applyStandardInputStyle(EditText et, PortType expectedType) {
        et.setTextColor(UIConstants.CLR_GRAY_LABEL);
        UIUtils.setLockedTextSize(et, UIConstants.Node.TEXT_SIZE_LABEL);
        et.setGravity(icyllis.modernui.view.Gravity.RIGHT | icyllis.modernui.view.Gravity.CENTER_VERTICAL);
        et.setSingleLine(true);

        ShapeDrawable bgDrawable = new ShapeDrawable();
        bgDrawable.setColor(0xFF252525);
        bgDrawable.setCornerRadius(UIUtils.dp2px(ConfigManager.INSTANCE.getConfig().node.cornerRadius));
        bgDrawable.setStroke(UIUtils.dp2pxInt(1), 0xFF333333);

        et.setBackground(bgDrawable);

        et.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);

        if (expectedType == PortType.INTEGER || expectedType == PortType.LONG) {
            et.addTextChangedListener(createRegexWatcher(et, "^-?\\d*$"));
        } else if (expectedType == PortType.FLOAT || expectedType == PortType.XYZ) {
            et.addTextChangedListener(createRegexWatcher(et, "^-?\\d*\\.?\\d*$"));
        }
    }


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
        // 统一使用像素转换
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(getStandardInputHeight())
        );
    }

    public static float getSafeVectorComponent(Object rawVal, int index) {
        if (rawVal instanceof Vec3 vec) {
            return switch (index) {
                case 0 -> (float) vec.x;
                case 1 -> (float) vec.y;
                case 2 -> (float) vec.z;
                default -> 0f;
            };
        }
        if (rawVal instanceof java.util.List<?> list && index < list.size()) {
            Object item = list.get(index);
            if (item instanceof Number) return ((Number) item).floatValue();
        }
        return 0f;
    }
}
