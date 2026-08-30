package com.mine.geometry_node.client.ui.workspace.area;

import com.mine.geometry_node.client.ui.components.common.SvgIconView;
import com.mine.geometry_node.client.ui.components.common.VectorIconView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;

final class AreaIconButton extends FrameLayout {
    interface HintSink {
        void showButtonHint(AreaIconButton button, String hint);

        void clearButtonHint(AreaIconButton button);
    }

    private final Paint mPaint = new Paint();
    private final RectF mRect = new RectF();
    private final View mIconView;
    private final String mHint;
    private final HintSink mHintSink;
    private boolean mHovered;
    private boolean mSelected;

    AreaIconButton(Context context, SvgIconView.Icon icon, String hint, HintSink hintSink) {
        this(context, new SvgIconView(context, icon, AreaStyle.COLOR_ICON), hint, hintSink);
    }

    AreaIconButton(Context context, VectorIconView.Kind kind, String hint, HintSink hintSink) {
        this(context, new VectorIconView(context, kind, AreaStyle.COLOR_ICON), hint, hintSink);
    }

    private AreaIconButton(Context context, View iconView, String hint, HintSink hintSink) {
        super(context);
        mIconView = iconView;
        mHint = hint;
        mHintSink = hintSink;
        mPaint.setAntiAlias(true);
        setWillNotDraw(false);
        addView(iconView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                updateHovered(true);
                if (mHintSink != null) {
                    mHintSink.showButtonHint(this, mHint);
                }
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                updateHovered(false);
                if (mHintSink != null) {
                    mHintSink.clearButtonHint(this);
                }
            }
            return true;
        });
    }

    void setSelectedState(boolean selected) {
        if (mSelected == selected) {
            return;
        }
        mSelected = selected;
        setIconColor(mSelected ? AreaStyle.COLOR_ICON_SELECTED : AreaStyle.COLOR_ICON);
        invalidate();
    }

    void setIcon(SvgIconView.Icon icon) {
        if (mIconView instanceof SvgIconView svgIconView) {
            svgIconView.setIcon(icon);
        }
    }

    private void updateHovered(boolean hovered) {
        if (mHovered == hovered) {
            return;
        }
        mHovered = hovered;
        if (!mSelected) {
            setIconColor(mHovered ? AreaStyle.COLOR_ICON_SELECTED : AreaStyle.COLOR_ICON);
        }
        invalidate();
    }

    private void setIconColor(int color) {
        if (mIconView instanceof SvgIconView svgIconView) {
            svgIconView.setIconColor(color);
        } else if (mIconView instanceof VectorIconView vectorIconView) {
            vectorIconView.setIconColor(color);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float inset = UIUtils.dp2px(1.0f);
        float radius = Math.min(w, h) * 0.22f;

        if (mSelected || mHovered) {
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setAlpha(255);
            mPaint.setColor(mSelected ? AreaStyle.COLOR_BUTTON_BG_SELECTED : AreaStyle.COLOR_BUTTON_BG_HOVER);
            mRect.set(inset, inset, w - inset, h - inset);
            canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(Math.max(1.0f, UIUtils.dp2px(1.0f)));
            mPaint.setColor(mSelected ? AreaStyle.COLOR_ACCENT : AreaStyle.COLOR_BUTTON_BORDER);
            mRect.set(inset + 0.5f, inset + 0.5f, w - inset - 0.5f, h - inset - 0.5f);
            canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);
        }

        super.onDraw(canvas);
    }
}
