package com.mine.geometry_node.client.ui.editor.game;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.runtime.game.snap.ViewportSnapSettings;
import com.mine.geometry_node.client.ui.editor.game.gizmo.ViewportOrientationOverlay;
import com.mine.geometry_node.client.ui.editor.game.input.GameViewportShortcuts;
import com.mine.geometry_node.client.ui.editor.game.layout.GameViewportLayout;
import com.mine.geometry_node.client.ui.editor.game.layout.GameViewportPixelDemand;
import com.mine.geometry_node.client.runtime.game.camera.EditViewportCamera;
import com.mine.geometry_node.client.runtime.game.input.EditViewportFlightInput;
import com.mine.geometry_node.client.runtime.game.render.EditViewportRuntime;
import com.mine.geometry_node.client.integration.game.GameFrameImage;
import com.mine.geometry_node.client.runtime.game.input.GameViewportInput;
import com.mine.geometry_node.client.runtime.game.diagnostic.GameViewTiming;
import com.mine.geometry_node.client.input.binding.KeyScope;
import com.mine.geometry_node.client.ui.shortcut.ScopedKeyManager;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.components.composite.menu.UiRadialMenu;
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
    private final com.mine.geometry_node.client.ui.editor.game.scene.ViewportSceneInteraction scene;
    private final Matrix inputMatrix = new Matrix();
    private final float[] inputPoint = new float[2];
    private final ScopedKeyManager<GameViewportShortcuts.Action, GameViewportView> shortcuts;
    private final GameViewportWindow inputOwner;
    private final EditViewportCamera editCamera;
    private final Runnable onInteraction;
    private final Runnable onSizeChanged;
    private final Rect visibleBounds = new Rect();
    private final Matrix localToRoot = new Matrix();
    private final ViewportOrientationOverlay orientation = new ViewportOrientationOverlay();
    private boolean orientationPressed;
    private EditViewportCamera.Axis pressedAxis;
    private boolean activationPressed;
    private boolean navigating;
    private boolean contextPressed;
    private boolean panning;
    private float pointerRootX = Float.NaN, pointerRootY = Float.NaN;
    private float lastX;
    private float lastY;
    private boolean active;
    private boolean handlingRepeat;
    private long firstFrameStart;
    private GameViewportLayout displayedBounds = GameViewportLayout.fit(0, 0, 0, 0);

    GameViewportView(Context context, GameViewportWindow inputOwner, EditViewportCamera editCamera,
                     Runnable onInteraction, Runnable onSizeChanged) {
        super(context);
        this.inputOwner = inputOwner;
        this.editCamera = editCamera;
        scene = new com.mine.geometry_node.client.ui.editor.game.scene.ViewportSceneInteraction(inputOwner.visualization(), this::selectionChanged);
        scene.setSnapQueryPublisher(query -> {
            if(!Boolean.getBoolean("gn.standalone"))EditViewportRuntime.setSnapQuery(inputOwner,query);
        });
        this.onInteraction = onInteraction;
        this.onSizeChanged = onSizeChanged;
        shortcuts = new ScopedKeyManager<>(this, KeyScope.GAME_VIEW,
                this::availableShortcuts, this::executeShortcut);
        shortcuts.dispose(); // Subscribe only while attached to the workspace.
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(UiThemes.EDITOR.surface().canvas());
        setBackground(background);
    }

    private void selectionChanged() {
        publishSelection();
        invalidate();
    }

    private void publishSelection() {
        if (!Boolean.getBoolean("gn.standalone"))
            scene.publishWorldSelection(inputOwner, active && inputOwner.mode() == ViewportMode.EDIT);
    }

    void setActive(boolean active) {
        this.active = active;
        publishSelection();
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
        publishSelection();
    }

    void releaseInteraction() {
        inputOwner.closeViewMenu();
        inputOwner.closeSceneMenus();
        cancelInteraction();
        if (!Boolean.getBoolean("gn.standalone")) GameViewportInput.release(inputOwner);
    }

    private boolean editing() { return inputOwner.mode().usesEditorCamera(); }

    boolean canNavigate() {
        return active && editing() && !Boolean.getBoolean("gn.standalone")
                && EditViewportRuntime.navigateAllowed(inputOwner) && isFrameAvailable();
    }

    private void cancelInteraction() {
        scene.cancel();
        EditViewportFlightInput.release(inputOwner);
        activationPressed = false;
        navigating = false;
        contextPressed = false;
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
        inputOwner.closeViewMenu();
        inputOwner.closeSceneMenus();
        cancelInteraction();
        pointerRootX = pointerRootY = Float.NaN;
        displayedBounds = GameViewportLayout.fit(0, 0, 0, 0);
        onSizeChanged.run();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        orientation.hide();
        displayedBounds = GameViewportLayout.fit(0, 0, 0, 0);
        if (!isFrameAvailable()) {
            scene.clearFrame();
            cancelInteraction();
            return;
        }
        var size = editing() ? EditViewportRuntime.size(inputOwner) : GameFrameImage.size();
        // Integer target rounding must not change the display or input aspect ratio.
        // The texture snapshot still guards channel.draw against a concurrent resize.
        var framebuffer = EditViewportRuntime.framebufferSize();
        if (!framebuffer.available()) {
            scene.clearFrame();
            cancelInteraction();
            return;
        }
        var bounds = GameViewportLayout.fit(getWidth(), getHeight(), framebuffer.width(), framebuffer.height());
        var presented = editing() ? GameFrameImage.drawPresented(inputOwner, canvas, size, bounds.left(), bounds.top(), bounds.right(), bounds.bottom()) : null;
        boolean drawn = editing() ? presented != null
                : GameFrameImage.draw(canvas, size, bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        if (drawn) {
            displayedBounds = bounds;
            if (editing()) {
                scene.presented(presented, bounds);
                publishSelection();
                if (inputOwner.mode() == ViewportMode.EDIT) scene.draw(canvas);
                var pose = presented.camera();
                orientation.layout(bounds, UIUtils.dp2px(1), pose.yaw(), pose.pitch());
                orientation.draw(canvas);
            }
            GameViewTiming.finish("activation to first UI frame recording", firstFrameStart, true);
            firstFrameStart = 0;
        }
        if (!drawn) scene.clearFrame();
        if (navigating && !canNavigate()) cancelInteraction();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        pointerRootX = event.getRawX();
        pointerRootY = event.getRawY();
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            cancelInteraction();
            return true;
        }
        if (scene.busy() && event.getActionMasked()==MotionEvent.ACTION_DOWN
                && (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)||event.getActionButton()==MotionEvent.BUTTON_SECONDARY)) {
            cancelInteraction();return true;
        }
        if(scene.transforming()){
            scene.transformMove(eventX(event),eventY(event));
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN && event.isButtonPressed(MotionEvent.BUTTON_PRIMARY))scene.confirmTransform();
            return true;
        }
        if (!scene.busy() && handleOrientationTouch(event)) return true;
        if (inputOwner.mode() == ViewportMode.EDIT) return handleEditTouch(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                activationPressed = isFrameAvailable() && event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)
                        && displayedBounds.contains(eventX(event), eventY(event));
                return activationPressed;
            }
            case MotionEvent.ACTION_UP -> {
                boolean capture = activationPressed && isFrameAvailable()
                        && displayedBounds.contains(eventX(event), eventY(event));
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
                if (!displayedBounds.contains(eventX(event), eventY(event))) activationPressed = false;
                return activationPressed;
            }
            default -> { return super.onTouchEvent(event); }
        }
    }

    /** Consume the entire overlay gesture so a flight-mode click never captures the mouse. */
    private boolean handleOrientationTouch(MotionEvent event) {
        float x = eventX(event), y = eventY(event);
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
        pointerRootX = event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT ? Float.NaN : event.getRawX();
        pointerRootY = event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT ? Float.NaN : event.getRawY();
        if(scene.transforming()){scene.transformMove(eventX(event),eventY(event));return true;}
        var hovered = event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT || !canNavigate()
                ? null : orientation.hit(eventX(event), eventY(event));
        if (orientation.setHovered(hovered)) invalidate();
        if (inputOwner.mode() == ViewportMode.EDIT) scene.hover(event.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT?Float.NaN:eventX(event),eventY(event));
        return super.onHoverEvent(event) || hovered != null;
    }

    private boolean handleEditTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                if (!canNavigate() || !displayedBounds.contains(eventX(event), eventY(event))) return false;
                if(!contextPressed){handlingRepeat=false;contextPressed=shortcuts.onMouseDown(event);}
                if(contextPressed){requestFocus();onInteraction.run();return true;}
                navigating = event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)
                        || event.getActionButton() == MotionEvent.BUTTON_TERTIARY;
                // ModernUI's native ACTION_MOVE currently omits modifier flags.
                // Seed Shift from DOWN and maintain it with focused key events.
                panning = event.isShiftPressed();
                activationPressed = event.isButtonPressed(MotionEvent.BUTTON_PRIMARY);
                if (navigating) scene.cancel();
                else if (activationPressed) scene.down(eventX(event), eventY(event), event.isShiftPressed(), event.isCtrlPressed());
                if (!navigating && !activationPressed) return false;
                lastX = eventX(event);
                lastY = eventY(event);
                requestFocus();
                onInteraction.run();
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!canNavigate()) {
                    cancelInteraction();
                    return false;
                }
                if (scene.busy()) return scene.move(eventX(event),eventY(event));
                if (navigating && !event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) navigating = false;
                if (navigating) {
                    float dx = eventX(event) - lastX;
                    float dy = eventY(event) - lastY;
                    // Events are already inverse-transformed into the view's local space.
                    // Use the rendered image height so letterboxing cannot change pan speed.
                    if (panning) editCamera.pan(dx, dy, displayedBounds.bottom() - displayedBounds.top());
                    else editCamera.orbit(dx, dy);
                    lastX = eventX(event);
                    lastY = eventY(event);
                    invalidate();
                }
                return navigating || activationPressed || contextPressed;
            }
            case MotionEvent.ACTION_UP -> {
                boolean handled = navigating || activationPressed || contextPressed;
                contextPressed=false;
                if (scene.busy()) scene.up(eventX(event),eventY(event));
                cancelInteraction();
                if (handled) performClick();
                return handled;
            }
            default -> { return super.onTouchEvent(event); }
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        pointerRootX = event.getRawX(); pointerRootY = event.getRawY();
        if(scene.busy() && event.getActionMasked()==MotionEvent.ACTION_SCROLL)return true;
        if(scene.busy() && event.getActionMasked()==MotionEvent.ACTION_BUTTON_PRESS
                && event.getActionButton()==MotionEvent.BUTTON_SECONDARY){cancelInteraction();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_BUTTON_RELEASE&&contextPressed){
            boolean handled=contextPressed;contextPressed=false;if(handled)return true;
        }
        if(inputOwner.mode()==ViewportMode.EDIT&&canNavigate()&&!scene.busy()
                &&!orientation.contains(eventX(event),eventY(event))
                &&event.getActionMasked()==MotionEvent.ACTION_BUTTON_PRESS){
            if(!contextPressed){handlingRepeat=false;contextPressed=shortcuts.onMouseDown(event);}
            if(contextPressed){requestFocus();onInteraction.run();return true;}
        }
        if (editing() && orientation.contains(eventX(event), eventY(event))
                && event.getActionMasked() == MotionEvent.ACTION_SCROLL) return true;
        if (inputOwner.mode() == ViewportMode.EDIT
                && event.getActionMasked() == MotionEvent.ACTION_SCROLL && canNavigate()
                && displayedBounds.contains(eventX(event), eventY(event))) {
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

    void showViewMenu(UiRadialMenu menu) {
        if (Float.isFinite(pointerRootX) && Float.isFinite(pointerRootY)) {
            menu.showAtRoot(this, pointerRootX, pointerRootY);
        } else {
            menu.showAtCenter(this);
        }
    }

    void prepareViewMenu() {
        scene.cancel();
        activationPressed = navigating = panning = orientationPressed = false;
        pressedAxis = null;
        orientation.reset();
        requestFocus();
        onInteraction.run();
    }

    void setMoveSnap(ViewportSnapSettings settings){scene.setSnapSettings(settings);}

    String transformLabel(){return scene.transformLabel();}

    void alignView(EditViewportCamera.Axis axis) {
        if (!canNavigate()) return;
        editCamera.alignToAxis(axis, inputOwner.mode() == ViewportMode.FLY, false);
        onInteraction.run();
        invalidate();
    }

    private java.util.List<com.mine.geometry_node.client.ui.shortcut.ScopedAction<GameViewportShortcuts.Action>> availableShortcuts() {
        return GameViewportShortcuts.ACTIONS.stream().filter(action -> {
            if (inputOwner.mode()!=ViewportMode.EDIT) return false;
            if(scene.transforming())return switch(action.id()){case AXIS_X,AXIS_Y,AXIS_Z,CONFIRM,CANCEL,SNAP_TOGGLE -> true;default -> false;};
            return switch(action.id()){case AXIS_X,AXIS_Y,AXIS_Z,CONFIRM -> false;case CANCEL -> scene.busy();default -> true;};
        }).toList();
    }

    boolean dispatchShortcut(KeyEvent event) {
        if (!canNavigate() || EditViewportFlightInput.isCaptured()) return false;
        if (inputOwner.mode()==ViewportMode.EDIT && scene.transforming()
                && !event.isControlPressed() && !event.isAltPressed() && !event.isSuperPressed()
                && !event.isShiftPressed() && scene.transformNumberKey(event.getKeyCode())) {
            invalidate();return true;
        }
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
        if (inputOwner.mode() == ViewportMode.EDIT) {
            switch (action) {
                case MOVE,ROTATE,SCALE -> {
                    boolean valid=UIUtils.rootToLocal(this,pointerRootX,pointerRootY,inputMatrix,inputPoint);
                    scene.beginTransform(action.name(),valid?inputPoint[0]:Float.NaN,valid?inputPoint[1]:Float.NaN);
                }
                case AXIS_X -> scene.transformAxis(1);
                case AXIS_Y -> scene.transformAxis(2);
                case AXIS_Z -> scene.transformAxis(4);
                case SNAP_TOGGLE -> inputOwner.toggleMoveSnap();
                case CONFIRM -> scene.confirmTransform();
                case PROJECTION -> inputOwner.toggleProjection();
                case ADD -> {
                    scene.cancel();
                    boolean valid = UIUtils.rootToLocal(this, pointerRootX, pointerRootY, inputMatrix, inputPoint);
                    inputOwner.openCreateMenu(pointerRootX, pointerRootY,
                            scene.creationPosition(valid ? inputPoint[0] : Float.NaN, valid ? inputPoint[1] : Float.NaN));
                }
                case FOCUS -> scene.focus(editCamera);
                case ALIGN_ENTITY -> {
                    if (scene.alignToSelectedEntity(inputOwner)) onInteraction.run();
                }
                case ENTITY_MOVE -> {
                    if(UIUtils.rootToLocal(this,pointerRootX,pointerRootY,inputMatrix,inputPoint))
                        scene.contextAction(inputPoint[0],inputPoint[1]);
                }
                case SELECT_ALL -> { scene.selectAll(); selectionChanged(); }
                case COPY -> scene.copySelected();
                case PASTE -> scene.paste();
                case DELETE -> scene.deleteSelected();
                case UNDO -> { scene.cancel(); var history = inputOwner.historyTarget(); if (history != null) history.undo(); }
                case REDO -> { scene.cancel(); var history = inputOwner.historyTarget(); if (history != null) history.redo(); }
                case CANCEL -> { scene.cancel(); inputOwner.closeSceneMenus(); }
                default -> {}
            }
            invalidate();
        }
    }

    private float eventX(MotionEvent event) {
        return UIUtils.rootToLocal(this,event.getRawX(),event.getRawY(),inputMatrix,inputPoint) ? inputPoint[0] : Float.NaN;
    }
    private float eventY(MotionEvent event) {
        return UIUtils.rootToLocal(this,event.getRawX(),event.getRawY(),inputMatrix,inputPoint) ? inputPoint[1] : Float.NaN;
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
