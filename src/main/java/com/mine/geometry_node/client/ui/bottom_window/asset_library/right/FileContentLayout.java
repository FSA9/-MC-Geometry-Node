package com.mine.geometry_node.client.ui.bottom_window.asset_library.right;

import com.mine.geometry_node.client.ui.UIConstants;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.*;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2px;
import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2pxInt;

final class FileContentLayout extends ViewGroup {
    interface SelectionHost {
        void onContentRightClick(float rawX, float rawY);
        void onBoxSelection(RectF selectionRect, boolean additive, Set<String> baseSelection);
        void clearSelection();
        void requestContentFocus();
        Set<String> selectedPathsSnapshot();
        void disallowScrollIntercept(boolean disallow);
    }

    private final SelectionHost mHost;
    private final Paint mSelectionFillPaint = new Paint();
    private final Paint mSelectionBorderPaint = new Paint();
    private final RectF mSelectionRect = new RectF();
    private final Set<String> mSelectionBase = new LinkedHashSet<>();
    private AssetViewMode mMode = AssetViewMode.LIST;
    private boolean mSelecting = false;
    private boolean mSelectionAdditive = false;
    private float mSelectionStartX;
    private float mSelectionStartY;
    private float mDownX;
    private float mDownY;
    private int mMinimumContentHeight = 0;
    private final float mTouchSlop;

    FileContentLayout(Context context, SelectionHost host) {
        super(context);
        mHost = host;
        setWillNotDraw(false);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mSelectionFillPaint.setColor(UIConstants.ViewPort.Selection.CLR_FILL);
        mSelectionFillPaint.setStyle(Paint.Style.FILL);
        mSelectionBorderPaint.setColor(UIConstants.ViewPort.Selection.CLR_BORDER);
        mSelectionBorderPaint.setStyle(Paint.Style.STROKE);
        mSelectionBorderPaint.setStrokeWidth(dp2px(UIConstants.ViewPort.Selection.STROKE_WIDTH));
        setBackground(RightFileBrowserPanel.createColorDrawable(0xFF1E1E1E));
    }

    void setViewMode(AssetViewMode mode) {
        mMode = mode;
    }

    void setMinimumContentHeight(int height) {
        if (mMinimumContentHeight == height) return;
        mMinimumContentHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height;

        if (mMode == AssetViewMode.LIST) {
            int rowHeight = dp2pxInt(mMode.itemHeightDp);
            int childWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            int childHeightSpec = MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(childWidthSpec, childHeightSpec);
            }
            height = rowHeight * getChildCount();
        } else {
            int padding = dp2pxInt(8);
            int gap = dp2pxInt(8);
            int itemWidth = dp2pxInt(mMode.itemWidthDp);
            int itemHeight = dp2pxInt(mMode.itemHeightDp);
            int available = Math.max(itemWidth, width - padding * 2);
            int columns = Math.max(1, (available + gap) / (itemWidth + gap));
            int childWidthSpec = MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY);
            int childHeightSpec = MeasureSpec.makeMeasureSpec(itemHeight, MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(childWidthSpec, childHeightSpec);
            }
            int rows = getChildCount() == 0 ? 0 : (getChildCount() + columns - 1) / columns;
            height = rows == 0 ? 0 : padding * 2 + rows * itemHeight + (rows - 1) * gap;
        }

        height = Math.max(height, mMinimumContentHeight);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mMode == AssetViewMode.LIST) {
            int y = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                int h = child.getMeasuredHeight();
                child.layout(0, y, r - l, y + h);
                y += h;
            }
            return;
        }

        int padding = dp2pxInt(8);
        int gap = dp2pxInt(8);
        int itemWidth = dp2pxInt(mMode.itemWidthDp);
        int itemHeight = dp2pxInt(mMode.itemHeightDp);
        int available = Math.max(itemWidth, getWidth() - padding * 2);
        int columns = Math.max(1, (available + gap) / (itemWidth + gap));
        for (int i = 0; i < getChildCount(); i++) {
            int col = i % columns;
            int row = i / columns;
            int x = padding + col * (itemWidth + gap);
            int y = padding + row * (itemHeight + gap);
            getChildAt(i).layout(x, y, x + itemWidth, y + itemHeight);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mHost.requestContentFocus();
                if (isRightMouse(event)) {
                    mHost.onContentRightClick(event.getRawX(), event.getRawY());
                    return true;
                }
                mDownX = event.getX();
                mDownY = event.getY();
                mSelectionStartX = mDownX;
                mSelectionStartY = mDownY;
                mSelectionAdditive = event.isCtrlPressed();
                mSelectionBase.clear();
                mSelectionBase.addAll(mHost.selectedPathsSnapshot());
                mSelecting = false;
                mSelectionRect.setEmpty();
                if (!mSelectionAdditive) {
                    mHost.clearSelection();
                }
                mHost.disallowScrollIntercept(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                updateSelectionRect(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mSelecting) {
                    updateSelectionRect(event.getX(), event.getY());
                }
                mSelecting = false;
                mSelectionRect.setEmpty();
                mHost.disallowScrollIntercept(false);
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void updateSelectionRect(float currentX, float currentY) {
        if (!mSelecting) {
            if (Math.abs(currentX - mDownX) <= mTouchSlop && Math.abs(currentY - mDownY) <= mTouchSlop) {
                return;
            }
            mSelecting = true;
        }

        float left = Math.min(mSelectionStartX, currentX);
        float top = Math.min(mSelectionStartY, currentY);
        float right = Math.max(mSelectionStartX, currentX);
        float bottom = Math.max(mSelectionStartY, currentY);
        mSelectionRect.set(left, top, right, bottom);
        mHost.onBoxSelection(mSelectionRect, mSelectionAdditive, mSelectionBase);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mSelecting && mSelectionRect.right > mSelectionRect.left && mSelectionRect.bottom > mSelectionRect.top) {
            canvas.drawRect(mSelectionRect.left, mSelectionRect.top, mSelectionRect.right, mSelectionRect.bottom, mSelectionFillPaint);
            canvas.drawRect(mSelectionRect.left, mSelectionRect.top, mSelectionRect.right, mSelectionRect.bottom, mSelectionBorderPaint);
        }
    }

    private boolean isRightMouse(MotionEvent e) {
        return (e.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0
                || e.getActionButton() == MotionEvent.BUTTON_SECONDARY;
    }
}
