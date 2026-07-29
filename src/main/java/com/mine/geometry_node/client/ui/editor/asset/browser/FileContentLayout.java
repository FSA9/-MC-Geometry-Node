package com.mine.geometry_node.client.ui.editor.asset.browser;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
        AssetFileItemView createItemView(AssetEntry entry);
        void onMountedItemViewsChanged(Map<String, AssetFileItemView> mountedItems);
    }

    private static final int OVERSCAN_ROWS = 3;

    private final SelectionHost mHost;
    private final Paint mSelectionFillPaint = new Paint();
    private final Paint mSelectionBorderPaint = new Paint();
    private final RectF mSelectionRect = new RectF();
    private final Set<String> mSelectionBase = new LinkedHashSet<>();
    private final Map<Integer, AssetFileItemView> mMountedByIndex = new HashMap<>();
    private final Map<String, AssetFileItemView> mMountedByKey = new HashMap<>();
    private List<AssetEntry> mEntries = List.of();
    private AssetViewMode mMode = AssetViewMode.LIST;
    private boolean mSelecting = false;
    private boolean mSelectionAdditive = false;
    private float mSelectionStartX;
    private float mSelectionStartY;
    private float mDownX;
    private float mDownY;
    private int mMinimumContentHeight = 0;
    private int mViewportScrollY = 0;
    private int mViewportHeight = 0;
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
        setBackground(AssetFileBrowserPanel.createColorDrawable(0xFF1E1E1E));
    }

    void setViewMode(AssetViewMode mode) {
        if (mMode == mode) return;
        mMode = mode;
        clearMountedItems();
        requestLayout();
        post(this::refreshMountedItems);
    }

    void setEntries(List<AssetEntry> entries) {
        mEntries = entries == null ? List.of() : List.copyOf(entries);
        mViewportScrollY = 0;
        clearMountedItems();
        requestLayout();
        post(this::refreshMountedItems);
    }

    void updateViewport(int scrollY, int viewportHeight) {
        mViewportScrollY = Math.max(0, scrollY);
        mViewportHeight = Math.max(0, viewportHeight);
        refreshMountedItems();
    }

    void setMinimumContentHeight(int height) {
        if (mMinimumContentHeight == height) return;
        mMinimumContentHeight = height;
        requestLayout();
    }

    void collectEntriesIntersecting(RectF selectionRect, Set<String> out) {
        if (selectionRect == null || out == null || mEntries.isEmpty()) return;
        if (selectionRect.right <= selectionRect.left || selectionRect.bottom <= selectionRect.top) return;
        int contentHeight = entriesContentHeight();
        if (selectionRect.bottom <= 0.0f || selectionRect.top >= contentHeight) return;

        if (mMode == AssetViewMode.LIST) {
            int rowHeight = itemHeight();
            int first = clampIndex((int) Math.floor(selectionRect.top / rowHeight));
            int last = clampIndex((int) Math.floor((selectionRect.bottom - 1.0f) / rowHeight));
            for (int i = first; i <= last; i++) {
                out.add(mEntries.get(i).key());
            }
            return;
        }

        int columns = columnsForWidth(getWidth());
        int itemWidth = itemWidth();
        int itemHeight = itemHeight();
        int padding = gridPadding();
        int gap = gridGap();
        int firstRow = Math.max(0, (int) Math.floor((selectionRect.top - padding) / (float) (itemHeight + gap)));
        int lastRow = Math.max(0, (int) Math.floor((selectionRect.bottom - padding - 1.0f) / (float) (itemHeight + gap)));
        RectF itemRect = new RectF();
        for (int row = firstRow; row <= lastRow; row++) {
            int firstIndex = row * columns;
            if (firstIndex >= mEntries.size()) break;
            for (int col = 0; col < columns; col++) {
                int index = firstIndex + col;
                if (index >= mEntries.size()) break;
                int left = padding + col * (itemWidth + gap);
                int top = padding + row * (itemHeight + gap);
                itemRect.set(left, top, left + itemWidth, top + itemHeight);
                if (itemRect.intersects(selectionRect.left, selectionRect.top, selectionRect.right, selectionRect.bottom)) {
                    out.add(mEntries.get(index).key());
                }
            }
        }
    }

    AssetEntry entryAtRaw(float rawX, float rawY) {
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        return entryAt(rawX - loc[0], rawY - loc[1]);
    }

    int entryTop(String key) {
        if (key == null || key.isEmpty()) return -1;
        for (int i = 0; i < mEntries.size(); i++) {
            if (key.equals(mEntries.get(i).key())) {
                return topForIndex(i);
            }
        }
        return -1;
    }

    void forceRefreshMountedItems() {
        refreshMountedItems();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height;

        if (mMode == AssetViewMode.LIST) {
            int rowHeight = itemHeight();
            int childWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            int childHeightSpec = MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(childWidthSpec, childHeightSpec);
            }
            height = rowHeight * mEntries.size();
        } else {
            int padding = gridPadding();
            int gap = gridGap();
            int itemWidth = itemWidth();
            int itemHeight = itemHeight();
            int columns = columnsForWidth(width);
            int childWidthSpec = MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY);
            int childHeightSpec = MeasureSpec.makeMeasureSpec(itemHeight, MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(childWidthSpec, childHeightSpec);
            }
            int rows = mEntries.isEmpty() ? 0 : (mEntries.size() + columns - 1) / columns;
            height = rows == 0 ? 0 : padding * 2 + rows * itemHeight + (rows - 1) * gap;
        }

        height = Math.max(height, mMinimumContentHeight);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed) {
            post(this::refreshMountedItems);
        }
        if (mMode == AssetViewMode.LIST) {
            int rowHeight = itemHeight();
            for (Map.Entry<Integer, AssetFileItemView> entry : mMountedByIndex.entrySet()) {
                View child = entry.getValue();
                int y = entry.getKey() * rowHeight;
                child.layout(0, y, r - l, y + rowHeight);
            }
            return;
        }

        int padding = gridPadding();
        int gap = gridGap();
        int itemWidth = itemWidth();
        int itemHeight = itemHeight();
        int columns = columnsForWidth(getWidth());
        for (Map.Entry<Integer, AssetFileItemView> entry : mMountedByIndex.entrySet()) {
            int index = entry.getKey();
            int col = index % columns;
            int row = index / columns;
            int x = padding + col * (itemWidth + gap);
            int y = padding + row * (itemHeight + gap);
            entry.getValue().layout(x, y, x + itemWidth, y + itemHeight);
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

    private void refreshMountedItems() {
        if (getWidth() <= 0 || mEntries.isEmpty()) {
            if (!mMountedByIndex.isEmpty()) {
                clearMountedItems();
            }
            return;
        }

        IndexRange range = visibleIndexRange();
        boolean changed = false;
        List<Integer> toRemove = new ArrayList<>();
        for (Integer index : mMountedByIndex.keySet()) {
            if (index < range.first || index > range.last) {
                toRemove.add(index);
            }
        }
        for (Integer index : toRemove) {
            AssetFileItemView view = mMountedByIndex.remove(index);
            if (view == null) continue;
            mMountedByKey.remove(view.getEntry().key());
            removeView(view);
            changed = true;
        }

        for (int index = range.first; index <= range.last; index++) {
            if (mMountedByIndex.containsKey(index)) continue;
            AssetEntry entry = mEntries.get(index);
            AssetFileItemView view = mHost.createItemView(entry);
            mMountedByIndex.put(index, view);
            mMountedByKey.put(entry.key(), view);
            addView(view);
            changed = true;
        }

        if (changed) {
            mHost.onMountedItemViewsChanged(new HashMap<>(mMountedByKey));
            requestLayout();
            invalidate();
        }
    }

    private void clearMountedItems() {
        removeAllViews();
        mMountedByIndex.clear();
        mMountedByKey.clear();
        mHost.onMountedItemViewsChanged(Map.of());
    }

    private IndexRange visibleIndexRange() {
        int viewportHeight = viewportHeight();
        int scrollY = Math.max(0, mViewportScrollY);
        if (mMode == AssetViewMode.LIST) {
            int rowHeight = itemHeight();
            int firstRow = Math.max(0, scrollY / rowHeight - OVERSCAN_ROWS);
            int lastRow = Math.min(mEntries.size() - 1, (scrollY + viewportHeight) / rowHeight + OVERSCAN_ROWS);
            return new IndexRange(firstRow, Math.max(firstRow, lastRow));
        }

        int columns = columnsForWidth(getWidth());
        int rowPitch = itemHeight() + gridGap();
        int contentTop = Math.max(0, scrollY - gridPadding());
        int firstRow = Math.max(0, contentTop / rowPitch - OVERSCAN_ROWS);
        int lastRow = Math.max(firstRow, (Math.max(0, scrollY + viewportHeight - gridPadding())) / rowPitch + OVERSCAN_ROWS);
        int first = Math.min(mEntries.size() - 1, firstRow * columns);
        int last = Math.min(mEntries.size() - 1, ((lastRow + 1) * columns) - 1);
        return new IndexRange(first, Math.max(first, last));
    }

    private AssetEntry entryAt(float localX, float localY) {
        if (mEntries.isEmpty() || localX < 0.0f || localY < 0.0f) return null;
        if (localX >= getWidth()) return null;
        if (mMode == AssetViewMode.LIST) {
            int index = (int) (localY / itemHeight());
            return index >= 0 && index < mEntries.size() ? mEntries.get(index) : null;
        }

        int padding = gridPadding();
        int gap = gridGap();
        int itemWidth = itemWidth();
        int itemHeight = itemHeight();
        float x = localX - padding;
        float y = localY - padding;
        if (x < 0.0f || y < 0.0f) return null;
        int col = (int) (x / (itemWidth + gap));
        int row = (int) (y / (itemHeight + gap));
        if (col < 0 || col >= columnsForWidth(getWidth())) return null;
        if (x - col * (itemWidth + gap) >= itemWidth || y - row * (itemHeight + gap) >= itemHeight) return null;
        int index = row * columnsForWidth(getWidth()) + col;
        return index >= 0 && index < mEntries.size() ? mEntries.get(index) : null;
    }

    private int topForIndex(int index) {
        if (index < 0 || index >= mEntries.size()) return -1;
        if (mMode == AssetViewMode.LIST) {
            return index * itemHeight();
        }
        int columns = columnsForWidth(getWidth());
        return gridPadding() + (index / columns) * (itemHeight() + gridGap());
    }

    private int viewportHeight() {
        if (mViewportHeight > 0) return mViewportHeight;
        ViewParent parent = getParent();
        if (parent instanceof View view && view.getHeight() > 0) {
            return view.getHeight();
        }
        return Math.max(itemHeight(), getHeight());
    }

    private int entriesContentHeight() {
        if (mEntries.isEmpty()) return 0;
        if (mMode == AssetViewMode.LIST) {
            return itemHeight() * mEntries.size();
        }
        int columns = columnsForWidth(getWidth());
        int rows = (mEntries.size() + columns - 1) / columns;
        return gridPadding() * 2 + rows * itemHeight() + Math.max(0, rows - 1) * gridGap();
    }

    private int clampIndex(int index) {
        if (mEntries.isEmpty()) return 0;
        return Math.max(0, Math.min(mEntries.size() - 1, index));
    }

    private int columnsForWidth(int width) {
        int padding = gridPadding();
        int gap = gridGap();
        int itemWidth = itemWidth();
        int available = Math.max(itemWidth, width - padding * 2);
        return Math.max(1, (available + gap) / (itemWidth + gap));
    }

    private int itemWidth() {
        return dp2pxInt(mMode.itemWidthDp);
    }

    private int itemHeight() {
        return dp2pxInt(mMode.itemHeightDp);
    }

    private int gridPadding() {
        return dp2pxInt(8);
    }

    private int gridGap() {
        return dp2pxInt(8);
    }

    private record IndexRange(int first, int last) {
    }
}
