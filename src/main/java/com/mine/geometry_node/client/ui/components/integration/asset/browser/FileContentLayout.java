package com.mine.geometry_node.client.ui.components.integration.asset.browser;

import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableRowHeights;

import com.mine.geometry_node.client.ui.theme.UiMetrics;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
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
import static com.mine.geometry_node.client.ui.components.base.drawable.UiDrawables.solid;

final class FileContentLayout extends ViewGroup {
    interface SelectionHost {
        void onContentRightClick(float rawX, float rawY);
        void onBoxSelection(RectF selectionRect, boolean additive, Set<String> baseSelection);
        void clearSelection();
        void requestContentFocus();
        Set<String> selectedPathsSnapshot();
        void disallowScrollIntercept(boolean disallow);
        void restoreScrollAnchor(int expectedScrollY, int scrollY);
        AssetFileItemView createItemView(AssetEntry entry, int index);
        void onMountedItemViewsChanged(Map<String, AssetFileItemView> mountedItems);
    }

    private final SelectionHost mHost;
    private final Paint mSelectionFillPaint = new Paint();
    private final Paint mSelectionBorderPaint = new Paint();
    private final RectF mSelectionRect = new RectF();
    private final Set<String> mSelectionBase = new LinkedHashSet<>();
    private final Map<Integer, AssetFileItemView> mMountedByIndex = new HashMap<>();
    private final Map<String, AssetFileItemView> mMountedByKey = new HashMap<>();
    private UiTableRowHeights mListRows = new UiTableRowHeights(0, 1);
    private ListReflow mListReflow;
    private final Runnable mApplyListReflow = this::applyListReflow;
    private int mListMeasuredWidth = -1;
    private int mListMinimumHeight;
    private List<AssetEntry> mEntries = List.of();
    private AssetViewMode mMode = AssetViewMode.LIST;
    private boolean mSelecting = false;
    private boolean mSelectionAdditive = false;
    private float mSelectionStartX;
    private float mSelectionStartY;
    private float mSelectionPointerX;
    private float mSelectionPointerViewportY;
    private int mSelectionGesture;
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
        mSelectionFillPaint.setColor(UiThemes.EDITOR.selection().marqueeFill());
        mSelectionFillPaint.setStyle(Paint.Style.FILL);
        mSelectionBorderPaint.setColor(UiThemes.EDITOR.selection().outline());
        mSelectionBorderPaint.setStyle(Paint.Style.STROKE);
        mSelectionBorderPaint.setStrokeWidth(dp2px(UiMetrics.MARQUEE_BORDER));
        setBackground(solid(UiThemes.EDITOR.surface().root()));
    }

    void setViewMode(AssetViewMode mode) {
        if (mMode == mode) return;
        cancelListReflow();
        mMode = mode;
        clearMountedItems();
        requestLayout();
        post(this::refreshMountedItems);
    }

    void setEntries(List<AssetEntry> entries) {
        cancelListReflow();
        mEntries = entries == null ? List.of() : List.copyOf(entries);
        mListMeasuredWidth = -1;
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
            ensureListRows(getWidth());
            if (selectionRect.right <= 0 || selectionRect.left >= getWidth()) return;
            int first = mListRows.indexAt(selectionRect.top);
            int last = mListRows.indexAt(Math.nextDown(selectionRect.bottom));
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
            ListReflow reflow = mListReflow != null ? mListReflow : captureListReflow();
            boolean geometryChanged = mListMeasuredWidth != width || mListMinimumHeight != itemHeight();
            ensureListRows(width);
            int childWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            int childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            boolean heightsChanged = false;
            for (var entry : mMountedByIndex.entrySet()) {
                View child = entry.getValue();
                child.measure(childWidthSpec, childHeightSpec);
                heightsChanged |= mListRows.setHeight(entry.getKey(), child.getMeasuredHeight());
            }
            height = mListRows.totalHeight();
            if (geometryChanged || heightsChanged) {
                if (reflow != null) {
                    mListReflow = reflow;
                    removeCallbacks(mApplyListReflow);
                    post(mApplyListReflow);
                } else post(this::refreshMountedItems);
            }
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
            for (Map.Entry<Integer, AssetFileItemView> entry : mMountedByIndex.entrySet()) {
                View child = entry.getValue();
                int y = mListRows.top(entry.getKey());
                child.layout(0, y, r - l, y + mListRows.height(entry.getKey()));
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
                ++mSelectionGesture;
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
                ++mSelectionGesture;
                mSelectionRect.setEmpty();
                mHost.disallowScrollIntercept(false);
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void updateSelectionRect(float currentX, float currentY) {
        mSelectionPointerX = currentX;
        mSelectionPointerViewportY = currentY - mViewportScrollY;
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
            AssetFileItemView view = mHost.createItemView(entry, index);
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
            ensureListRows(getWidth());
            int firstRow = mListRows.indexAt(scrollY);
            int lastRow = mListRows.indexAt((float) scrollY + viewportHeight);
            return new IndexRange(firstRow, Math.max(firstRow, lastRow));
        }

        int columns = columnsForWidth(getWidth());
        int rowPitch = itemHeight() + gridGap();
        int contentTop = Math.max(0, scrollY - gridPadding());
        int firstRow = contentTop / rowPitch;
        int lastRow = Math.max(firstRow,
                Math.max(0, scrollY + viewportHeight - gridPadding()) / rowPitch);
        int first = Math.min(mEntries.size() - 1, firstRow * columns);
        int last = Math.min(mEntries.size() - 1, ((lastRow + 1) * columns) - 1);
        return new IndexRange(first, Math.max(first, last));
    }

    private AssetEntry entryAt(float localX, float localY) {
        if (mEntries.isEmpty() || localX < 0.0f || localY < 0.0f) return null;
        if (localX >= getWidth()) return null;
        if (mMode == AssetViewMode.LIST) {
            ensureListRows(getWidth());
            if (localY >= mListRows.totalHeight()) return null;
            return mEntries.get(mListRows.indexAt(localY));
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
            ensureListRows(getWidth());
            return mListRows.top(index);
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
            ensureListRows(getWidth());
            return mListRows.totalHeight();
        }
        int columns = columnsForWidth(getWidth());
        int rows = (mEntries.size() + columns - 1) / columns;
        return gridPadding() * 2 + rows * itemHeight() + Math.max(0, rows - 1) * gridGap();
    }

    private void ensureListRows(int width) {
        int minimum = dp2pxInt(UiTableStyle.ROW_HEIGHT);
        if (mListMeasuredWidth == width && mListRows.size() == mEntries.size()
                && mListMinimumHeight == minimum) return;
        mListRows = new UiTableRowHeights(mEntries.size(), minimum);
        mListMeasuredWidth = width;
        mListMinimumHeight = minimum;
    }

    private ListReflow captureListReflow() {
        if (mListMeasuredWidth < 0 || mEntries.isEmpty() || mListRows.size() != mEntries.size()) return null;
        return new ListReflow(rowAnchor(mViewportScrollY), mViewportScrollY,
                mSelecting ? rowAnchor(mSelectionStartY) : null, mSelectionGesture);
    }

    private RowAnchor rowAnchor(float y) {
        int index = mListRows.indexAt(y);
        return new RowAnchor(index, y - mListRows.top(index));
    }

    private float anchorPosition(RowAnchor anchor) {
        return mListRows.top(anchor.index())
                + Math.max(0, Math.min(anchor.offset(), mListRows.height(anchor.index()) - 1));
    }

    private void applyListReflow() {
        ListReflow reflow = mListReflow;
        mListReflow = null;
        if (reflow == null || mMode != AssetViewMode.LIST) return;
        // Restore after layout so ScrollView clamps against the new content height.
        mHost.restoreScrollAnchor(reflow.scrollY(), (int) anchorPosition(reflow.viewport()));
        if (mSelecting && reflow.selectionStart() != null && reflow.gesture() == mSelectionGesture) {
            mSelectionStartY = anchorPosition(reflow.selectionStart());
            updateSelectionRect(mSelectionPointerX, mSelectionPointerViewportY + mViewportScrollY);
        }
        refreshMountedItems();
    }

    private void cancelListReflow() {
        removeCallbacks(mApplyListReflow);
        mListReflow = null;
        ++mSelectionGesture;
        mSelecting = false;
        mSelectionRect.setEmpty();
        mHost.disallowScrollIntercept(false);
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        cancelListReflow();
        super.onDetachedFromWindow();
    }

    private record RowAnchor(int index, float offset) {}
    private record ListReflow(RowAnchor viewport, int scrollY, RowAnchor selectionStart, int gesture) {}

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
        return dp2pxInt(mMode == AssetViewMode.LIST ? UiTableStyle.ROW_HEIGHT : mMode.itemHeightDp);
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
