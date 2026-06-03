package com.mine.geometry_node.client.ui.viewport.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortType;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.util.Objects;

final class ExpandedTextInputOverlay extends FrameLayout {
    private final EditorContext editorContext;
    private final NodeData nodeData;
    private final String portId;
    private final PortType expectedType;
    private final EditText editor;

    private ExpandedTextInputOverlay(Context context, EditorContext editorContext, NodeData nodeData, String portId,
                                     PortType expectedType, String text) {
        super(context);
        this.editorContext = editorContext;
        this.nodeData = nodeData;
        this.portId = portId;
        this.expectedType = expectedType;

        setBackground(rect(0xAA050608, 0.0f, 0, 0));
        setOnClickListener(v -> dismiss());
        setFocusable(true);
        setFocusableInTouchMode(true);

        LinearLayout window = new LinearLayout(context);
        window.setOrientation(LinearLayout.VERTICAL);
        window.setPadding(dp(18), dp(14), dp(18), dp(14));
        window.setBackground(rect(0xF01D2028, 6.0f, 1, 0xFF3C4658));
        window.setOnClickListener(v -> {
        });

        TextView title = label(context, "文本预览 / 编辑", 14.0f, 0xFFE8EDF6, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        window.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));

        editor = new EditText(context);
        editor.setText(text == null ? "" : text);
        editor.setTextColor(UIConstants.CLR_GRAY_LABEL);
        editor.setTextSize(14.0f);
        editor.setGravity(Gravity.LEFT | Gravity.TOP);
        editor.setSingleLine(false);
        editor.setMinLines(12);
        editor.setPadding(dp(12), dp(10), dp(12), dp(10));
        editor.setBackground(rect(0xFF111318, 4.0f, 1, 0xFF303846));
        editor.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ESCAPE) {
                dismiss();
                return true;
            }
            return false;
        });
        window.addView(editor, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(12), 0, 0);
        actions.addView(button(context, "取消", 0xFF3A3F4A, v -> dismiss()), new LinearLayout.LayoutParams(dp(80), dp(30)));
        TextView spacer = label(context, "", 1.0f, 0, Gravity.CENTER);
        actions.addView(spacer, new LinearLayout.LayoutParams(dp(10), 1));
        actions.addView(button(context, "确认", 0xFF3D638D, v -> commit()), new LinearLayout.LayoutParams(dp(86), dp(30)));
        window.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        FrameLayout.LayoutParams windowParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        windowParams.leftMargin = dp(36);
        windowParams.rightMargin = dp(36);
        windowParams.topMargin = dp(36);
        windowParams.bottomMargin = dp(36);
        addView(window, windowParams);
    }

    static void show(Context context, View anchor, EditorContext editorContext, NodeData nodeData, String portId,
                     PortType expectedType, String text) {
        if (editorContext == null || nodeData == null || anchor == null) {
            return;
        }
        ViewGroup host = findViewportHost(anchor);
        if (host == null) {
            return;
        }

        ExpandedTextInputOverlay overlay = new ExpandedTextInputOverlay(context, editorContext, nodeData, portId, expectedType, text);
        host.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.post(() -> {
            overlay.requestFocus();
            overlay.editor.requestFocus();
            overlay.editor.setSelection(overlay.editor.getText().length());
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEY_ESCAPE) {
            dismiss();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        super.dispatchTouchEvent(event);
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        super.dispatchGenericMotionEvent(event);
        return true;
    }

    private void commit() {
        Object parsedValue = parseValue(editor.getText().toString());
        if (parsedValue == null && (expectedType == PortType.INTEGER || expectedType == PortType.FLOAT)) {
            return;
        }
        Object currentValue = nodeData.inputs.get(portId);
        if (!Objects.equals(parsedValue, currentValue)) {
            CmdChangeInputValue cmd = new CmdChangeInputValue(editorContext.getGraphController(), nodeData.id, portId, currentValue, parsedValue);
            editorContext.getCommandManager().execute(cmd);
        }
        dismiss();
    }

    private Object parseValue(String text) {
        try {
            if (expectedType == PortType.INTEGER) {
                return Integer.parseInt(text);
            }
            if (expectedType == PortType.FLOAT) {
                return Float.parseFloat(text);
            }
            return text;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void dismiss() {
        if (getParent() instanceof ViewGroup parent) {
            parent.removeView(this);
            parent.requestFocus();
        }
    }

    private static ViewGroup findViewportHost(View anchor) {
        View current = anchor;
        while (current != null) {
            if (current instanceof com.mine.geometry_node.client.ui.viewport.Viewport viewport) {
                return viewport;
            }
            if (!(current.getParent() instanceof View)) {
                break;
            }
            current = (View) current.getParent();
        }
        return null;
    }

    private static TextView button(Context context, String text, int color, View.OnClickListener listener) {
        TextView view = label(context, text, 13.0f, 0xFFFFFFFF, Gravity.CENTER);
        view.setBackground(rect(color, 4.0f, 1, 0x553C4658));
        view.setOnClickListener(listener);
        return view;
    }

    private static TextView label(Context context, String text, float sizeDp, int color, int gravity) {
        TextView view = UIUtils.createLockedTextView(context, text, sizeDp, color);
        view.setGravity(gravity);
        return view;
    }

    private static ShapeDrawable rect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeWidthDp > 0) {
            drawable.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        }
        return drawable;
    }
}
