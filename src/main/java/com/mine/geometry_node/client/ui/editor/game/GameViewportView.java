package com.mine.geometry_node.client.ui.editor.game;

import com.mine.geometry_node.client.runtime.game.EditViewportCamera;
import com.mine.geometry_node.client.runtime.game.EditViewportFlightInput;
import com.mine.geometry_node.client.runtime.game.EditViewportRuntime;
import com.mine.geometry_node.client.runtime.game.GameFrameImage;
import com.mine.geometry_node.client.runtime.game.GameViewportInput;
import com.mine.geometry_node.client.runtime.game.GameViewTiming;
import com.mine.geometry_node.client.ui.shortcut.KeyScope;
import com.mine.geometry_node.client.ui.shortcut.ScopedKeyManager;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
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
        if (!active) cancelInteraction();
        displayedBounds = GameViewportLayout.fit(0, 0, 0, 0);
        invalidate();
    }

    boolean isFrameAvailable() {
        if (!active || Boolean.getBoolean("gn.standalone")) return false;
        return editing() ? EditViewportRuntime.isAvailable(inputOwner)
                : !EditViewportRuntime.isRendering() && GameFrameImage.isAvailable();
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
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) cancelInteraction();
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
        displayedBounds = GameViewportLayout.fit(0, 0, 0, 0);
        if (!isFrameAvailable()) {
            cancelInteraction();
            return;
        }
        var size = editing() ? EditViewportRuntime.size(inputOwner) : GameFrameImage.size();
        var bounds = GameViewportLayout.fit(getWidth(), getHeight(), size.width(), size.height());
        boolean drawn = editing()
                ? EditViewportRuntime.draw(inputOwner, canvas, size, bounds.left(), bounds.top(), bounds.right(), bounds.bottom())
                : GameFrameImage.draw(canvas, size, bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        if (drawn) {
            displayedBounds = bounds;
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
        cancelInteraction();
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
