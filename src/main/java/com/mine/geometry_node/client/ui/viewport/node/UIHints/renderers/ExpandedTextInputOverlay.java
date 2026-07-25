package com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers;

import com.mine.geometry_node.client.ui.common.ColorPickerDialog;
import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintValueBinder;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.value.RichTextValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.SpannableStringBuilder;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.text.style.ForegroundColorSpan;
import icyllis.modernui.text.style.StrikethroughSpan;
import icyllis.modernui.text.style.StyleSpan;
import icyllis.modernui.text.style.UnderlineSpan;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ExpandedTextInputOverlay extends FrameLayout {
    private static ExpandedTextInputOverlay sOpenOverlay;

    private final EditorContext editorContext;
    private final NodeData nodeData;
    private final String portId;
    private final PortType expectedType;
    private final boolean richTextMode;
    private final EditText editor;

    private ExpandedTextInputOverlay(Context context, EditorContext editorContext, NodeData nodeData, String portId,
                                     PortType expectedType, Object value) {
        super(context);
        this.editorContext = editorContext;
        this.nodeData = nodeData;
        this.portId = portId;
        this.expectedType = expectedType;
        this.richTextMode = expectedType == PortType.RICH_TEXT;

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

        if (richTextMode) {
            window.addView(createRichTextToolbar(context), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        }

        editor = new EditText(context);
        if (richTextMode) {
            editor.setText(toEditable(RichTextValue.from(value)), TextView.BufferType.EDITABLE);
        } else {
            editor.setText(value == null ? "" : value.toString());
        }
        editor.setTextColor(UIConstants.CLR_GRAY_LABEL);
        UIUtils.setLockedTextSize(editor, 14.0f);
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
                     PortType expectedType, Object value) {
        if (editorContext == null || nodeData == null || anchor == null) {
            return;
        }
        ViewGroup host = findWindowHost(anchor);
        if (host == null) {
            return;
        }

        ExpandedTextInputOverlay overlay = new ExpandedTextInputOverlay(context, editorContext, nodeData, portId, expectedType, value);
        closeOpenOverlay();
        host.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sOpenOverlay = overlay;
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
        Object parsedValue = richTextMode
                ? toRichTextValue(editor.getText()).toMap()
                : UIHintValueBinder.parseText(editor.getText().toString(), expectedType);
        if (parsedValue == null && UIHintValueBinder.requiresNumericValue(expectedType)) {
            return;
        }
        UIHintValueBinder.commit(editorContext, nodeData, portId, parsedValue);
        dismiss();
    }

    private void dismiss() {
        if (sOpenOverlay == this) {
            sOpenOverlay = null;
        }
        if (getParent() instanceof ViewGroup parent) {
            parent.removeView(this);
            parent.requestFocus();
        }
    }

    private static void closeOpenOverlay() {
        if (sOpenOverlay != null) {
            sOpenOverlay.dismiss();
        }
    }

    private static ViewGroup findWindowHost(View anchor) {
        View current = anchor;
        ViewGroup best = anchor instanceof ViewGroup viewGroup ? viewGroup : null;
        while (current != null) {
            if (current instanceof FrameLayout frameLayout) {
                best = frameLayout;
            }
            if (!(current.getParent() instanceof View parentView)) {
                break;
            }
            current = parentView;
        }
        return best != null ? best : anchor.getParent() instanceof ViewGroup parent ? parent : null;
    }

    private LinearLayout createRichTextToolbar(Context context) {
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        toolbar.setPadding(0, dp(4), 0, dp(6));

        toolbar.addView(toolButton(context, "B", v -> applyStyle("bold", true)), new LinearLayout.LayoutParams(dp(30), dp(26)));
        toolbar.addView(toolButton(context, "I", v -> applyStyle("italic", true)), new LinearLayout.LayoutParams(dp(30), dp(26)));
        toolbar.addView(toolButton(context, "U", v -> applyStyle("underlined", true)), new LinearLayout.LayoutParams(dp(30), dp(26)));
        toolbar.addView(toolButton(context, "S", v -> applyStyle("strikethrough", true)), new LinearLayout.LayoutParams(dp(30), dp(26)));
        toolbar.addView(toolButton(context, "Color", v -> showColorPicker()), new LinearLayout.LayoutParams(dp(58), dp(26)));
        toolbar.addView(toolButton(context, "Latex", v -> applyLatex()), new LinearLayout.LayoutParams(dp(58), dp(26)));
        toolbar.addView(toolButton(context, "Clear", v -> clearSelectionStyles()), new LinearLayout.LayoutParams(dp(58), dp(26)));
        return toolbar;
    }

    private TextView toolButton(Context context, String text, View.OnClickListener listener) {
        TextView view = button(context, text, 0xFF2D3440, listener);
        UIUtils.setLockedTextSize(view, 12.0f);
        return view;
    }

    private void applyStyle(String key, Object value) {
        int start = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
        int end = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
        applyStyle(key, value, start, end);
    }

    private void applyStyle(String key, Object value, int start, int end) {
        if (start == end) {
            return;
        }

        Editable editable = editor.getText();
        int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        switch (key) {
            case "bold" -> editable.setSpan(new StyleSpan(Typeface.BOLD), start, end, flags);
            case "italic" -> editable.setSpan(new StyleSpan(Typeface.ITALIC), start, end, flags);
            case "underlined" -> editable.setSpan(new UnderlineSpan(), start, end, flags);
            case "strikethrough" -> editable.setSpan(new StrikethroughSpan(), start, end, flags);
            case "color" -> editable.setSpan(new ForegroundColorSpan(parseColor(value, 0xFFFFFFFF)), start, end, flags);
            default -> {
            }
        }
        editor.setSelection(start, end);
    }

    private void showColorPicker() {
        int start = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
        int end = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
        if (start == end) {
            return;
        }
        int initialColor = selectedColor(start, end);
        ColorPickerDialog.show(getContext(), editor, initialColor, color -> {
            applyStyle("color", String.format("#%06x", color & 0xFFFFFF), start, end);
            editor.requestFocus();
            editor.setSelection(start, end);
        });
    }

    private void applyLatex() {
        int start = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
        int end = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
        if (start == end) {
            return;
        }
        Editable editable = editor.getText();
        clearSpans(editable, start, end);
        editable.setSpan(new LatexSpan(editable.subSequence(start, end).toString(), "inline"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        editable.setSpan(new ForegroundColorSpan(0xFF88D7FF), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        editable.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        editor.setSelection(start, end);
    }

    private void clearSelectionStyles() {
        int start = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
        int end = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
        if (start == end) {
            return;
        }
        clearSpans(editor.getText(), start, end);
        editor.setSelection(start, end);
    }

    private void clearSpans(Editable editable, int start, int end) {
        for (Object span : editable.getSpans(start, end, Object.class)) {
            if (span instanceof StyleSpan || span instanceof ForegroundColorSpan
                    || span instanceof UnderlineSpan || span instanceof StrikethroughSpan
                    || span instanceof LatexSpan) {
                editable.removeSpan(span);
            }
        }
    }

    private static SpannableStringBuilder toEditable(RichTextValue value) {
        RichTextValue safe = value == null ? RichTextValue.EMPTY : value;
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (safe.segments().isEmpty()) {
            builder.append(safe.plain());
            return builder;
        }

        for (RichTextValue.Segment segment : safe.segments()) {
            int start = builder.length();
            if (RichTextValue.KIND_LATEX.equals(segment.kind())) {
                builder.append(segment.source());
                builder.setSpan(new LatexSpan(segment.source(), segment.display()), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(0xFF88D7FF), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new StyleSpan(Typeface.ITALIC), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                builder.append(segment.text());
                applySegmentStyle(builder, start, builder.length(), segment.style());
            }
        }
        return builder;
    }

    private static RichTextValue toRichTextValue(Editable editable) {
        String plain = editable == null ? "" : editable.toString();
        if (plain.isEmpty()) {
            return RichTextValue.EMPTY;
        }

        List<RichTextValue.Segment> segments = new ArrayList<>();
        int cursor = 0;
        while (cursor < plain.length()) {
            int next = nextTransition(editable, cursor, plain.length());
            if (next <= cursor) {
                next = cursor + 1;
            }
            String text = plain.substring(cursor, next);
            LatexSpan latex = firstSpan(editable, cursor, next, LatexSpan.class);
            if (latex != null) {
                segments.add(RichTextValue.Segment.latex(text, latex.display()));
            } else {
                segments.add(RichTextValue.Segment.text(text, collectStyle(editable, cursor, next)));
            }
            cursor = next;
        }
        return new RichTextValue(plain, segments);
    }

    private static int nextTransition(Editable editable, int start, int limit) {
        int next = limit;
        next = Math.min(next, editable.nextSpanTransition(start, limit, StyleSpan.class));
        next = Math.min(next, editable.nextSpanTransition(start, limit, ForegroundColorSpan.class));
        next = Math.min(next, editable.nextSpanTransition(start, limit, UnderlineSpan.class));
        next = Math.min(next, editable.nextSpanTransition(start, limit, StrikethroughSpan.class));
        next = Math.min(next, editable.nextSpanTransition(start, limit, LatexSpan.class));
        return next;
    }

    private static Map<String, Object> collectStyle(Editable editable, int start, int end) {
        Map<String, Object> style = new LinkedHashMap<>();
        for (StyleSpan span : editable.getSpans(start, end, StyleSpan.class)) {
            int value = span.getStyle();
            if (value == Typeface.BOLD || value == Typeface.BOLD_ITALIC) {
                style.put("bold", true);
            }
            if (value == Typeface.ITALIC || value == Typeface.BOLD_ITALIC) {
                style.put("italic", true);
            }
        }
        ForegroundColorSpan color = firstSpan(editable, start, end, ForegroundColorSpan.class);
        if (color != null) {
            style.put("color", String.format("#%06x", color.getForegroundColor() & 0xFFFFFF));
        }
        if (firstSpan(editable, start, end, UnderlineSpan.class) != null) {
            style.put("underlined", true);
        }
        if (firstSpan(editable, start, end, StrikethroughSpan.class) != null) {
            style.put("strikethrough", true);
        }
        return style;
    }

    private static <T> T firstSpan(Editable editable, int start, int end, Class<T> type) {
        List<T> spans = editable.getSpans(start, end, type);
        return spans.isEmpty() ? null : spans.get(0);
    }

    private static void applySegmentStyle(SpannableStringBuilder builder, int start, int end, Map<String, Object> style) {
        if (start >= end || style == null || style.isEmpty()) {
            return;
        }
        int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        boolean bold = truthy(style.get("bold"));
        boolean italic = truthy(style.get("italic"));
        if (bold && italic) {
            builder.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), start, end, flags);
        } else if (bold) {
            builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, flags);
        } else if (italic) {
            builder.setSpan(new StyleSpan(Typeface.ITALIC), start, end, flags);
        }
        Object color = style.get("color");
        if (color != null) {
            builder.setSpan(new ForegroundColorSpan(parseColor(color, 0xFFFFFFFF)), start, end, flags);
        }
        if (truthy(style.get("underlined")) || truthy(style.get("underline"))) {
            builder.setSpan(new UnderlineSpan(), start, end, flags);
        }
        if (truthy(style.get("strikethrough"))) {
            builder.setSpan(new StrikethroughSpan(), start, end, flags);
        }
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string);
        }
        return false;
    }

    private static int parseColor(Object value, int fallback) {
        if (value instanceof Number number) {
            return 0xFF000000 | (number.intValue() & 0xFFFFFF);
        }
        if (!(value instanceof String string) || string.isBlank()) {
            return fallback;
        }
        String color = string.trim();
        return switch (color) {
            case "red" -> 0xFFFF5555;
            case "gold" -> 0xFFFFAA00;
            case "aqua" -> 0xFF55FFFF;
            case "green" -> 0xFF55FF55;
            case "yellow" -> 0xFFFFFF55;
            case "white" -> 0xFFFFFFFF;
            case "gray" -> 0xFFAAAAAA;
            default -> parseHexColor(color, fallback);
        };
    }

    private int selectedColor(int start, int end) {
        ForegroundColorSpan color = firstSpan(editor.getText(), start, end, ForegroundColorSpan.class);
        return color != null ? color.getForegroundColor() : 0xFFFFFFFF;
    }

    private static int parseHexColor(String color, int fallback) {
        String hex = color.startsWith("#") ? color.substring(1) : color;
        try {
            return 0xFF000000 | (Integer.parseUnsignedInt(hex, 16) & 0xFFFFFF);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static TextView button(Context context, String text, int color, View.OnClickListener listener) {
        TextView view = label(context, text, 13.0f, 0xFFFFFFFF, Gravity.CENTER);
        view.setBackground(rect(color, 4.0f, 1, 0x553C4658));
        view.setOnClickListener(listener);
        return view;
    }

    private record LatexSpan(String source, String display) {
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
