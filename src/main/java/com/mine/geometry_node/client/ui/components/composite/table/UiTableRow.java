package com.mine.geometry_node.client.ui.components.composite.table;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Matrix;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;

/** One cell per shared column. Headers reserve narrow boundary hit areas for resizing. */
public final class UiTableRow extends ViewGroup {
    private final UiTheme theme;
    private final UiTableColumns columns;
    private boolean header;
    private boolean dataStyle, alternate, pointerInside;
    private final icyllis.modernui.graphics.drawable.ShapeDrawable dataBackground = new icyllis.modernui.graphics.drawable.ShapeDrawable();
    private int dragging = -1;
    private int hovered = -1;
    private float lastRawX, lastRawY;
    private final Matrix dragMatrix = new Matrix();
    private final float[] dragPoint = new float[2];

    public UiTableRow(Context context, UiTableColumns columns) {
        this(context, columns, UiThemes.EDITOR);
    }

    public UiTableRow(Context context, UiTableColumns columns, UiTheme theme) {
        super(context);
        this.theme = java.util.Objects.requireNonNull(theme, "theme");
        this.columns = columns;
    }

    public void setHeader(boolean header) {
        this.header = header;
        if (!header) finishDrag();
        invalidate();
    }

    /** Opt-in detailed rows; existing compact tables retain their own presentation. */
    public void setDataStyle(boolean alternate) {
        this.dataStyle = true;
        this.alternate = alternate;
        if (getBackground() != dataBackground) setBackground(dataBackground);
        updateDataBackground();
    }

    private void updateDataBackground() {
        if (dataStyle) dataBackground.setColor(pointerInside ? theme.surface().header()
                : alternate ? theme.control().input() : theme.surface().panel());
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int maxHeight = getSuggestedMinimumHeight();
        boolean exactHeight = MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY;
        for (int i = 0; i < Math.min(getChildCount(), columns.size()); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int requestedHeight = child.getLayoutParams().height;
            // MATCH_PARENT must not consume the parent's available height before the row has a height.
            if (!exactHeight && requestedHeight == LayoutParams.MATCH_PARENT)
                requestedHeight = LayoutParams.WRAP_CONTENT;
            int childHeightSpec = getChildMeasureSpec(heightSpec, 0, requestedHeight);
            child.measure(MeasureSpec.makeMeasureSpec(columns.width(i), MeasureSpec.EXACTLY), childHeightSpec);
            maxHeight = Math.max(maxHeight, child.getMeasuredHeight());
        }
        int rowHeight = resolveSize(maxHeight, heightSpec);
        setMeasuredDimension(resolveSize(columns.contentWidth(), widthSpec), rowHeight);
        if (!exactHeight) {
            for (int i = 0; i < Math.min(getChildCount(), columns.size()); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != GONE && child.getLayoutParams().height == LayoutParams.MATCH_PARENT) {
                    child.measure(MeasureSpec.makeMeasureSpec(columns.width(i), MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.EXACTLY));
                }
            }
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = 0;
        for (int i = 0; i < Math.min(getChildCount(), columns.size()); i++) {
            View child = getChildAt(i);
            int width = columns.width(i);
            if (child.getVisibility() != GONE) {
                int y = Math.max(0, (getHeight() - child.getMeasuredHeight()) / 2);
                child.layout(x, y, x + width, y + child.getMeasuredHeight());
            }
            x += width;
        }
    }

    @Override protected LayoutParams generateDefaultLayoutParams() { return new LayoutParams(-1, -1); }

    private int boundary(float x) {
        if (!header || !isEnabled()) return -1;
        int edge = 0;
        float hit = Math.max(3, UIUtils.dp2px(4));
        for (int i = 0; i < columns.size(); i++) {
            edge += columns.width(i);
            if (columns.column(i).resizable() && Math.abs(x - edge) <= hit) return i;
        }
        return -1;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            boolean primary = event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)
                    || event.getActionButton() == MotionEvent.BUTTON_PRIMARY;
            dragging = primary && UIUtils.rootToLocal(this, event.getRawX(), event.getRawY(), dragMatrix, dragPoint)
                    ? boundary(dragPoint[0]) : -1;
            if (dragging >= 0) {
                lastRawX = event.getRawX();
                lastRawY = event.getRawY();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
        }
        return dragging >= 0;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (dragging < 0) return super.onTouchEvent(event);
        if (!isEnabled() || !header) {
            finishDrag();
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE -> {
                if (UIUtils.rootToLocal(this, lastRawX, lastRawY, dragMatrix, dragPoint)) {
                    float previous = dragPoint[0];
                    if (UIUtils.rootToLocal(this, event.getRawX(), event.getRawY(), dragMatrix, dragPoint)) {
                        columns.resize(dragging, dragPoint[0] - previous);
                    }
                }
                lastRawX = event.getRawX();
                lastRawY = event.getRawY();
                invalidate();
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finishDrag();
        }
        return true;
    }

    private void finishDrag() {
        if (dragging < 0) return;
        dragging = -1;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            hovered = -1;
            finishDrag();
            invalidate();
        }
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        if (!focused) {
            hovered = -1;
            finishDrag();
            invalidate();
        }
        super.onWindowFocusChanged(focused);
    }

    @Override public boolean dispatchHoverEvent(MotionEvent event) {
        pointerInside = event.getActionMasked() != MotionEvent.ACTION_HOVER_EXIT
                && event.getX() >= 0 && event.getX() < getWidth()
                && event.getY() >= 0 && event.getY() < getHeight();
        int next = event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT ? -1 : boundary(event.getX());
        if (hovered != next) { hovered = next; invalidate(); }
        boolean handled = super.dispatchHoverEvent(event);
        updateDataBackground();
        return handled;
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!header && !dataStyle) return;
        Paint paint = Paint.obtain();
        if (dataStyle) {
            paint.setColor(UiTableStyle.dividerColor(theme));
            canvas.drawRect(0, getHeight() - Math.max(1, UIUtils.dp2px(1)), getWidth(), getHeight(), paint);
        }
        if (!header) { paint.recycle(); return; }
        int x = 0;
        for (int i = 0; i < columns.size(); i++) {
            x += columns.width(i);
            if (!columns.column(i).resizable()) continue;
            paint.setColor(i == dragging || i == hovered ? theme.border().focus() : theme.border().strong());
            float thickness = Math.max(1, UIUtils.dp2px(1));
            canvas.drawRect(x - thickness, UIUtils.dp2px(6), x, getHeight() - UIUtils.dp2px(6), paint);
        }
        paint.recycle();
    }

    @Override protected void onDetachedFromWindow() {
        finishDrag();
        hovered = -1;
        pointerInside = false;
        updateDataBackground();
        super.onDetachedFromWindow();
    }
}
