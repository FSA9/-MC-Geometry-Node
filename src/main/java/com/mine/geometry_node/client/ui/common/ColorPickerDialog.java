package com.mine.geometry_node.client.ui.common;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

public final class ColorPickerDialog extends FrameLayout {
    public interface OnColorSelected {
        void onColorSelected(int argbColor);
    }

    private static final int WINDOW_W_DP = 380;
    private static final int PANEL_PADDING_DP = 14;
    private static final int FIELD_H_DP = 28;

    private static final int COLOR_DIM = 0x66000000;
    private static final int COLOR_WINDOW = 0xFF242832;
    private static final int COLOR_TITLE = 0xFF1C2029;
    private static final int COLOR_BORDER = 0xFF3C4658;
    private static final int COLOR_FIELD = 0xFF151820;
    private static final int COLOR_LABEL = 0xFFB8C0CC;
    private static final int COLOR_TEXT = 0xFFE8EDF6;
    private static final int COLOR_BUTTON = 0xFF3A3F4A;
    private static final int COLOR_PRIMARY = 0xFF3D638D;

    private final OnColorSelected mListener;
    private final View mReturnFocusTarget;
    private final LinearLayout mWindow;
    private final SaturationValueView mSaturationValueView;
    private final HueStripView mHueStripView;
    private final ColorPreviewView mPreviewView;
    private final TextView mHexView;
    private final EditText mRedInput;
    private final EditText mGreenInput;
    private final EditText mBlueInput;
    private final EditText mHueInput;
    private final EditText mSaturationInput;
    private final EditText mValueInput;

    private int mColor;
    private float mHue;
    private float mSaturation;
    private float mValue;
    private boolean mSyncing;
    private float mDragStartRawX;
    private float mDragStartRawY;
    private int mDragStartLeft;
    private int mDragStartTop;
    private boolean mDragging;
    private boolean mPointerDownInsideWindow;

    private ColorPickerDialog(Context context, int initialColor, View returnFocusTarget, OnColorSelected listener) {
        super(context);
        mListener = listener;
        mReturnFocusTarget = returnFocusTarget;
        setBackground(rect(COLOR_DIM, 0.0f, 0, 0));
        setFocusable(true);
        setFocusableInTouchMode(true);

        mWindow = new LinearLayout(context);
        mWindow.setOrientation(LinearLayout.VERTICAL);
        mWindow.setBackground(rect(COLOR_WINDOW, 6.0f, 1, COLOR_BORDER));
        mWindow.setOnClickListener(v -> {});

        TextView title = label(context, "调色盘", 14.0f, COLOR_TEXT, Gravity.CENTER_VERTICAL);
        title.setPadding(dpPx(12), 0, dpPx(8), 0);
        title.setBackground(rect(COLOR_TITLE, 6.0f, 0, 0));
        title.setOnTouchListener(this::onTitleBarTouch);
        mWindow.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpPx(34)));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpPx(PANEL_PADDING_DP), dpPx(12), dpPx(PANEL_PADDING_DP), dpPx(PANEL_PADDING_DP));

        LinearLayout pickerRow = new LinearLayout(context);
        pickerRow.setOrientation(LinearLayout.HORIZONTAL);

        mSaturationValueView = new SaturationValueView(context);
        pickerRow.addView(mSaturationValueView, new LinearLayout.LayoutParams(dpPx(196), dpPx(152)));

        LinearLayout rightColumn = new LinearLayout(context);
        rightColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightColumnLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        rightColumnLp.leftMargin = dpPx(12);
        pickerRow.addView(rightColumn, rightColumnLp);

        mPreviewView = new ColorPreviewView(context);
        rightColumn.addView(mPreviewView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpPx(58)));

        mHexView = label(context, "#FFFFFF", 13.0f, COLOR_TEXT, Gravity.CENTER);
        LinearLayout.LayoutParams hexLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpPx(28));
        hexLp.topMargin = dpPx(8);
        rightColumn.addView(mHexView, hexLp);

        TextView hueLabel = label(context, "Hue", 11.0f, COLOR_LABEL, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hueLabelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpPx(20));
        hueLabelLp.topMargin = dpPx(8);
        rightColumn.addView(hueLabel, hueLabelLp);

        mHueStripView = new HueStripView(context);
        rightColumn.addView(mHueStripView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpPx(28)));

        content.addView(pickerRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpPx(152)));

        mRedInput = field(context);
        mGreenInput = field(context);
        mBlueInput = field(context);
        LinearLayout.LayoutParams rgbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpPx(FIELD_H_DP));
        rgbLp.topMargin = dpPx(12);
        content.addView(channelRow(context, "RGB", new ChannelField("R", mRedInput),
                new ChannelField("G", mGreenInput), new ChannelField("B", mBlueInput)), rgbLp);

        mHueInput = field(context);
        mSaturationInput = field(context);
        mValueInput = field(context);
        LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpPx(FIELD_H_DP));
        hsvLp.topMargin = dpPx(8);
        content.addView(channelRow(context, "HSV", new ChannelField("H", mHueInput),
                new ChannelField("S", mSaturationInput), new ChannelField("V", mValueInput)), hsvLp);

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpPx(34));
        actionsLp.topMargin = dpPx(12);
        content.addView(actions, actionsLp);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(dpPx(82), dpPx(30));
        cancelLp.rightMargin = dpPx(8);
        actions.addView(button(context, "取消", COLOR_BUTTON, v -> dismiss()), cancelLp);
        actions.addView(button(context, "应用", COLOR_PRIMARY, v -> applyAndDismiss()), new LinearLayout.LayoutParams(dpPx(86), dpPx(30)));

        mWindow.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams windowLp = new FrameLayout.LayoutParams(dpPx(WINDOW_W_DP), ViewGroup.LayoutParams.WRAP_CONTENT);
        windowLp.gravity = Gravity.CENTER;
        addView(mWindow, windowLp);

        wireInputWatchers();
        setColor(ensureOpaque(initialColor), false);
        post(this::initializeWindowPosition);
        post(this::requestFocus);
    }

    public static ColorPickerDialog show(Context context, View anchor, int initialColor, OnColorSelected listener) {
        if (context == null || anchor == null) {
            return null;
        }
        ViewGroup host = findWindowHost(anchor);
        if (host == null) {
            return null;
        }
        ColorPickerDialog dialog = new ColorPickerDialog(context, initialColor, anchor, listener);
        addFullscreen(host, dialog);
        return dialog;
    }

    public static ColorPickerDialog showIn(ViewGroup parent, int initialColor, OnColorSelected listener) {
        if (parent == null) {
            return null;
        }
        ColorPickerDialog dialog = new ColorPickerDialog(parent.getContext(), initialColor, parent, listener);
        addFullscreen(parent, dialog);
        return dialog;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEY_ESCAPE) {
            dismiss();
            return true;
        }
        super.dispatchKeyEvent(event);
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        requestFocus();
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mPointerDownInsideWindow = isInsideWindow(event.getX(), event.getY());
        }

        if (mDragging && action != MotionEvent.ACTION_DOWN) {
            if (action == MotionEvent.ACTION_MOVE) {
                int targetLeft = mDragStartLeft + Math.round(event.getRawX() - mDragStartRawX);
                int targetTop = mDragStartTop + Math.round(event.getRawY() - mDragStartRawY);
                moveWindowTo(targetLeft, targetTop);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mPointerDownInsideWindow = false;
                mDragging = false;
            }
            return true;
        }

        if (mPointerDownInsideWindow || mDragging) {
            super.dispatchTouchEvent(event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mPointerDownInsideWindow = false;
                mDragging = false;
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP) {
            dismiss();
        } else if (action == MotionEvent.ACTION_CANCEL) {
            mPointerDownInsideWindow = false;
            mDragging = false;
        }
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (isInsideWindow(event.getX(), event.getY())) {
            super.dispatchGenericMotionEvent(event);
        }
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        requestFocus();
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return true;
    }

    public void dismiss() {
        if (getParent() instanceof ViewGroup parent) {
            parent.removeView(this);
        }
        if (mReturnFocusTarget != null) {
            mReturnFocusTarget.requestFocus();
        }
    }

    private void applyAndDismiss() {
        if (mListener != null) {
            mListener.onColorSelected(mColor);
        }
        dismiss();
    }

    private void wireInputWatchers() {
        TextWatcher rgbWatcher = watcher(this::setColorFromRgbInputs);
        mRedInput.addTextChangedListener(rgbWatcher);
        mGreenInput.addTextChangedListener(rgbWatcher);
        mBlueInput.addTextChangedListener(rgbWatcher);

        TextWatcher hsvWatcher = watcher(this::setColorFromHsvInputs);
        mHueInput.addTextChangedListener(hsvWatcher);
        mSaturationInput.addTextChangedListener(hsvWatcher);
        mValueInput.addTextChangedListener(hsvWatcher);
    }

    private TextWatcher watcher(Runnable afterChange) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!mSyncing) {
                    afterChange.run();
                }
            }
        };
    }

    private void setColorFromRgbInputs() {
        int r = parseInt(mRedInput, red(mColor), 0, 255);
        int g = parseInt(mGreenInput, green(mColor), 0, 255);
        int b = parseInt(mBlueInput, blue(mColor), 0, 255);
        setColorFromRgb(r, g, b, true);
    }

    private void setColorFromHsvInputs() {
        float h = parseInt(mHueInput, Math.round(mHue), 0, 360);
        float s = parseInt(mSaturationInput, Math.round(mSaturation * 100.0f), 0, 100) / 100.0f;
        float v = parseInt(mValueInput, Math.round(mValue * 100.0f), 0, 100) / 100.0f;
        setColorFromHsv(h, s, v, true);
    }

    private void setColorFromRgb(int red, int green, int blue, boolean skipFocusedField) {
        mColor = 0xFF000000 | (clamp(red, 0, 255) << 16) | (clamp(green, 0, 255) << 8) | clamp(blue, 0, 255);
        float[] hsv = rgbToHsv(red(mColor), green(mColor), blue(mColor));
        mHue = hsv[0];
        mSaturation = hsv[1];
        mValue = hsv[2];
        refresh(skipFocusedField);
    }

    private void setColorFromHsv(float hue, float saturation, float value, boolean skipFocusedField) {
        mHue = clamp(hue, 0.0f, 360.0f);
        mSaturation = clamp(saturation, 0.0f, 1.0f);
        mValue = clamp(value, 0.0f, 1.0f);
        mColor = hsvToRgb(mHue, mSaturation, mValue);
        refresh(skipFocusedField);
    }

    private void setColor(int color, boolean skipFocusedField) {
        mColor = ensureOpaque(color);
        float[] hsv = rgbToHsv(red(mColor), green(mColor), blue(mColor));
        mHue = hsv[0];
        mSaturation = hsv[1];
        mValue = hsv[2];
        refresh(skipFocusedField);
    }

    private void refresh(boolean skipFocusedField) {
        mSyncing = true;
        setFieldText(mRedInput, String.valueOf(red(mColor)), skipFocusedField);
        setFieldText(mGreenInput, String.valueOf(green(mColor)), skipFocusedField);
        setFieldText(mBlueInput, String.valueOf(blue(mColor)), skipFocusedField);
        setFieldText(mHueInput, String.valueOf(Math.round(mHue)), skipFocusedField);
        setFieldText(mSaturationInput, String.valueOf(Math.round(mSaturation * 100.0f)), skipFocusedField);
        setFieldText(mValueInput, String.valueOf(Math.round(mValue * 100.0f)), skipFocusedField);
        mSyncing = false;

        mHexView.setText(String.format("#%06X", mColor & 0xFFFFFF));
        mPreviewView.invalidate();
        mSaturationValueView.invalidate();
        mHueStripView.invalidate();
    }

    private void setFieldText(EditText input, String text, boolean skipFocusedField) {
        if (skipFocusedField && input.hasFocus()) {
            return;
        }
        if (!text.equals(input.getText().toString())) {
            input.setText(text);
            input.setSelection(input.getText().length());
        }
    }

    private LinearLayout channelRow(Context context, String title, ChannelField first, ChannelField second, ChannelField third) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView rowTitle = label(context, title, 12.0f, COLOR_LABEL, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(rowTitle, new LinearLayout.LayoutParams(dpPx(42), ViewGroup.LayoutParams.MATCH_PARENT));
        addChannel(row, context, first);
        addChannel(row, context, second);
        addChannel(row, context, third);
        return row;
    }

    private void addChannel(LinearLayout row, Context context, ChannelField channel) {
        TextView channelLabel = label(context, channel.label(), 11.0f, COLOR_LABEL, Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(dpPx(18), ViewGroup.LayoutParams.MATCH_PARENT);
        labelLp.leftMargin = dpPx(4);
        row.addView(channelLabel, labelLp);
        row.addView(channel.input(), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
    }

    private EditText field(Context context) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setTextColor(COLOR_TEXT);
        input.setTextSize(12.0f);
        input.setGravity(Gravity.CENTER);
        input.setPadding(dpPx(5), 0, dpPx(5), 0);
        input.setBackground(rect(COLOR_FIELD, 4.0f, 1, 0xFF303846));
        return input;
    }

    private TextView button(Context context, String text, int color, View.OnClickListener listener) {
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
            drawable.setStroke(dpPx(strokeWidthDp), strokeColor);
        }
        return drawable;
    }

    private static void addFullscreen(ViewGroup host, ColorPickerDialog dialog) {
        if (host instanceof FrameLayout) {
            host.addView(dialog, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            host.addView(dialog, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private boolean onTitleBarTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                requestFocus();
                mDragStartRawX = event.getRawX();
                mDragStartRawY = event.getRawY();
                FrameLayout.LayoutParams downLp = (FrameLayout.LayoutParams) mWindow.getLayoutParams();
                ensureWindowHasAbsolutePosition(downLp);
                mDragStartLeft = downLp.leftMargin;
                mDragStartTop = downLp.topMargin;
                mDragging = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mDragging) return true;
                int targetLeft = mDragStartLeft + Math.round(event.getRawX() - mDragStartRawX);
                int targetTop = mDragStartTop + Math.round(event.getRawY() - mDragStartRawY);
                moveWindowTo(targetLeft, targetTop);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDragging = false;
                return true;
            default:
                return true;
        }
    }

    private void ensureWindowHasAbsolutePosition(FrameLayout.LayoutParams lp) {
        if (lp.gravity == (Gravity.TOP | Gravity.LEFT)) {
            return;
        }
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = mWindow.getLeft();
        lp.topMargin = mWindow.getTop();
        mWindow.setLayoutParams(lp);
    }

    private void initializeWindowPosition() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mWindow.getLayoutParams();
        ensureWindowHasAbsolutePosition(lp);
        moveWindowTo(lp.leftMargin, lp.topMargin);
    }

    private void moveWindowTo(int left, int top) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mWindow.getLayoutParams();
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = clamp(left, 0, Math.max(0, getWidth() - mWindow.getWidth()));
        lp.topMargin = clamp(top, 0, Math.max(0, getHeight() - mWindow.getHeight()));
        mWindow.setLayoutParams(lp);
    }

    private boolean isInsideWindow(float x, float y) {
        return x >= mWindow.getLeft() && x < mWindow.getRight()
                && y >= mWindow.getTop() && y < mWindow.getBottom();
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
        if (best != null) {
            return best;
        }
        return anchor.getParent() instanceof ViewGroup parent ? parent : null;
    }

    private int parseInt(EditText input, int fallback, int min, int max) {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            return clamp(Integer.parseInt(text), min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int ensureOpaque(int color) {
        return color | 0xFF000000;
    }

    private static int red(int color) {
        return (color >>> 16) & 0xFF;
    }

    private static int green(int color) {
        return (color >>> 8) & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int dpPx(float value) {
        return UIUtils.dp2pxInt(value);
    }

    private static float[] rgbToHsv(int red, int green, int blue) {
        float r = red / 255.0f;
        float g = green / 255.0f;
        float b = blue / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue;
        if (delta == 0.0f) {
            hue = 0.0f;
        } else if (max == r) {
            hue = 60.0f * (((g - b) / delta) % 6.0f);
        } else if (max == g) {
            hue = 60.0f * (((b - r) / delta) + 2.0f);
        } else {
            hue = 60.0f * (((r - g) / delta) + 4.0f);
        }
        if (hue < 0.0f) {
            hue += 360.0f;
        }
        float saturation = max == 0.0f ? 0.0f : delta / max;
        return new float[]{hue, saturation, max};
    }

    private static int hsvToRgb(float hue, float saturation, float value) {
        float normalizedHue = ((hue % 360.0f) + 360.0f) % 360.0f;
        float c = value * saturation;
        float x = c * (1.0f - Math.abs((normalizedHue / 60.0f) % 2.0f - 1.0f));
        float m = value - c;
        float r1;
        float g1;
        float b1;
        if (normalizedHue < 60.0f) {
            r1 = c;
            g1 = x;
            b1 = 0.0f;
        } else if (normalizedHue < 120.0f) {
            r1 = x;
            g1 = c;
            b1 = 0.0f;
        } else if (normalizedHue < 180.0f) {
            r1 = 0.0f;
            g1 = c;
            b1 = x;
        } else if (normalizedHue < 240.0f) {
            r1 = 0.0f;
            g1 = x;
            b1 = c;
        } else if (normalizedHue < 300.0f) {
            r1 = x;
            g1 = 0.0f;
            b1 = c;
        } else {
            r1 = c;
            g1 = 0.0f;
            b1 = x;
        }
        int r = clamp(Math.round((r1 + m) * 255.0f), 0, 255);
        int g = clamp(Math.round((g1 + m) * 255.0f), 0, 255);
        int b = clamp(Math.round((b1 + m) * 255.0f), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private record ChannelField(String label, EditText input) {
    }

    private final class ColorPreviewView extends View {
        private final Paint mPaint = new Paint();

        private ColorPreviewView(Context context) {
            super(context);
            setWillNotDraw(false);
            mPaint.setAntiAlias(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float radius = UIUtils.dp2px(5.0f);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(mColor);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, radius, radius, mPaint);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(UIUtils.dp2px(1.0f));
            mPaint.setColor(0xAAFFFFFF);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, radius, radius, mPaint);
        }
    }

    private final class SaturationValueView extends View {
        private final Paint mPaint = new Paint();

        private SaturationValueView(Context context) {
            super(context);
            setWillNotDraw(false);
            mPaint.setAntiAlias(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = Math.max(1, getWidth());
            int height = Math.max(1, getHeight());
            int step = Math.max(1, dpPx(2));
            mPaint.setStyle(Paint.Style.FILL);
            for (int y = 0; y < height; y += step) {
                float value = 1.0f - clamp((float) y / Math.max(1.0f, height - 1.0f), 0.0f, 1.0f);
                for (int x = 0; x < width; x += step) {
                    float saturation = clamp((float) x / Math.max(1.0f, width - 1.0f), 0.0f, 1.0f);
                    mPaint.setColor(hsvToRgb(mHue, saturation, value));
                    canvas.drawRect(x, y, Math.min(width, x + step), Math.min(height, y + step), mPaint);
                }
            }

            float markerX = mSaturation * width;
            float markerY = (1.0f - mValue) * height;
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(UIUtils.dp2px(2.0f));
            mPaint.setColor(UIConstants.CLR_WHITE);
            canvas.drawCircle(markerX, markerY, UIUtils.dp2px(5.0f), mPaint);
            mPaint.setStrokeWidth(UIUtils.dp2px(1.0f));
            mPaint.setColor(0xFF000000);
            canvas.drawCircle(markerX, markerY, UIUtils.dp2px(6.5f), mPaint);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            return onTouchEvent(event);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                float saturation = clamp(event.getX() / Math.max(1.0f, getWidth()), 0.0f, 1.0f);
                float value = 1.0f - clamp(event.getY() / Math.max(1.0f, getHeight()), 0.0f, 1.0f);
                setColorFromHsv(mHue, saturation, value, true);
                return true;
            }
            return true;
        }
    }

    private final class HueStripView extends View {
        private final Paint mPaint = new Paint();

        private HueStripView(Context context) {
            super(context);
            setWillNotDraw(false);
            mPaint.setAntiAlias(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = Math.max(1, getWidth());
            int height = Math.max(1, getHeight());
            mPaint.setStyle(Paint.Style.FILL);
            for (int x = 0; x < width; x++) {
                float hue = (x / Math.max(1.0f, width - 1.0f)) * 360.0f;
                mPaint.setColor(hsvToRgb(hue, 1.0f, 1.0f));
                canvas.drawRect(x, 0, x + 1, height, mPaint);
            }

            float markerX = (mHue / 360.0f) * width;
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(UIUtils.dp2px(2.0f));
            mPaint.setColor(UIConstants.CLR_WHITE);
            canvas.drawLine(markerX, 0, markerX, height, mPaint);
            mPaint.setStrokeWidth(UIUtils.dp2px(1.0f));
            mPaint.setColor(0xFF000000);
            canvas.drawLine(markerX - 2, 0, markerX - 2, height, mPaint);
            canvas.drawLine(markerX + 2, 0, markerX + 2, height, mPaint);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            return onTouchEvent(event);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                float hue = clamp(event.getX() / Math.max(1.0f, getWidth()), 0.0f, 1.0f) * 360.0f;
                setColorFromHsv(hue, mSaturation, mValue, true);
                return true;
            }
            return true;
        }
    }
}
