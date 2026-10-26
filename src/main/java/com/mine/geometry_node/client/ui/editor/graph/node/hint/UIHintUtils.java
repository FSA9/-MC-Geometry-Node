package com.mine.geometry_node.client.ui.editor.graph.node.hint;

import com.mine.geometry_node.client.ui.editor.graph.node.NodeUiMetrics;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import icyllis.modernui.core.Context;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.world.phys.Vec3;

public class UIHintUtils {

    public static final float INPUT_HEIGHT_OFFSET = 2.0f;

    public static float getStandardInputHeight() {
        return NodeUiMetrics.ROW_HEIGHT - INPUT_HEIGHT_OFFSET;
    }

    public static boolean hasDynamicRemoveButton(PortRow row) {
        return row != null
                && row.hintParams() != null
                && Boolean.TRUE.equals(row.hintParams().get(PortMetaKeys.IS_DYNAMIC))
                && !Boolean.FALSE.equals(row.hintParams().get(PortMetaKeys.DYNAMIC_REMOVE_BUTTON))
                && row.hintParams().get(PortMetaKeys.DYNAMIC_INDEX) instanceof Integer;
    }

    public static EditText createInput(Context context) {
        return new UiTextInput(context, new UiTextInput.Style(NodeUiMetrics.TEXT_SIZE_LABEL, 8,
                ConfigManager.INSTANCE.getConfig().node.cornerRadius, UiMetrics.BORDER,
                UiThemes.EDITOR.text().secondary(), UiThemes.EDITOR.text().hint(), UiThemes.EDITOR.surface().panel(), UiThemes.EDITOR.border().subtle()));
    }

    public static void applyStandardInputStyle(EditText et, PortType expectedType) {
        et.setTextColor(UiThemes.EDITOR.text().secondary());
        UIUtils.setLockedTextSize(et, NodeUiMetrics.TEXT_SIZE_LABEL);
        et.setGravity(icyllis.modernui.view.Gravity.RIGHT | icyllis.modernui.view.Gravity.CENTER_VERTICAL);
        et.setSingleLine(true);

        if (!(et instanceof UiTextInput)) {
            ShapeDrawable bgDrawable = new ShapeDrawable();
            bgDrawable.setColor(UiThemes.EDITOR.surface().panel());
            bgDrawable.setCornerRadius(UIUtils.dp2px(ConfigManager.INSTANCE.getConfig().node.cornerRadius));
            bgDrawable.setStroke(UIUtils.dp2pxInt(1), UiThemes.EDITOR.border().subtle());
            et.setBackground(bgDrawable);
        }

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
