package com.mine.geometry_node.client.ui.components.menu;

import com.mine.geometry_node.client.ui.components.scroll.UiScrollView;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.area.AreaLifecycle;
import com.mine.geometry_node.client.ui.workspace.area.AreaTransientUi;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Matrix;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ScrollView;

/** Reusable arbitrary-content popup. Positioning uses root coordinates and layout margins. */
public final class UiPopover extends FrameLayout {
    private final ScrollView panel;
    private final int width;
    private final int maxHeight;
    private final UiMenuFocus focus = new UiMenuFocus();

    /** Size from the required child width in pixels, after individual UI dimensions are rounded. */
    public static UiPopover forContentWidth(Context context, View content, int contentWidthPx, int maxHeightDp) {
        return new UiPopover(context, content, contentWidthPx, maxHeightDp, true);
    }
    private boolean outsidePressed;
    private boolean dismissPending;
    private final Runnable deferredDismiss = () -> {
        if (dismissPending) finishDismiss();
    };
    private final Matrix inputMatrix=new Matrix();
    private final float[] inputPoint=new float[2];
    private AreaLifecycle.Subscription binding = AreaLifecycle.Subscription.NONE;
    public UiPopover(Context context, View content, int widthDp) {
        this(context, content, widthDp, 0);
    }
    /** A zero maximum height uses the available host height. */
    public UiPopover(Context context, View content, int widthDp, int maxHeightDp) {
        this(context, content, widthDp, maxHeightDp, false);
    }
    private UiPopover(Context context, View content, int widthDp, int maxHeightDp, boolean contentWidth) {
        super(context);
        if (widthDp <= 0 || maxHeightDp < 0) throw new IllegalArgumentException("Invalid popup dimensions");
        maxHeight = maxHeightDp == 0 ? Integer.MAX_VALUE : UIUtils.dp2pxInt(maxHeightDp);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setOnClickListener(v -> dismiss());
        panel = new UiScrollView(context);
        panel.setOnClickListener(v -> {});
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(UiTheme.Surface.PANEL);
        background.setCornerRadius(UIUtils.dp2px(4));
        background.setStroke(UIUtils.dp2pxInt(1), UiTheme.Control.BORDER);
        panel.setBackground(background);
        int padding = UIUtils.dp2pxInt(7);
        // Every content type needs symmetric margins. An inset scrollbar changes the
        // resolved padding on one side even when no scrollbar is currently visible.
        // Overlay only the reserved outer margin; controls remain clear of the track.
        panel.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        int side = Math.max(padding, panel.getVerticalScrollbarWidth()) + Math.max(1, UIUtils.dp2pxInt(4));
        panel.setPadding(side,padding,side,padding);
        width = contentWidth ? widthDp + side * 2 : UIUtils.dp2pxInt(widthDp);
        panel.addView(content, new ViewGroup.LayoutParams(-1,-2));
        addView(panel, new FrameLayout.LayoutParams(width,-2));
    }
    public void showAtRoot(View owner, FrameLayout host, float rootX, float rootY) {
        dismiss();
        // Opening during an ongoing layout must not reinsert a child either.
        if (dismissPending || host.isInLayout()) return;
        setVisibility(VISIBLE);
        float[] point = {rootX,rootY};
        if (!UIUtils.rootToLocal(host,rootX,rootY,new Matrix(),point)) return;
        int w = Math.min(width,host.getWidth());
        if (w <= 0 || host.getHeight() <= 0) return;
        panel.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.min(host.getHeight(),maxHeight),MeasureSpec.AT_MOST));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w,Math.min(host.getHeight(),panel.getMeasuredHeight()));
        lp.leftMargin = Math.round(Math.clamp(point[0],0,host.getWidth()-w));
        lp.topMargin = Math.round(Math.clamp(point[1],0,Math.max(0,host.getHeight()-lp.height)));
        panel.setLayoutParams(lp);
        focus.capture(owner);
        host.addView(this,new FrameLayout.LayoutParams(-1,-1));
        binding = AreaTransientUi.bind(owner,this,this::dismiss);
        requestFocus();
    }
    /** Refit dynamic menu contents while retaining the current local anchor and viewport bounds. */
    public void refreshSize() {
        if (!(getParent() instanceof ViewGroup host)) return;
        int w=Math.min(width,host.getWidth());
        panel.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.min(host.getHeight(),maxHeight),MeasureSpec.AT_MOST));
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)panel.getLayoutParams();
        lp.width=w;lp.height=Math.min(host.getHeight(),panel.getMeasuredHeight());
        lp.leftMargin=Math.max(0,Math.min(lp.leftMargin,host.getWidth()-w));
        lp.topMargin=Math.max(0,Math.min(lp.topMargin,host.getHeight()-lp.height));
        panel.setLayoutParams(lp);panel.scrollTo(0,0);
    }
    public void showBelow(View anchor, FrameLayout host) {
        Matrix matrix = new Matrix();
        anchor.transformMatrixToGlobal(matrix);
        float[] point = {0,anchor.getHeight()};
        matrix.mapPoint(point);
        showAtRoot(anchor,host,point[0],point[1]);
    }
    public boolean isOpen() { return !dismissPending && getParent() != null; }
    public void dismiss() {
        outsidePressed = false;
        binding.close(); binding = AreaLifecycle.Subscription.NONE;
        focus.restoreAndClear(this);
        if (getParent() != null) clearTooltipState(this);
        if (getParent() != null && isInLayout()) {
            // A sibling's size callback can close us while FrameLayout is iterating
            // its original child count. Hide now; mutate the child array after layout.
            if (!dismissPending) {
                dismissPending = true;
                setVisibility(GONE);
                post(deferredDismiss);
            }
            return;
        }
        finishDismiss();
    }
    private void finishDismiss() {
        removeCallbacks(deferredDismiss);
        dismissPending = false;
        if (getParent() instanceof ViewGroup group) group.removeView(this);
    }
    /** ModernUI offsets child events before the inherited ancestor scale. Rebase once at this
     * unscaled popup boundary, then retain native scrolling, text editing and click dispatch. */
    private MotionEvent localEvent(MotionEvent source) {
        if(!UIUtils.rootToLocal(this,source.getRawX(),source.getRawY(),inputMatrix,inputPoint))return null;
        MotionEvent event=MotionEvent.obtain(source.getEventTimeNano(),source.getAction(),source.getActionButton(),
                inputPoint[0],inputPoint[1],source.getModifiers(),source.getButtonState(),0);
        event.setAxisValue(MotionEvent.AXIS_VSCROLL,source.getAxisValue(MotionEvent.AXIS_VSCROLL));
        event.setAxisValue(MotionEvent.AXIS_HSCROLL,source.getAxisValue(MotionEvent.AXIS_HSCROLL));
        return event;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent source) {
        if(!isOpen())return false;
        MotionEvent event=localEvent(source);if(event==null)return false;
        try {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                outsidePressed = !containsPanel(event.getX(), event.getY());
            }
            if (outsidePressed) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) dismiss();
                else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) outsidePressed = false;
                return true;
            }
            return super.dispatchTouchEvent(event);
        } finally { event.recycle(); }
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent source) {
        if(!isOpen())return false;
        MotionEvent event=localEvent(source);if(event==null)return false;
        try{return super.dispatchGenericMotionEvent(event);}finally{event.recycle();}
    }
    /** Owner windows can dismiss clicks outside this popup's host as well (header/sidebar). */
    public boolean dismissIfOutside(float rootX, float rootY) {
        if (!isOpen() || !UIUtils.rootToLocal(this, rootX, rootY, inputMatrix, inputPoint)) return false;
        if (containsPanel(inputPoint[0], inputPoint[1])) return false;
        dismiss();
        return true;
    }
    private boolean containsPanel(float x, float y) {
        return x >= panel.getLeft() && x < panel.getRight() && y >= panel.getTop() && y < panel.getBottom();
    }

    private static void clearTooltipState(View view) {
        CharSequence text = view.getTooltipText();
        if (text != null) {
            // The public setter cancels pending callbacks and hides an active tooltip.
            // Restore only its configuration so reusable content can show it again.
            view.setTooltipText(null);
            view.setTooltipText(text);
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) clearTooltipState(group.getChildAt(i));
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getKeyCode()==KeyEvent.KEY_ESCAPE){dismiss();return true;}
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
        super.onSizeChanged(w,h,oldW,oldH);
        if(oldW>0&&oldH>0&&(w!=oldW||h!=oldH))dismiss();
    }
    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if (key == KeyEvent.KEY_ESCAPE) { dismiss(); return true; }
        return super.onKeyDown(key,event);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) dismiss();
    }
}
