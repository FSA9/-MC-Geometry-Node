package com.mine.geometry_node.client.ui.editor.game;

import com.mine.geometry_node.client.ui.editor.game.gizmo.ViewportOrientationOverlay;
import com.mine.geometry_node.client.ui.editor.game.input.GameViewportShortcuts;
import com.mine.geometry_node.client.ui.editor.game.layout.GameViewportLayout;
import com.mine.geometry_node.client.ui.editor.game.layout.GameViewportPixelDemand;

import com.mine.geometry_node.client.runtime.game.EditViewportCamera;
import com.mine.geometry_node.client.runtime.game.EditViewportFlightInput;
import com.mine.geometry_node.client.runtime.game.EditViewportRuntime;
import com.mine.geometry_node.client.runtime.game.GameFrameImage;
import com.mine.geometry_node.client.runtime.game.GameViewportInput;
import com.mine.geometry_node.client.runtime.game.GameViewTiming;
import com.mine.geometry_node.client.ui.shortcut.KeyScope;
import com.mine.geometry_node.client.ui.shortcut.ScopedKeyManager;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Matrix;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;

/** Canvas composition and local input coordinates preserve workspace transforms and clipping. */
final class GameViewportView extends View {
    private final ScopedKeyManager<GameViewportShortcuts.Action, GameViewportView> shortcuts;
    private final GameViewportWindow inputOwner;
    private final EditViewportCamera editCamera;
    private final Runnable onInteraction;
    private final Runnable onProjectionChanged;
    private final Runnable onSizeChanged;
    private final Rect visibleBounds = new Rect();
    private final Matrix localToRoot = new Matrix();
    private final ViewportOrientationOverlay orientation = new ViewportOrientationOverlay();
    private boolean orientationPressed;
    private EditViewportCamera.Axis pressedAxis;
    private boolean activationPressed;
    private boolean navigating;
    private boolean panning;
    private float lastX;
    private float lastY;
    private boolean active;
    private boolean handlingRepeat;
    private long firstFrameStart;
    private GameViewportLayout displayedBounds = GameViewportLayout.fit(0, 0, 0, 0);

    GameViewportView(Context context, GameViewportWindow inputOwner, EditViewportCamera editCamera,
                     Runnable onInteraction, Runnable onProjectionChanged, Runnable onSizeChanged) {
        super(context);
        this.inputOwner = inputOwner;
        this.editCamera = editCamera;
        this.onInteraction = onInteraction;
        this.onProjectionChanged = onProjectionChanged;
        this.onSizeChanged = onSizeChanged;
        shortcuts = new ScopedKeyManager<>(this, KeyScope.GAME_VIEW,
                () -> GameViewportShortcuts.ACTIONS, this::executeShortcut);
        shortcuts.dispose(); // Subscribe only while attached to the workspace.
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(0xFF080808);
        setBackground(background);
    }

    void setActive(boolean active) {
        this.active = active;
        firstFrameStart = active ? GameViewTiming.start() : 0;
        if (!active) releaseInteraction();
        displayedBounds = GameViewportLayout.fit(0, 0, 0, 0);
        invalidate();
    }

    boolean isFrameAvailable() {
        if (!active || Boolean.getBoolean("gn.standalone")) return false;
        return editing() ? EditViewportRuntime.isAvailable(inputOwner)
                : GameFrameImage.isAvailable();
    }

    void publishPixelDemand() {
        if (!active || !editing() || Boolean.getBoolean("gn.standalone")) return;
        var frame = EditViewportRuntime.framebufferSize();
        double demand = 0;
        if (getGlobalVisibleRect(visibleBounds)) {
            // transformMatrixToGlobal concatenates, so reset our reusable matrix each sample.
            localToRoot.setIdentity();
            transformMatrixToGlobal(localToRoot);
            View root = getRootView();
            demand = GameViewportPixelDemand.scale(getWidth(), getHeight(), frame.width(), frame.height(),
                    root.getWidth(), root.getHeight(), localToRoot, visibleBounds);
        }
        EditViewportRuntime.setDemand(inputOwner, demand);
    }

    void releaseInteraction() {
        cancelInteraction();
        if (!Boolean.getBoolean("gn.standalone")) GameViewportInput.release(inputOwner);
    }

    private boolean editing() { return inputOwner.mode().usesEditorCamera(); }

    private boolean canNavigate() {
        return active && editing() && !Boolean.getBoolean("gn.standalone")
                && EditViewportRuntime.navigateAllowed(inputOwner) && isFrameAvailable();
    }

    private void cancelInteraction() {
        EditViewportFlightInput.release(inputOwner);
        activationPressed = false;
        navigating = false;
        panning = false;
        orientationPressed = false;
        pressedAxis = null;
        orientation.reset();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) releaseInteraction();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        cancelInteraction();
        displayedBounds = GameViewportLayout.fit(0, 0, 0, 0);
        onSizeChanged.run();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        orientation.hide();
        displayedBounds = GameViewportLayout.fit(0, 0, 0, 0);
        if (!isFrameAvailable()) {
            cancelInteraction();
            return;
        }
        var size = editing() ? EditViewportRuntime.size(inputOwner) : GameFrameImage.size();
        // Integer target rounding must not change the display or input aspect ratio.
        // The texture snapshot still guards channel.draw against a concurrent resize.
        var framebuffer = EditViewportRuntime.framebufferSize();
        if (!framebuffer.available()) {
            cancelInteraction();
            return;
        }
        var bounds = GameViewportLayout.fit(getWidth(), getHeight(), framebuffer.width(), framebuffer.height());
        boolean drawn = editing()
                ? EditViewportRuntime.draw(inputOwner, canvas, size, bounds.left(), bounds.top(), bounds.right(), bounds.bottom())
                : GameFrameImage.draw(canvas, size, bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        if (drawn) {
            displayedBounds = bounds;
            if (editing()) {
                var pose = editCamera.snapshot();
                orientation.layout(bounds, UIUtils.dp2px(1), pose.yaw(), pose.pitch());
                orientation.draw(canvas);
            }
            GameViewTiming.finish("activation to first UI frame recording", firstFrameStart, true);
            firstFrameStart = 0;
        }
        if (navigating && !canNavigate()) cancelInteraction();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            cancelInteraction();
            return true;
        }
        if (handleOrientationTouch(event)) return true;
        if (inputOwner.mode() == ViewportMode.EDIT) return handleEditTouch(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                activationPressed = isFrameAvailable() && event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)
                        && displayedBounds.contains(event.getX(), event.getY());
                return activationPressed;
            }
            case MotionEvent.ACTION_UP -> {
                boolean capture = activationPressed && isFrameAvailable()
                        && displayedBounds.contains(event.getX(), event.getY());
                activationPressed = false;
                if (capture) {
                    requestFocus();
                    onInteraction.run();
                    if (inputOwner.mode() == ViewportMode.FLY) {
                        if (canNavigate()) EditViewportFlightInput.requestCapture(inputOwner, editCamera);
                    } else GameViewportInput.requestCapture(inputOwner);
                    performClick();
                }
                return capture;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!displayedBounds.contains(event.getX(), event.getY())) activationPressed = false;
                return activationPressed;
            }
            default -> { return super.onTouchEvent(event); }
        }
    }

    /** Consume the entire overlay gesture so a flight-mode click never captures the mouse. */
    private boolean handleOrientationTouch(MotionEvent event) {
        float x = event.getX(), y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                if (!canNavigate() || !orientation.contains(x, y)) return false;
                orientationPressed = true;
                pressedAxis = event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)
                        ? orientation.hit(x, y) : null;
                activationPressed = false;
                requestFocus();
                onInteraction.run();
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!orientationPressed) return false;
                if (orientation.hit(x, y) != pressedAxis) pressedAxis = null;
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                if (!orientationPressed) return false;
                var axis = pressedAxis;
                orientationPressed = false;
                pressedAxis = null;
                if (axis != null && canNavigate() && orientation.hit(x, y) == axis) {
                    editCamera.alignToAxis(axis, inputOwner.mode() == ViewportMode.FLY);
                    onInteraction.run();
                    invalidate();
                    performClick();
                }
                return true;
            }
            default -> { return false; }
        }
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        var hovered = event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT || !canNavigate()
                ? null : orientation.hit(event.getX(), event.getY());
        if (orientation.setHovered(hovered)) invalidate();
        return super.onHoverEvent(event) || hovered != null;
    }

    private boolean handleEditTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                if (!canNavigate() || !displayedBounds.contains(event.getX(), event.getY())) return false;
                navigating = event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)
                        || event.getActionButton() == MotionEvent.BUTTON_TERTIARY;
                // ModernUI's native ACTION_MOVE currently omits modifier flags.
                // Seed Shift from DOWN and maintain it with focused key events.
                panning = event.isShiftPressed();
                activationPressed = event.isButtonPressed(MotionEvent.BUTTON_PRIMARY);
                if (!navigating && !activationPressed) return false;
                lastX = event.getX();
                lastY = event.getY();
                requestFocus();
                onInteraction.run();
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!canNavigate()) {
                    cancelInteraction();
                    return false;
                }
                if (navigating && !event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) navigating = false;
                if (navigating) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    // Events are already inverse-transformed into the view's local space.
                    // Use the rendered image height so letterboxing cannot change pan speed.
                    if (panning) editCamera.pan(dx, dy, displayedBounds.bottom() - displayedBounds.top());
                    else editCamera.orbit(dx, dy);
                    lastX = event.getX();
                    lastY = event.getY();
                    invalidate();
                }
                return navigating || activationPressed;
            }
            case MotionEvent.ACTION_UP -> {
                boolean handled = navigating || activationPressed;
                cancelInteraction();
                if (handled) performClick();
                return handled;
            }
            default -> { return super.onTouchEvent(event); }
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (editing() && orientation.contains(event.getX(), event.getY())
                && event.getActionMasked() == MotionEvent.ACTION_SCROLL) return true;
        if (inputOwner.mode() == ViewportMode.EDIT
                && event.getActionMasked() == MotionEvent.ACTION_SCROLL && canNavigate()
                && displayedBounds.contains(event.getX(), event.getY())) {
            float steps = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (steps == 0 || !Float.isFinite(steps)) return false;
            editCamera.dolly(steps);
            requestFocus();
            onInteraction.run();
            invalidate();
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    boolean dispatchShortcut(KeyEvent event) {
        if (!canNavigate() || EditViewportFlightInput.isCaptured()) return false;
        handlingRepeat = event.getRepeatCount() != 0;
        return shortcuts.onKeyDown(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (navigating && (keyCode == KeyEvent.KEY_LEFT_SHIFT || keyCode == KeyEvent.KEY_RIGHT_SHIFT)) {
            panning = true;
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void executeShortcut(GameViewportShortcuts.Action action) {
        if (handlingRepeat) return;
        if (action == GameViewportShortcuts.Action.FLY) {
            cancelInteraction();
            onInteraction.run();
            inputOwner.selectFlightMode();
        } else if (inputOwner.mode() == ViewportMode.EDIT) {
            editCamera.toggleOrthographic();
            onProjectionChanged.run();
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        shortcuts.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        shortcuts.dispose();
        releaseInteraction();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (navigating && (keyCode == KeyEvent.KEY_LEFT_SHIFT || keyCode == KeyEvent.KEY_RIGHT_SHIFT)) {
            panning = false;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }
}
