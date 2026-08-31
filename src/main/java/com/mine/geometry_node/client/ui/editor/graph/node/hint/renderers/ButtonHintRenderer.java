package com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.ButtonHintActionDispatcher;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintUtils;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.MetaKey;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

public class ButtonHintRenderer implements UIHintRenderer {
    private static final String DEFAULT_LABEL = "Button";
    private static final int DEFAULT_BG = UIConstants.Node.CLR_DYNAMIC_BTN_BG;
    private static final int DEFAULT_STROKE = UIConstants.Node.CLR_DYNAMIC_BTN_STROKE;
    private static final int DEFAULT_TEXT = UIConstants.Node.CLR_DYNAMIC_BTN_FG;

    @Override
    public float getRequiredExtraRows(PortRow row) {
        return 1.0f;
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        String label = firstNonBlank(
                hintString(row, PortMetaKeys.BUTTON_LABEL),
                DEFAULT_LABEL
        );
        int bgColor = firstColor(
                hintInt(row, PortMetaKeys.BUTTON_COLOR),
                DEFAULT_BG
        );
        int textColor = firstColor(
                hintInt(row, PortMetaKeys.BUTTON_TEXT_COLOR),
                DEFAULT_TEXT
        );
        String action = hintString(row, PortMetaKeys.BUTTON_ACTION);

        TextView button = new TextView(context);
        button.setText(displayLabel(label));
        button.setGravity(Gravity.CENTER);
        UIUtils.setLockedTextSize(button, UIConstants.Node.TEXT_SIZE_LABEL);
        button.setTextColor(textColor);
        button.setSingleLine(true);
        button.setPadding(UIUtils.dp2pxInt(6), 0, UIUtils.dp2pxInt(6), 0);
        button.setBackground(buttonBackground(bgColor));
        button.setOnClickListener(v -> ButtonHintActionDispatcher.dispatch(editorContext, nodeData, row, action, v));
        return button;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        float startX = UIConstants.Node.LABEL_MARGIN_PORT;
        float endX = nodeWidth - UIConstants.Node.LABEL_MARGIN_PORT;

        boolean hasLabel = row.leftPort() != null || row.rightPort() != null;
        float topOffset = hasLabel ? UIConstants.Node.ROW_HEIGHT : 0.0f;
        float buttonHeight = UIHintUtils.getStandardInputHeight();
        float verticalMargin = (UIConstants.Node.ROW_HEIGHT - buttonHeight) / 2.0f;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        int widthPx = UIUtils.dp2pxInt(endX - startX);
        int heightPx = UIUtils.dp2pxInt(buttonHeight);
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(widthPx, heightPx);
        } else {
            lp.width = widthPx;
            lp.height = heightPx;
        }
        lp.gravity = Gravity.LEFT | Gravity.TOP;
        lp.leftMargin = UIUtils.dp2pxInt(startX);
        lp.topMargin = UIUtils.dp2pxInt(currentY + topOffset + verticalMargin);
        view.setLayoutParams(lp);
    }

    private static ShapeDrawable buttonBackground(int bgColor) {
        ShapeDrawable bg = new ShapeDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(UIUtils.dp2px(2.0f));
        bg.setStroke(UIUtils.dp2pxInt(1), DEFAULT_STROKE);
        return bg;
    }

    private static String hintString(PortRow row, MetaKey<String> key) {
        if (row == null || row.hintParams() == null) {
            return null;
        }
        Object value = row.hintParams().get(key);
        return value instanceof String string ? string : null;
    }

    private static Integer hintInt(PortRow row, MetaKey<Integer> key) {
        if (row == null || row.hintParams() == null) {
            return null;
        }
        Object value = row.hintParams().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static String firstNonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return fallback;
    }

    private static String displayLabel(String label) {
        if (label != null && label.startsWith("geometry_node.")) {
            return Component.translatable(label).getString();
        }
        return label;
    }

    private static int firstColor(Integer first, int fallback) {
        if (first != null) {
            return first;
        }
        return fallback;
    }
}
