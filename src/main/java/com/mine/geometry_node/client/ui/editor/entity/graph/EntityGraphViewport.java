package com.mine.geometry_node.client.ui.editor.entity.graph;

import com.mine.geometry_node.client.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.input.binding.KeyBinding;
import com.mine.geometry_node.client.ui.editor.graph.Viewport;
import com.mine.geometry_node.client.ui.editor.graph.action.ViewportActionId;
import com.mine.geometry_node.client.ui.editor.graph.action.ViewportActionSink;
import com.mine.geometry_node.client.ui.editor.graph.menu.ViewportMenu;
import com.mine.geometry_node.client.ui.shortcut.UiInputBindings;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.area.AreaLifecycle;
import com.mine.geometry_node.client.ui.workspace.area.AreaTransientUi;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;

/** Reuses all standard graph visuals and edit commands; preview accepts camera movement only. */
final class EntityGraphViewport extends Viewport {
    private boolean editing;
    private final Runnable save;
    private ViewportMenu menu;
    private AreaLifecycle.Subscription menuBinding = AreaLifecycle.Subscription.NONE;
    private float lastX;
    private float lastY;

    EntityGraphViewport(Context context, boolean editing, Runnable save) {
        super(context);
        this.editing = editing;
        this.save = save;
    }

    void setEditing(boolean value) { editing = value; }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (editing) return super.dispatchTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                lastX = event.getX();
                lastY = event.getY();
                requestFocus();
            }
            case MotionEvent.ACTION_MOVE -> {
                getCamera().pan(event.getX() - lastX, event.getY() - lastY);
                lastX = event.getX();
                lastY = event.getY();
            }
        }
        return true;
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (editing) return super.dispatchGenericMotionEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_SCROLL) {
            float scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (scroll != 0) getCamera().zoom(scroll > 0, event.getX(), event.getY());
        }
        return true;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (!editing) return true;
        // Standard Save uses synchronous disk IO; this sidecar routes it to its IO queue.
        KeyBinding binding = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_SAVE));
        if (binding != null && UiInputBindings.matches(binding, event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) save.run();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public ViewportActionSink getActionSink() {
        return new ViewportActionSink() {
            @Override public void performAction(ViewportActionId id,
                    com.mine.geometry_node.client.ui.editor.graph.action.ViewportActionRequest request) {
                if (!editing) return;
                if (id == ViewportActionId.SAVE) save.run();
                else getController().performAction(id, request);
            }

            @Override public String graphTypeId() { return getController().graphTypeId(); }
        };
    }

    @Override public void showMenu(float screenX, float screenY) {
        if (!editing) return;
        closeMenu();
        menu = new ViewportMenu(getContext());
        addView(menu);
        menu.showAt(screenX, screenY, this, getActionSink());
        menuBinding = AreaTransientUi.bind(this, menu, this::closeMenu);
    }

    @Override public void closeMenu() {
        // Viewport construction/lifecycle may invoke this before subclass initialization.
        if (menuBinding != null) menuBinding.close();
        menuBinding = AreaLifecycle.Subscription.NONE;
        if (menu != null) {
            removeView(menu);
            menu = null;
        }
        super.closeMenu();
    }

    @Override public void deactivateLifecycle() {
        closeMenu();
        super.deactivateLifecycle();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (oldWidth == 0 || oldHeight == 0) post(this::fitGraph);
    }

    void fitGraph() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        RectF bounds = new RectF();
        RectF item = new RectF();
        boolean hasBounds = false;
        for (var node : getNodeVisuals().values()) {
            node.getLogicalBounds(item);
            if (!item.isEmpty()) {
                if (hasBounds) bounds.union(item);
                else bounds.set(item);
                hasBounds = true;
            }
        }
        for (var frame : getFrameVisuals().values()) {
            frame.getLogicalBounds(item);
            if (!item.isEmpty()) {
                if (hasBounds) bounds.union(item);
                else bounds.set(item);
                hasBounds = true;
            }
        }
        if (bounds.isEmpty()) return;
        float scale = Math.min(1, Math.min(
                getWidth() / UIUtils.dp2px(bounds.width() + 80),
                getHeight() / UIUtils.dp2px(bounds.height() + 80)));
        getCamera().setScale(scale);
        getCamera().setPosition(getWidth() / 2f - UIUtils.dp2px(bounds.centerX()) * scale,
                getHeight() / 2f - UIUtils.dp2px(bounds.centerY()) * scale);
    }
}
