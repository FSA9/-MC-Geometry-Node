package com.mine.geometry_node.client.ui.workspace.area;

import icyllis.modernui.core.Handler;
import icyllis.modernui.core.Looper;
import icyllis.modernui.view.View;

import java.util.Objects;

/** Binds local popup lifetime to its originating area, not to keyboard focus inside the popup. */
public final class AreaTransientUi {
    public static AreaLifecycle.Subscription bind(View anchor, View popup, Runnable dismiss) {
        return new Binding(Objects.requireNonNull(anchor), Objects.requireNonNull(popup),
                Objects.requireNonNull(dismiss));
    }

    private static final class Binding implements AreaLifecycle.Subscription, View.OnAttachStateChangeListener {
        private final View anchor;
        private final View popup;
        private final Runnable dismiss;
        private final AreaLifecycle.Subscription subscription;
        private boolean invalidated;
        private boolean closed;

        Binding(View anchor, View popup, Runnable dismiss) {
            this.anchor = anchor;
            this.popup = popup;
            this.dismiss = dismiss;
            AreaLifecycle area = AreaLifecycle.find(anchor);
            subscription = area == null ? AreaLifecycle.Subscription.NONE : area.subscribe(event -> {
                if (event != AreaLifecycle.Event.ACTIVATED) invalidate(
                        event == AreaLifecycle.Event.HIDDEN || event == AreaLifecycle.Event.DETACHED
                                || event == AreaLifecycle.Event.DISPOSED);
            });
            anchor.addOnAttachStateChangeListener(this);
            popup.addOnAttachStateChangeListener(this);
        }

        @Override public void onViewAttachedToWindow(View view) {}
        @Override public void onViewDetachedFromWindow(View view) { invalidate(true); }

        private void invalidate(boolean defer) {
            if (closed || invalidated) return;
            invalidated = true;
            unsubscribe();
            popup.setVisibility(View.GONE);
            // Detachment/visibility dispatch walks ancestor child arrays. Hide now,
            // remove after traversal; never mutate a sibling array in that callback.
            if (defer) new Handler(Looper.myLooper()).post(() -> { if (!closed) dismiss.run(); });
            else dismiss.run();
        }

        private void unsubscribe() {
            subscription.close();
            anchor.removeOnAttachStateChangeListener(this);
            popup.removeOnAttachStateChangeListener(this);
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            unsubscribe();
        }
    }

    private AreaTransientUi() {}
}
