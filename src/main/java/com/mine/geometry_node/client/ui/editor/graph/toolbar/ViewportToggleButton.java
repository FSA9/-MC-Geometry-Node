package com.mine.geometry_node.client.ui.editor.graph.toolbar;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;

final class ViewportToggleButton extends ViewportToolButton {
    private static final int COLOR_BG_OFF = UiThemes.EDITOR.control().icon();
    private static final int COLOR_BORDER_OFF = UiThemes.EDITOR.border().subtle();
    private static final int COLOR_ICON_OFF = UiThemes.EDITOR.text().muted();
    private static final int COLOR_HIGHLIGHT = UiThemes.EDITOR.control().hoverOverlay();

    private final Paint mPaint = new Paint();
    private final RectF mRect = new RectF();
    private final ToggleStyle mStyle;
    private final IconPainter mIconPainter;

    private ViewportToggleButton(Context context, TooltipHost tooltipHost, ToggleStyle style, IconPainter iconPainter) {
        super(context, "", tooltipHost);
        this.mStyle = style;
        this.mIconPainter = iconPainter;
        mPaint.setAntiAlias(true);
    }

    static ViewportToggleButton createSnap(Context context, TooltipHost tooltipHost) {
        return new ViewportToggleButton(
                context,
                tooltipHost,
                new ToggleStyle(UiThemes.EDITOR.selection().fill(), UiThemes.EDITOR.selection().outline(), UiThemes.EDITOR.text().strong()),
                ViewportToggleButton::drawSnapIcon
        );
    }

    static ViewportToggleButton createGridVisibility(Context context, TooltipHost tooltipHost) {
        ViewportToggleButton button = new ViewportToggleButton(
                context,
                tooltipHost,
                new ToggleStyle(UiThemes.EDITOR.selection().fill(), UiThemes.EDITOR.selection().outline(), UiThemes.EDITOR.text().strong()),
                ViewportToggleButton::drawGridVisibilityIcon
        );
        button.setChecked(true);
        return button;
    }

    void setChecked(boolean checked) {
        setToolActive(checked);
    }

    boolean isChecked() {
        return isToolActive();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        boolean active = isToolActive();
        boolean hovered = isToolHovered();
        float minSize = Math.min(w, h);
        float pad = Math.max(1.0f, minSize * 0.08f);
        float stroke = Math.max(1.0f, UIUtils.dp2px(1.0f));
        float left = pad;
        float top = pad;
        float right = w - pad;
        float bottom = h - pad;
        float radius = minSize * 0.22f;

        mPaint.setStyle(Paint.Style.FILL);
        int background = active ? mStyle.mBackgroundOn : COLOR_BG_OFF;
        if (isEnabled()) {
            if (isPressed()) background = active ? UiThemes.EDITOR.selection().pressed() : UiThemes.EDITOR.control().iconPressed();
            else if (hovered) background = active ? UiThemes.EDITOR.selection().hover() : UiThemes.EDITOR.control().iconHover();
        }
        mPaint.setColor(background);
        mRect.set(left, top, right, bottom);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

        if (isEnabled() && (active || hovered)) {
            mPaint.setColor(COLOR_HIGHLIGHT);
            mRect.set(left + stroke, top + stroke, right - stroke, top + minSize * 0.38f);
            canvas.drawRoundRect(mRect, radius * 0.8f, radius * 0.8f, radius * 0.8f, radius * 0.8f, mPaint);
        }

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(stroke);
        mPaint.setColor(isEnabled() && isFocused() ? UiThemes.EDITOR.border().focus()
                : active ? mStyle.mBorderOn : (hovered ? COLOR_ICON_OFF : COLOR_BORDER_OFF));
        mRect.set(left + stroke * 0.5f, top + stroke * 0.5f, right - stroke * 0.5f, bottom - stroke * 0.5f);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

        mIconPainter.draw(canvas, mPaint, left, top, right, bottom, minSize, active, hovered, mStyle.mIconOn);
        mPaint.setAlpha(255);
    }

    private static void drawSnapIcon(
            Canvas canvas,
            Paint paint,
            float left,
            float top,
            float right,
            float bottom,
            float minSize,
            boolean active,
            boolean hovered,
            int iconOnColor
    ) {
        paint.setColor(active ? iconOnColor : COLOR_ICON_OFF);
        paint.setAlpha(active ? 230 : (hovered ? 190 : 145));
        paint.setStrokeWidth(Math.max(1.0f, minSize * 0.055f));
        float iconInset = minSize * 0.28f;
        float iconLeft = left + iconInset;
        float iconTop = top + iconInset;
        float iconRight = right - iconInset;
        float iconBottom = bottom - iconInset;
        float thirdW = (iconRight - iconLeft) / 3.0f;
        float thirdH = (iconBottom - iconTop) / 3.0f;
        for (int i = 1; i < 3; i++) {
            float x = iconLeft + thirdW * i;
            float y = iconTop + thirdH * i;
            canvas.drawLine(x, iconTop, x, iconBottom, paint);
            canvas.drawLine(iconLeft, y, iconRight, y, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? UiThemes.EDITOR.text().strong() : UiThemes.EDITOR.text().hint());
        paint.setAlpha(255);
        float pointSize = Math.max(1.0f, minSize * (active ? 0.16f : 0.12f));
        float cx = (iconLeft + iconRight) * 0.5f;
        float cy = (iconTop + iconBottom) * 0.5f;
        canvas.drawRect(cx - pointSize * 0.5f, cy - pointSize * 0.5f, cx + pointSize * 0.5f, cy + pointSize * 0.5f, paint);
    }

    private static void drawGridVisibilityIcon(
            Canvas canvas,
            Paint paint,
            float left,
            float top,
            float right,
            float bottom,
            float minSize,
            boolean active,
            boolean hovered,
            int iconOnColor
    ) {
        float iconInset = minSize * 0.27f;
        float iconLeft = left + iconInset;
        float iconTop = top + iconInset;
        float iconRight = right - iconInset;
        float iconBottom = bottom - iconInset;
        float cx = (iconLeft + iconRight) * 0.5f;
        float cy = (iconTop + iconBottom) * 0.5f;

        paint.setColor(active ? iconOnColor : COLOR_ICON_OFF);
        paint.setAlpha(active ? 230 : (hovered ? 185 : 135));
        paint.setStrokeWidth(Math.max(1.0f, minSize * 0.055f));

        canvas.drawLine(iconLeft, iconTop, iconRight, iconTop, paint);
        canvas.drawLine(iconLeft, cy, iconRight, cy, paint);
        canvas.drawLine(iconLeft, iconBottom, iconRight, iconBottom, paint);
        canvas.drawLine(iconLeft, iconTop, iconLeft, iconBottom, paint);
        canvas.drawLine(cx, iconTop, cx, iconBottom, paint);
        canvas.drawLine(iconRight, iconTop, iconRight, iconBottom, paint);

        paint.setAlpha(active ? 255 : 160);
        paint.setStrokeWidth(Math.max(1.0f, minSize * 0.09f));
        canvas.drawLine(cx, iconTop, cx, iconBottom, paint);
        canvas.drawLine(iconLeft, cy, iconRight, cy, paint);
    }

    private interface IconPainter {
        void draw(
                Canvas canvas,
                Paint paint,
                float left,
                float top,
                float right,
                float bottom,
                float minSize,
                boolean active,
                boolean hovered,
                int iconOnColor
        );
    }

    private static final class ToggleStyle {
        private final int mBackgroundOn;
        private final int mBorderOn;
        private final int mIconOn;

        private ToggleStyle(int backgroundOn, int borderOn, int iconOn) {
            this.mBackgroundOn = backgroundOn;
            this.mBorderOn = borderOn;
            this.mIconOn = iconOn;
        }
    }
}
