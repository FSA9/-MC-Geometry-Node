package com.mine.geometry_node.client.ui.components.base.scroll;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.widget.ScrollView;

/** Native vertical scrolling with project-scaled, nonoverlapping scrollbar hit bounds. */
public class UiScrollView extends ScrollView {
    private final UiScrollBehavior behavior = new UiScrollBehavior(false);

    public UiScrollView(Context context) {
        this(context, UiThemes.EDITOR.scroll());
    }

    public UiScrollView(Context context, UiTheme.Scroll colors) {
        super(context);
        java.util.Objects.requireNonNull(colors, "colors");
        setHorizontalScrollBarEnabled(false);
        behavior.configure(this, colors);
        setEdgeEffectColor(colors.edge());
    }

    /** Themes native vertical text scrollbars without changing layout, movement or hit policy. */
    public static void applyScrollbarColors(View host, UiTheme.Scroll colors) {
        UiScrollBehavior.applyColors(host, false, colors);
    }

    @Override
    public int getVerticalScrollbarWidth() {
        // Theme drawables can have an intrinsic size at ModernUI's unrelated density.
        return getScrollBarSize();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        behavior.syncSize(this);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean isVerticalScrollBarEnabled() {
        // Scoped to input dispatch: drawing and layout retain the real enabled state.
        return (behavior == null || !behavior.suppressed()) && super.isVerticalScrollBarEnabled();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        behavior.beginGesture(this, event);
        try {
            return super.dispatchTouchEvent(event);
        } finally {
            behavior.endGesture(event);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        boolean previous = behavior.filter(this, event);
        try {
            return super.onInterceptTouchEvent(event);
        } finally {
            behavior.restore(previous);
        }
    }

    @Override
    protected boolean handleScrollBarDragging(MotionEvent event) {
        boolean previous = behavior.filter(this, event);
        try {
            return super.handleScrollBarDragging(event);
        } finally {
            behavior.restore(previous);
        }
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_SCROLL) return super.dispatchGenericMotionEvent(event);
        boolean previous = behavior.filter(this, event);
        try {
            return super.dispatchGenericMotionEvent(event);
        } finally {
            behavior.restore(previous);
        }
    }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event) {
        boolean previous = behavior.filter(this, event);
        try {
            return super.onResolvePointerIcon(event);
        } finally {
            behavior.restore(previous);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        behavior.reset();
        super.onDetachedFromWindow();
    }
}
