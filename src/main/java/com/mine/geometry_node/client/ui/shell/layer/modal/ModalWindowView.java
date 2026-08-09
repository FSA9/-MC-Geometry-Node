package com.mine.geometry_node.client.ui.shell.layer.modal;

import com.mine.geometry_node.client.ui.common.SvgIconView;
import com.mine.geometry_node.client.ui.shell.layer.OverlayCloseReason;
import com.mine.geometry_node.client.ui.shell.layer.OverlayHandle;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.util.Objects;

/** Reusable, business-agnostic window chrome for MainUI modal overlays. */
public class ModalWindowView extends LinearLayout implements MainUiModal {
    public enum MovementMode {
        FIXED_CENTER,
        DRAGGABLE
    }

    public record Style(
            int windowColor,
            int titleBarColor,
            int borderColor,
            int textColor,
            int mutedTextColor,
            int closeHoverColor,
            float cornerRadiusDp
    ) {
        public static Style defaults() {
            return new Style(
                    0xFF252525,
                    0xFF202020,
                    0xFF424242,
                    0xFFE6E6E6,
                    0xFFAAAAAA,
                    0x334C9FFF,
                    4.0f
            );
        }
    }

    private static final float DEFAULT_WIDTH_DP = 520.0f;
    private static final float DEFAULT_MIN_WIDTH_DP = 280.0f;
    private static final float TITLE_HEIGHT_DP = 34.0f;
    private static final float CONTENT_PADDING_X_DP = 14.0f;
    private static final float CONTENT_PADDING_Y_DP = 12.0f;
    private static final float ACTION_HEIGHT_DP = 48.0f;
    private static final float CLOSE_BUTTON_SIZE_DP = 30.0f;
    private static final float CLOSE_ICON_SIZE_DP = 14.0f;
    private static final float SCREEN_EDGE_DP = 8.0f;

    private final Style style;
    private final TextView titleView;
    private final FrameLayout closeButton;
    private final FrameLayout contentHost;
    private final LinearLayout actionsHost;

    private MovementMode movementMode;
    private OverlayHandle overlayHandle;
    private float preferredWidthDp = DEFAULT_WIDTH_DP;
    private float preferredHeightDp;
    private float minimumWidthDp = DEFAULT_MIN_WIDTH_DP;
    private float minimumHeightDp;
    private float maximumWidthDp;
    private float maximumHeightDp;
    private float dragStartRawX;
    private float dragStartRawY;
    private int dragStartLeft;
    private int dragStartTop;
    private boolean dragging;

    public ModalWindowView(Context context, CharSequence title) {
        this(context, title, MovementMode.DRAGGABLE, Style.defaults());
    }

    public ModalWindowView(Context context, CharSequence title, MovementMode movementMode) {
        this(context, title, movementMode, Style.defaults());
    }

    public ModalWindowView(Context context, CharSequence title, MovementMode movementMode, Style style) {
        super(context);
        this.movementMode = Objects.requireNonNull(movementMode, "movementMode");
        this.style = Objects.requireNonNull(style, "style");

        setOrientation(VERTICAL);
        setBackground(windowBackground(style));
        setFocusable(true);

        LinearLayout titleBar = new LinearLayout(context);
        titleBar.setOrientation(HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(UIUtils.dp2pxInt(12.0f), 0, UIUtils.dp2pxInt(2.0f), 0);
        titleBar.setBackground(rect(style.titleBarColor(), style.cornerRadiusDp(), 0, 0));
        titleBar.setOnTouchListener(this::onTitleBarTouch);

        titleView = UIUtils.createLockedTextView(context, title == null ? "" : title.toString(),
                13.0f, style.textColor());
        titleView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        titleBar.addView(titleView, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        closeButton = createCloseButton(context);
        LayoutParams closeParams = new LayoutParams(
                UIUtils.dp2pxInt(CLOSE_BUTTON_SIZE_DP),
                UIUtils.dp2pxInt(CLOSE_BUTTON_SIZE_DP)
        );
        titleBar.addView(closeButton, closeParams);
        addView(titleBar, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(TITLE_HEIGHT_DP)
        ));

        contentHost = new FrameLayout(context);
        contentHost.setPadding(
                UIUtils.dp2pxInt(CONTENT_PADDING_X_DP),
                UIUtils.dp2pxInt(CONTENT_PADDING_Y_DP),
                UIUtils.dp2pxInt(CONTENT_PADDING_X_DP),
                UIUtils.dp2pxInt(CONTENT_PADDING_Y_DP)
        );
        addView(contentHost, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        ));

        actionsHost = new LinearLayout(context);
        actionsHost.setOrientation(HORIZONTAL);
        actionsHost.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        actionsHost.setPadding(
                UIUtils.dp2pxInt(CONTENT_PADDING_X_DP),
                UIUtils.dp2pxInt(6.0f),
                UIUtils.dp2pxInt(CONTENT_PADDING_X_DP),
                UIUtils.dp2pxInt(8.0f)
        );
        actionsHost.setVisibility(GONE);
        addView(actionsHost, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(ACTION_HEIGHT_DP)
        ));
    }

    public final TextView titleView() {
        return titleView;
    }

    public final FrameLayout contentHost() {
        return contentHost;
    }

    public final LinearLayout actionsHost() {
        return actionsHost;
    }

    public final Style style() {
        return style;
    }

    public final void setTitle(CharSequence title) {
        titleView.setText(title == null ? "" : title);
    }

    public final void setContent(View content) {
        contentHost.removeAllViews();
        if (content != null) {
            contentHost.addView(content, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
        }
    }

    public final void setActions(View actions) {
        actionsHost.removeAllViews();
        if (actions == null) {
            actionsHost.setVisibility(GONE);
            return;
        }
        actionsHost.addView(actions, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        actionsHost.setVisibility(VISIBLE);
    }

    public final void setActionsVisible(boolean visible) {
        actionsHost.setVisibility(visible ? VISIBLE : GONE);
    }

    public final void setCloseButtonVisible(boolean visible) {
        closeButton.setVisibility(visible ? VISIBLE : GONE);
    }

    public final MovementMode movementMode() {
        return movementMode;
    }

    public final void setMovementMode(MovementMode movementMode) {
        MovementMode next = Objects.requireNonNull(movementMode, "movementMode");
        if (this.movementMode == next) {
            return;
        }
        this.movementMode = next;
        dragging = false;
        if (next == MovementMode.FIXED_CENTER) {
            centerInParent();
        }
    }

    public final void setPreferredSizeDp(float widthDp, float heightDp) {
        preferredWidthDp = nonNegative(widthDp, "widthDp");
        preferredHeightDp = nonNegative(heightDp, "heightDp");
        requestLayout();
    }

    public final void setMinimumSizeDp(float widthDp, float heightDp) {
        float width = nonNegative(widthDp, "widthDp");
        float height = nonNegative(heightDp, "heightDp");
        validateSizeConstraints(width, height, maximumWidthDp, maximumHeightDp);
        minimumWidthDp = width;
        minimumHeightDp = height;
        requestLayout();
    }

    public final void setMaximumSizeDp(float widthDp, float heightDp) {
        float width = nonNegative(widthDp, "widthDp");
        float height = nonNegative(heightDp, "heightDp");
        validateSizeConstraints(minimumWidthDp, minimumHeightDp, width, height);
        maximumWidthDp = width;
        maximumHeightDp = height;
        requestLayout();
    }

    public final boolean requestClose() {
        return overlayHandle != null
                && overlayHandle.requestClose(OverlayCloseReason.PROGRAMMATIC);
    }

    @Override
    public final View createView(Context context) {
        if (context != getContext()) {
            throw new IllegalArgumentException("ModalWindowView must be shown in its creation context");
        }
        return this;
    }

    @Override
    public final FrameLayout.LayoutParams createLayoutParams(ViewGroup host) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.CENTER;
        return params;
    }

    @Override
    public final void onShown(OverlayHandle handle) {
        overlayHandle = Objects.requireNonNull(handle, "handle");
        onWindowShown();
        post(this::constrainToParent);
    }

    @Override
    public final void onClosed(OverlayCloseReason reason) {
        dragging = false;
        onWindowClosed(reason);
    }

    @Override
    public final void onDestroyed() {
        overlayHandle = null;
        onWindowDestroyed();
    }

    protected void onWindowShown() {
    }

    protected void onWindowClosed(OverlayCloseReason reason) {
    }

    protected void onWindowDestroyed() {
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int targetWidth = resolveMeasuredDimension(
                getMeasuredWidth(), preferredWidthDp, minimumWidthDp, maximumWidthDp, widthMeasureSpec);
        int targetHeight = resolveMeasuredDimension(
                getMeasuredHeight(), preferredHeightDp, minimumHeightDp, maximumHeightDp, heightMeasureSpec);
        if (targetWidth != getMeasuredWidth() || targetHeight != getMeasuredHeight()) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(targetWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY)
            );
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        dragging = false;
        super.onDetachedFromWindow();
    }

    private FrameLayout createCloseButton(Context context) {
        FrameLayout button = new FrameLayout(context);
        button.setClickable(true);
        button.setOnClickListener(view -> onCloseRequested());
        button.setOnHoverListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                button.setBackground(rect(style.closeHoverColor(), 2.0f, 0, 0));
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                button.setBackground(null);
            }
            return false;
        });

        SvgIconView icon = new SvgIconView(context, SvgIconView.Icon.CLOSE, style.mutedTextColor());
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                UIUtils.dp2pxInt(CLOSE_ICON_SIZE_DP),
                UIUtils.dp2pxInt(CLOSE_ICON_SIZE_DP)
        );
        iconParams.gravity = Gravity.CENTER;
        button.addView(icon, iconParams);
        return button;
    }

    protected void onCloseRequested() {
        requestClose();
    }

    private boolean onTitleBarTouch(View view, MotionEvent event) {
        if (movementMode != MovementMode.DRAGGABLE) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                ensureAbsolutePosition();
                dragStartRawX = event.getRawX();
                dragStartRawY = event.getRawY();
                FrameLayout.LayoutParams params = frameLayoutParams();
                dragStartLeft = params.leftMargin;
                dragStartTop = params.topMargin;
                dragging = true;
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    moveTo(
                            dragStartLeft + Math.round(event.getRawX() - dragStartRawX),
                            dragStartTop + Math.round(event.getRawY() - dragStartRawY)
                    );
                }
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false;
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    private void centerInParent() {
        if (!(getLayoutParams() instanceof FrameLayout.LayoutParams params)) {
            return;
        }
        params.gravity = Gravity.CENTER;
        params.leftMargin = 0;
        params.topMargin = 0;
        setLayoutParams(params);
    }

    private void ensureAbsolutePosition() {
        FrameLayout.LayoutParams params = frameLayoutParams();
        if (params.gravity == (Gravity.TOP | Gravity.LEFT)) {
            return;
        }
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.leftMargin = getLeft();
        params.topMargin = getTop();
        setLayoutParams(params);
    }

    private void constrainToParent() {
        if (movementMode != MovementMode.DRAGGABLE || getParent() == null) {
            return;
        }
        ensureAbsolutePosition();
        FrameLayout.LayoutParams params = frameLayoutParams();
        moveTo(params.leftMargin, params.topMargin);
    }

    private void moveTo(int left, int top) {
        if (!(getParent() instanceof View parent)) {
            return;
        }
        int edge = UIUtils.dp2pxInt(SCREEN_EDGE_DP);
        int maxLeft = Math.max(edge, parent.getWidth() - getWidth() - edge);
        int maxTop = Math.max(edge, parent.getHeight() - getHeight() - edge);
        FrameLayout.LayoutParams params = frameLayoutParams();
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.leftMargin = clamp(left, edge, maxLeft);
        params.topMargin = clamp(top, edge, maxTop);
        setLayoutParams(params);
    }

    private FrameLayout.LayoutParams frameLayoutParams() {
        if (!(getLayoutParams() instanceof FrameLayout.LayoutParams params)) {
            throw new IllegalStateException("ModalWindowView requires a FrameLayout parent");
        }
        return params;
    }

    private int resolveMeasuredDimension(int naturalPx, float preferredDp, float minimumDp,
                                         float maximumDp, int measureSpec) {
        int mode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        if (mode == MeasureSpec.EXACTLY) {
            return specSize;
        }

        int target = preferredDp > 0.0f ? UIUtils.dp2pxInt(preferredDp) : naturalPx;
        target = Math.max(target, UIUtils.dp2pxInt(minimumDp));
        if (maximumDp > 0.0f) {
            target = Math.min(target, UIUtils.dp2pxInt(maximumDp));
        }
        if (mode == MeasureSpec.AT_MOST) {
            int edgeBudget = UIUtils.dp2pxInt(SCREEN_EDGE_DP * 2.0f);
            target = Math.min(target, Math.max(0, specSize - edgeBudget));
        }
        return Math.max(0, target);
    }

    private static void validateSizeConstraints(float minimumWidthDp, float minimumHeightDp,
                                                float maximumWidthDp, float maximumHeightDp) {
        if (maximumWidthDp > 0.0f && minimumWidthDp > maximumWidthDp) {
            throw new IllegalArgumentException("Minimum width cannot exceed maximum width");
        }
        if (maximumHeightDp > 0.0f && minimumHeightDp > maximumHeightDp) {
            throw new IllegalArgumentException("Minimum height cannot exceed maximum height");
        }
    }

    private static float nonNegative(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    private static ShapeDrawable windowBackground(Style style) {
        return rect(style.windowColor(), style.cornerRadiusDp(), 1, style.borderColor());
    }

    private static ShapeDrawable rect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeWidthDp > 0) {
            drawable.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        }
        return drawable;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
