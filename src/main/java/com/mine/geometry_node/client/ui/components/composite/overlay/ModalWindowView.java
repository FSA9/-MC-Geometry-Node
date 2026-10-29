package com.mine.geometry_node.client.ui.components.composite.overlay;

import static com.mine.geometry_node.client.ui.components.base.drawable.UiDrawables.rect;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.overlay.modal.UiModal;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.overlay.OverlayCloseReason;
import com.mine.geometry_node.client.ui.overlay.OverlayHandle;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.overlay.UiWindowLifetime;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import java.util.Objects;

/** Reusable window chrome for modal overlays mounted through the shared host protocol. */
public class ModalWindowView extends LinearLayout implements UiModal {
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
            float cornerRadiusDp,
            int closeFocusColor
    ) {
        /** Retains the original chrome and close-button behavior for explicit legacy styles. */
        public Style(int windowColor, int titleBarColor, int borderColor, int textColor,
                     int mutedTextColor, int closeHoverColor, float cornerRadiusDp) {
            this(windowColor, titleBarColor, borderColor, textColor, mutedTextColor, closeHoverColor,
                    cornerRadiusDp, UiThemes.EDITOR.border().focus());
        }

        public static Style defaults() {
            return forTheme(UiThemes.EDITOR);
        }

        public static Style forTheme(UiTheme theme) {
            Objects.requireNonNull(theme, "theme");
            return new Style(
                    theme.surface().panel(), theme.surface().modalTitle(), theme.border().strong(),
                    theme.text().normal(), theme.text().muted(), theme.control().hoverOverlay(),
                    4.0f, theme.border().focus()
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
    private UiWindowLifetime sessionOwner;
    private AutoCloseable ownerSubscription;
    private boolean suspendedByOwner;
    private boolean manuallySuspended;
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
        setBackground(rect(style.windowColor(), style.cornerRadiusDp(), 1, style.borderColor()));
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

    /** Opt in to the logical source window lifetime; temporary View detachment is irrelevant. */
    public final void bindSessionOwner(View anchor) {
        if (overlayHandle != null) throw new IllegalStateException("Bind owner before showing the window");
        sessionOwner = UiWindowLifetime.find(anchor);
    }

    private void reconcileOwner(UiWindowLifetime.State state) {
        if (overlayHandle == null || !overlayHandle.isOpen()) return;
        switch (state) {
            case HIDDEN -> {
                // A covered parent is effectively suspended already, but still needs an explicit
                // owner suspension so closing its child cannot reveal it while the source is hidden.
                suspendedByOwner = !manuallySuspended;
                overlayHandle.suspend();
            }
            case VISIBLE -> {
                if (suspendedByOwner) {
                    suspendedByOwner = false;
                    if (!manuallySuspended) overlayHandle.resume();
                }
            }
            case CLOSED -> overlayHandle.requestClose(OverlayCloseReason.OWNER_CLOSED);
        }
    }

    private void releaseOwner() {
        if (ownerSubscription == null) return;
        try { ownerSubscription.close(); }
        catch (Exception failure) { throw new IllegalStateException("Cannot release window owner", failure); }
        finally { ownerSubscription = null; }
    }

    public final boolean requestClose() {
        return overlayHandle != null
                && overlayHandle.requestClose(OverlayCloseReason.PROGRAMMATIC);
    }

    /** Hides this window and its descendants without closing or validating its form. */
    public final boolean suspendWindow() {
        boolean suspended = overlayHandle != null && overlayHandle.suspend();
        if (suspended) manuallySuspended = true;
        return suspended;
    }

    /** Restores the retained window branch without invoking onWindowShown again. */
    public final boolean resumeWindow() {
        if (sessionOwner != null && sessionOwner.state() != UiWindowLifetime.State.VISIBLE) return false;
        boolean resumed = overlayHandle != null && overlayHandle.resume();
        if (resumed) { manuallySuspended = false; suspendedByOwner = false; }
        return resumed;
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
        if (sessionOwner != null && sessionOwner.state() == UiWindowLifetime.State.CLOSED) {
            overlayHandle.requestClose(OverlayCloseReason.OWNER_CLOSED);
            return;
        }
        onWindowShown();
        if (sessionOwner != null && overlayHandle != null && overlayHandle.isOpen()) {
            ownerSubscription = sessionOwner.observe(this::reconcileOwner);
            reconcileOwner(sessionOwner.state());
        }
        post(this::constrainToParent);
    }

    @Override
    public final void onSuspended() {
        dragging = false;
        onWindowSuspended();
    }

    @Override
    public final void onResumed() {
        onWindowResumed();
        post(this::constrainToParent);
    }

    @Override
    public final void onClosed(OverlayCloseReason reason) {
        dragging = false;
        releaseOwner();
        onWindowClosed(reason);
    }

    @Override
    public final void onDestroyed() {
        releaseOwner();
        overlayHandle = null;
        sessionOwner = null;
        onWindowDestroyed();
    }

    protected void onWindowShown() {
    }

    protected void onWindowSuspended() {
    }

    protected void onWindowResumed() {
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
        UiIconButton.Style buttonStyle = new UiIconButton.Style(2.0f, 0.0f, 0,
                style.closeHoverColor(), style.closeHoverColor(), 0, 0,
                0, style.mutedTextColor(), style.mutedTextColor(), 0, 0,
                CLOSE_ICON_SIZE_DP, 0.5f, style.closeFocusColor());
        UiIconButton button = new UiIconButton(context,
                new SvgIconView(context, SvgIconView.Icon.CLOSE, style.mutedTextColor()), buttonStyle);
        button.setOnClickListener(view -> onCloseRequested());
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
