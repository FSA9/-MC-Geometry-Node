package com.mine.geometry_node.client.ui.window;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.common.VectorIconView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ToolWindowStripe<T extends ToolWindowEntry> extends LinearLayout {
    private static final float STRIPE_WIDTH = 42.0f;
    private static final float ITEM_HEIGHT = 36.0f;
    private static final float ITEM_MARGIN_TOP = 4.0f;
    private static final float STRIPE_PADDING_TOP = 6.0f;

    private static final int COLOR_BG = UIConstants.CLR_BG_DARK_3;
    private static final int COLOR_ICON_NORMAL = UIConstants.CLR_GRAY_TEXT;
    private static final int COLOR_ICON_HOVER = UIConstants.CLR_WHITE;
    private static final int COLOR_ICON_SELECTED = UIConstants.CLR_WHITE;
    private static final int COLOR_BG_HOVER = UIConstants.CLR_BG_DARK_4;
    private static final int COLOR_BG_SELECTED = UIConstants.CLR_BG_DARK_5;
    private static final int COLOR_BORDER_SELECTED = UIConstants.ViewPort.Selection.CLR_BORDER;
    private static final int COLOR_BORDER_HOVER = UIConstants.CLR_SEARCH_BG;
    private static final int COLOR_ACCENT = UIConstants.ViewPort.Selection.CLR_BORDER;
    private static final int COLOR_HIGHLIGHT = UIConstants.CLR_HOVER_WHITE;

    public interface OnToolWindowSelectedListener<T extends ToolWindowEntry> {
        void onSelected(T type);
    }

    private final Map<T, ToolWindowTabView> mTabViews = new HashMap<>();
    private OnToolWindowSelectedListener<T> mListener;
    private T mCurrentSelectedType = null;

    public ToolWindowStripe(Context context, T[] types) {
        super(context);
        setOrientation(VERTICAL);
        setBackground(createColorDrawable(COLOR_BG));
        setPadding(0, UIUtils.dp2pxInt(STRIPE_PADDING_TOP), 0, 0);
        setLayoutParams(new ViewGroup.LayoutParams(
                UIUtils.dp2pxInt(STRIPE_WIDTH),
                ViewGroup.LayoutParams.MATCH_PARENT));

        for (T type : types) {
            ToolWindowTabView tabBtn = createTabButton(context, type);
            mTabViews.put(type, tabBtn);

            LayoutParams params = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    UIUtils.dp2pxInt(ITEM_HEIGHT));
            params.setMargins(0, UIUtils.dp2pxInt(ITEM_MARGIN_TOP), 0, 0);
            addView(tabBtn, params);
        }
    }

    public void setOnToolWindowSelectedListener(OnToolWindowSelectedListener<T> listener) {
        mListener = listener;
    }

    public void selectTab(T type) {
        if (Objects.equals(mCurrentSelectedType, type)) {
            return;
        }

        mCurrentSelectedType = type;
        updateTabsUI();

        if (mListener != null) {
            mListener.onSelected(type);
        }
    }

    private ToolWindowTabView createTabButton(Context context, T type) {
        ToolWindowTabView btn = new ToolWindowTabView(context, type.getIconKind());

        btn.setOnHoverListener((v, event) -> {
            if (!Objects.equals(mCurrentSelectedType, type)) {
                if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                    btn.setToolHovered(true);
                } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                    btn.setToolHovered(false);
                }
            }
            return true;
        });

        btn.setOnClickListener(v -> selectTab(type));
        return btn;
    }

    private void updateTabsUI() {
        for (Map.Entry<T, ToolWindowTabView> entry : mTabViews.entrySet()) {
            T type = entry.getKey();
            ToolWindowTabView view = entry.getValue();
            view.setSelectedState(Objects.equals(type, mCurrentSelectedType));
        }
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private static final class ToolWindowTabView extends VectorIconView {
        private final Paint mPaint = new Paint();
        private final RectF mRect = new RectF();
        private boolean mSelected;
        private boolean mHovered;

        private ToolWindowTabView(Context context, VectorIconView.Kind kind) {
            super(context, kind, COLOR_ICON_NORMAL);
            mPaint.setAntiAlias(true);
        }

        void setToolHovered(boolean hovered) {
            if (mHovered == hovered) {
                return;
            }
            mHovered = hovered;
            updateIconColor();
            invalidate();
        }

        void setSelectedState(boolean selected) {
            if (mSelected == selected) {
                return;
            }
            mSelected = selected;
            if (selected) {
                mHovered = false;
            }
            updateIconColor();
            invalidate();
        }

        private void updateIconColor() {
            if (mSelected) {
                setIconColor(COLOR_ICON_SELECTED);
            } else if (mHovered) {
                setIconColor(COLOR_ICON_HOVER);
            } else {
                setIconColor(COLOR_ICON_NORMAL);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float insetX = UIUtils.dp2px(4.0f);
            float insetY = UIUtils.dp2px(2.0f);
            float radius = UIUtils.dp2px(7.0f);
            float stroke = Math.max(1.0f, UIUtils.dp2px(1.0f));

            if (mSelected || mHovered) {
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setAlpha(255);
                mPaint.setColor(mSelected ? COLOR_BG_SELECTED : COLOR_BG_HOVER);
                mRect.set(insetX, insetY, w - insetX, h - insetY);
                canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

                mPaint.setColor(COLOR_HIGHLIGHT);
                mRect.set(insetX + stroke, insetY + stroke, w - insetX - stroke, insetY + h * 0.36f);
                canvas.drawRoundRect(mRect, radius * 0.8f, radius * 0.8f, radius * 0.8f, radius * 0.8f, mPaint);

                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(stroke);
                mPaint.setColor(mSelected ? COLOR_BORDER_SELECTED : COLOR_BORDER_HOVER);
                mRect.set(insetX + stroke * 0.5f, insetY + stroke * 0.5f, w - insetX - stroke * 0.5f, h - insetY - stroke * 0.5f);
                canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);
            }

            if (mSelected) {
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(COLOR_ACCENT);
                mPaint.setAlpha(255);
                float accentW = Math.max(2.0f, UIUtils.dp2px(3.0f));
                mRect.set(0, h * 0.24f, accentW, h * 0.76f);
                canvas.drawRoundRect(mRect, accentW, accentW, accentW, accentW, mPaint);
            }

            mPaint.setAlpha(255);
            super.onDraw(canvas);
        }
    }
}
