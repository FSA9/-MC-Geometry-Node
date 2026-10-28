package com.mine.geometry_node.client.ui.editor.graph.toolbar;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
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
    private final SvgIconView mIcon;

    private ViewportToggleButton(Context context, TooltipHost tooltipHost, ToggleStyle style, SvgIconView.Icon icon) {
        super(context, "", tooltipHost);
        this.mStyle = style;
        this.mIcon = new SvgIconView(context, icon, COLOR_ICON_OFF);
        mPaint.setAntiAlias(true);
    }

    static ViewportToggleButton createSnap(Context context, TooltipHost tooltipHost) {
        return new ViewportToggleButton(
                context,
                tooltipHost,
                new ToggleStyle(UiThemes.EDITOR.selection().fill(), UiThemes.EDITOR.selection().outline(), UiThemes.EDITOR.text().strong()),
                SvgIconView.Icon.SNAP
        );
    }

    static ViewportToggleButton createGridVisibility(Context context, TooltipHost tooltipHost) {
        ViewportToggleButton button = new ViewportToggleButton(
                context,
                tooltipHost,
                new ToggleStyle(UiThemes.EDITOR.selection().fill(), UiThemes.EDITOR.selection().outline(), UiThemes.EDITOR.text().strong()),
                SvgIconView.Icon.GRID
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
        drawChrome(canvas);
        int size = Math.round(Math.min(getWidth(), getHeight()) * 0.5f);
        mIcon.setIconColor(isToolActive() ? mStyle.mIconOn : COLOR_ICON_OFF);
        mIcon.drawIcon(canvas, (getWidth() - size) * 0.5f, (getHeight() - size) * 0.5f, size);
    }

    void drawChrome(Canvas canvas) {
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
