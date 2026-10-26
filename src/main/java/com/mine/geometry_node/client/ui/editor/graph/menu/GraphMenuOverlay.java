package com.mine.geometry_node.client.ui.editor.graph.menu;

import com.mine.geometry_node.client.ui.components.base.overlay.UiInputBarrier;
import com.mine.geometry_node.client.ui.editor.graph.interaction.InteractionContext;
import com.mine.geometry_node.client.ui.workspace.area.AreaLifecycle;
import com.mine.geometry_node.client.ui.workspace.area.AreaTransientUi;
import icyllis.modernui.view.ViewGroup;

import java.util.function.BooleanSupplier;

/** Lifetime shared by the frame, group and port editors added directly to the viewport. */
public final class GraphMenuOverlay extends UiInputBarrier {
    private final InteractionContext owner;
    private final BooleanSupplier targetCurrent;
    private AreaLifecycle.Subscription binding = AreaLifecycle.Subscription.NONE;
    private boolean closed;

    GraphMenuOverlay(InteractionContext owner, BooleanSupplier targetCurrent) {
        super(owner.getUIContext());
        this.owner = owner;
        this.targetCurrent = targetCurrent;
    }

    void bindLifetime() {
        binding = AreaTransientUi.bind((ViewGroup) owner, this, () -> close(false));
    }

    boolean isCurrent() {
        return !closed && getParent() == owner && getVisibility() == VISIBLE
                && owner.isReady() && targetCurrent.getAsBoolean();
    }

    void close(boolean restoreFocus) {
        boolean current = isCurrent();
        closed = true;
        binding.close();
        binding = AreaLifecycle.Subscription.NONE;
        if (getParent() instanceof ViewGroup parent) parent.removeView(this);
        if (restoreFocus && current) owner.requestViewportFocus();
    }

    @Override protected void onDetachedFromWindow() {
        closed = true;
        binding.close();
        binding = AreaLifecycle.Subscription.NONE;
        super.onDetachedFromWindow();
    }

    static boolean containsIdentity(Iterable<?> visuals, Object target) {
        for (Object visual : visuals) if (visual == target) return true;
        return false;
    }

    /** Called by the same cancellation path that clears connection and movement gestures. */
    public static void dismissAll(InteractionContext context) {
        if (!(context instanceof ViewGroup parent)) return;
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            if (parent.getChildAt(i) instanceof GraphMenuOverlay overlay) overlay.close(false);
        }
    }
}
