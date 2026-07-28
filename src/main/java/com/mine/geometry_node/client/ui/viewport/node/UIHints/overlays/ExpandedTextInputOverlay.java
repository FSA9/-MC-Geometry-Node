package com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays;

import com.mine.geometry_node.client.dialogue.ui.DialogueHudTheme;
import com.mine.geometry_node.client.ui.common.ColorPickerDialog;
import com.mine.geometry_node.client.ui.UICommand.EditorContext;
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
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExpandedTextInputOverlay extends FrameLayout {
    private static final int COLOR_DIM = DialogueHudTheme.OVERLAY_DIM;
    private static final int COLOR_WINDOW = DialogueHudTheme.PANEL;
    private static final int COLOR_SURFACE = DialogueHudTheme.SURFACE;
    private static final int COLOR_FIELD = DialogueHudTheme.withAlpha(DialogueHudTheme.PANEL, 0xFF);
    private static final int COLOR_BORDER = DialogueHudTheme.DIVIDER;
    private static final int COLOR_FIELD_BORDER = DialogueHudTheme.withAlpha(DialogueHudTheme.TEXT_MUTED, 0x44);
    private static final int COLOR_TEXT = DialogueHudTheme.TEXT_PRIMARY;
    private static final int COLOR_BUTTON = DialogueHudTheme.BUTTON;
    private static final int COLOR_BUTTON_HOVER = DialogueHudTheme.BUTTON_HOVER;
    private static final int COLOR_BUTTON_PRESSED = DialogueHudTheme.BUTTON_PRESSED;
    private static final int COLOR_PRIMARY = DialogueHudTheme.ACCENT;
    private static final int COLOR_PRIMARY_HOVER = DialogueHudTheme.ACCENT_HOVER;
    private static final int COLOR_PRIMARY_PRESSED = DialogueHudTheme.ACCENT_PRESSED;
    private static final int WINDOW_MARGIN_DP = 34;

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
        UIUtils.syncFixedDensity();

        setBackground(rect(COLOR_DIM, 0.0f, 0, 0));
        setOnClickListener(v -> dismiss());
        setFocusable(true);
        setFocusableInTouchMode(true);

        LinearLayout window = new LinearLayout(context);
        window.setOrientation(LinearLayout.VERTICAL);
        window.setPadding(dp(18), dp(14), dp(18), dp(14));
        window.setBackground(rect(COLOR_WINDOW, 3.0f, 1, COLOR_BORDER));
        window.setOnClickListener(v -> {
        });

        window.addView(createHeader(context), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        window.addView(divider(context), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        editor = new EditText(context);
        if (richTextMode) {
            editor.setText(toEditable(RichTextValue.from(value)), TextView.BufferType.EDITABLE);
        } else {
            editor.setText(value == null ? "" : value.toString());
        }
        editor.setTextColor(COLOR_TEXT);
        UIUtils.setLockedTextSize(editor, 14.0f);
        editor.setGravity(Gravity.LEFT | Gravity.TOP);
        editor.setSingleLine(false);
        editor.setMinLines(12);
        editor.setPadding(dp(14), dp(12), dp(14), dp(12));
        editor.setBackground(rect(COLOR_FIELD, 2.0f, 1, COLOR_FIELD_BORDER));
        editor.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ESCAPE) {
                dismiss();
                return true;
            }
            return false;
        });
        LinearLayout workspace = new LinearLayout(context);
        workspace.setOrientation(LinearLayout.HORIZONTAL);
        workspace.addView(editor, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        if (richTextMode) {
            LinearLayout.LayoutParams toolbarParams = new LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.MATCH_PARENT);
            toolbarParams.leftMargin = dp(10);
            workspace.addView(createRichTextToolbar(context), toolbarParams);
        }

        LinearLayout.LayoutParams workspaceParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        workspaceParams.topMargin = dp(12);
        workspaceParams.bottomMargin = dp(12);
        window.addView(workspace, workspaceParams);

        window.addView(divider(context), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        window.addView(createActions(context), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        FrameLayout.LayoutParams windowParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        windowParams.leftMargin = dp(WINDOW_MARGIN_DP);
        windowParams.rightMargin = dp(WINDOW_MARGIN_DP);
        windowParams.topMargin = dp(WINDOW_MARGIN_DP);
        windowParams.bottomMargin = dp(WINDOW_MARGIN_DP);
        addView(window, windowParams);
    }

    private View createHeader(Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        View marker = new View(context);
        marker.setBackground(rect(COLOR_PRIMARY, 1.0f, 0, 0));
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(3), dp(26));
        markerParams.rightMargin = dp(11);
        header.addView(marker, markerParams);

        String titleKey = richTextMode
                ? "geometry_node.rich_text_editor.title"
                : "geometry_node.text_editor.title";
        TextView title = label(context, tr(titleKey), 15.0f, COLOR_TEXT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        return header;
    }

    private View createActions(Context context) {
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(8), 0, 0);

        actions.addView(actionButton(context, tr("geometry_node.common.cancel"), false, v -> dismiss()),
                new LinearLayout.LayoutParams(dp(82), dp(32)));
        TextView spacer = label(context, "", 1.0f, 0, Gravity.CENTER);
        actions.addView(spacer, new LinearLayout.LayoutParams(dp(10), 1));
        actions.addView(actionButton(context, tr("geometry_node.common.save"), true, v -> commit()),
                new LinearLayout.LayoutParams(dp(90), dp(32)));
        return actions;
    }

    public static void show(Context context, View anchor, EditorContext editorContext, NodeData nodeData, String portId,
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
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        toolbar.setPadding(dp(5), dp(6), dp(5), dp(6));
        toolbar.setBackground(rect(COLOR_SURFACE, 2.0f, 1, COLOR_FIELD_BORDER));

        addToolButton(toolbar, toolButton(context, "B", "geometry_node.rich_text_editor.bold", v -> applyStyle("bold", true)));
        addToolButton(toolbar, toolButton(context, "I", "geometry_node.rich_text_editor.italic", v -> applyStyle("italic", true)));
        addToolButton(toolbar, toolButton(context, "U", "geometry_node.rich_text_editor.underline", v -> applyStyle("underlined", true)));
        addToolButton(toolbar, toolButton(context, "S", "geometry_node.rich_text_editor.strikethrough", v -> applyStyle("strikethrough", true)));

        View separator = divider(context);
        LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(dp(20), dp(1));
        separatorParams.gravity = Gravity.CENTER_HORIZONTAL;
        separatorParams.topMargin = dp(3);
        separatorParams.bottomMargin = dp(8);
        toolbar.addView(separator, separatorParams);

        TextView color = toolButton(context, "A", "geometry_node.rich_text_editor.color", v -> showColorPicker());
        color.setTextColor(COLOR_PRIMARY);
        addToolButton(toolbar, color);
        addToolButton(toolbar, toolButton(context, "fx", "geometry_node.rich_text_editor.latex", v -> applyLatex()));
        addToolButton(toolbar, toolButton(context, "Tx", "geometry_node.rich_text_editor.clear_formatting", v -> clearSelectionStyles()));
        return toolbar;
    }

    private void addToolButton(LinearLayout toolbar, TextView button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(28), dp(28));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = dp(5);
        toolbar.addView(button, params);
    }

    private TextView toolButton(Context context, String text, String tooltipKey, View.OnClickListener listener) {
        TextView view = interactiveButton(context, text, 11.5f, COLOR_TEXT,
                COLOR_BUTTON, COLOR_BUTTON_HOVER, COLOR_BUTTON_PRESSED, listener);
        view.setTooltipText(tr(tooltipKey));
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
        editable.setSpan(new ForegroundColorSpan(COLOR_PRIMARY), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
                builder.setSpan(new ForegroundColorSpan(COLOR_PRIMARY), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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

    private static TextView actionButton(Context context, String text, boolean primary, View.OnClickListener listener) {
        int textColor = primary ? 0xFF17191B : COLOR_TEXT;
        int normal = primary ? COLOR_PRIMARY : COLOR_BUTTON;
        int hover = primary ? COLOR_PRIMARY_HOVER : COLOR_BUTTON_HOVER;
        int pressed = primary ? COLOR_PRIMARY_PRESSED : COLOR_BUTTON_PRESSED;
        return interactiveButton(context, text, 12.5f, textColor, normal, hover, pressed, listener);
    }

    private static TextView interactiveButton(Context context, String text, float textSize, int textColor,
                                              int normalColor, int hoverColor, int pressedColor,
                                              View.OnClickListener listener) {
        TextView view = label(context, text, textSize, textColor, Gravity.CENTER);
        view.setBackground(rect(normalColor, 2.0f, 0, 0));
        view.setOnClickListener(listener);
        boolean[] hovered = {false};
        view.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                hovered[0] = true;
                v.setBackground(rect(hoverColor, 2.0f, 0, 0));
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                hovered[0] = false;
                v.setBackground(rect(normalColor, 2.0f, 0, 0));
            }
            return false;
        });
        view.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                v.setBackground(rect(pressedColor, 2.0f, 0, 0));
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                v.setBackground(rect(hovered[0] ? hoverColor : normalColor, 2.0f, 0, 0));
            }
            return false;
        });
        return view;
    }

    private record LatexSpan(String source, String display) {
    }

    private static TextView label(Context context, String text, float sizeDp, int color, int gravity) {
        TextView view = UIUtils.createLockedTextView(context, text, sizeDp, color);
        view.setGravity(gravity);
        return view;
    }

    private static View divider(Context context) {
        View divider = new View(context);
        divider.setBackground(rect(COLOR_BORDER, 0.0f, 0, 0));
        return divider;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
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
