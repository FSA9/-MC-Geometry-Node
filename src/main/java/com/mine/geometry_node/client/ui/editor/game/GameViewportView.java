package com.mine.geometry_node.client.ui.editor.game;

import com.mine.geometry_node.client.runtime.game.GameFrameImage;
import com.mine.geometry_node.client.runtime.game.GameViewportInput;
import com.mine.geometry_node.client.runtime.game.GameViewTiming;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;

/** A normal canvas view so editor menus, tooltips and modal scrims composite above the game. */
final class GameViewportView extends View {
    private final Object inputOwner;
    private final Runnable onInteraction;
    private boolean activationPressed;
    private boolean active;
    private long firstFrameStart;
    private GameViewportLayout displayedBounds = GameViewportLayout.fit(0, 0, 0, 0);

    GameViewportView(Context context, Object inputOwner, Runnable onInteraction) {
        super(context);
        this.inputOwner = inputOwner;
        this.onInteraction = onInteraction;
        setClickable(true);
        setFocusable(true);
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(0xFF080808);
        setBackground(background);
    }

    void setActive(boolean active) {
        this.active = active;
        firstFrameStart = active ? GameViewTiming.start() : 0;
        if (!active) activationPressed = false;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        displayedBounds = GameViewportLayout.fit(0, 0, 0, 0);
        if (!active || Boolean.getBoolean("gn.standalone") || !GameFrameImage.isAvailable()) return;
        var size = GameFrameImage.size();
        var bounds = GameViewportLayout.fit(getWidth(), getHeight(), size.width(), size.height());
        if (GameFrameImage.draw(canvas, size, bounds.left(), bounds.top(), bounds.right(), bounds.bottom())) {
            displayedBounds = bounds;
            GameViewTiming.finish("activation to first UI frame recording", firstFrameStart, true);
            firstFrameStart = 0;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN -> {
                activationPressed = active && event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)
                        && displayedBounds.contains(event.getX(), event.getY());
                return activationPressed;
            }
            case MotionEvent.ACTION_UP -> {
                boolean capture = activationPressed && active
                        && displayedBounds.contains(event.getX(), event.getY());
                activationPressed = false;
                if (capture) {
                    requestFocus();
                    onInteraction.run();
                    GameViewportInput.requestCapture(inputOwner);
                    performClick();
                }
                return capture;
            }
            case MotionEvent.ACTION_CANCEL -> {
                activationPressed = false;
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!displayedBounds.contains(event.getX(), event.getY())) activationPressed = false;
                return activationPressed;
            }
            default -> { return super.onTouchEvent(event); }
        }
    }
}
