package com.mine.geometry_node.client.ui.shell.layer;

import com.mine.geometry_node.client.ui.shell.MainUiServices;
import com.mine.geometry_node.client.ui.shell.MainUiShell;
import com.mine.geometry_node.client.ui.shell.layer.modal.MainUiModal;
import com.mine.geometry_node.client.ui.shell.layer.modal.ModalOptions;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Adapts existing full-screen editors without changing their save/cancel policies. */
public final class OverlayMount {
    private OverlayMount() {}

    public static ViewGroup host(View anchor) {
        for (View current = anchor; current != null;
             current = current.getParent() instanceof View parent ? parent : null) {
            if (current instanceof MainUiShell shell) return shell;
        }
        return null;
    }

    /** Null means the caller is outside MainUI and must use its existing standalone host. */
    public static OverlayHandle modal(View anchor, View content, BooleanSupplier onEscape,
                                      Consumer<OverlayCloseReason> onClosed) {
        MainUiServices services = MainUiServices.find(anchor);
        if (services == null) return null;
        Mount overlay = new Mount(anchor, content,
                new FrameLayout.LayoutParams(-1, -1), onEscape, onClosed);
        return services.layerManager().showModal(overlay, new ModalOptions(false, true, anchor, 0));
    }

    public static OverlayHandle tooltip(View anchor, View content, FrameLayout.LayoutParams params,
                                        Consumer<OverlayCloseReason> onClosed) {
        MainUiServices services = MainUiServices.find(anchor);
        if (services == null) return null;
        return services.layerManager().showTooltip(new Mount(anchor, content, params, () -> false, onClosed), anchor);
    }

    private static final class Mount implements MainUiModal, View.OnAttachStateChangeListener {
        private final View anchor;
        private final View content;
        private final FrameLayout.LayoutParams params;
        private final BooleanSupplier onEscape;
        private final Consumer<OverlayCloseReason> onClosed;
        private OverlayHandle handle;

        private Mount(View anchor, View content, FrameLayout.LayoutParams params,
                      BooleanSupplier onEscape, Consumer<OverlayCloseReason> onClosed) {
            this.anchor = anchor;
            this.content = content;
            this.params = params;
            this.onEscape = onEscape;
            this.onClosed = onClosed;
        }

        @Override public View createView(Context context) { return content; }
        @Override public FrameLayout.LayoutParams createLayoutParams(ViewGroup host) { return params; }
        @Override public boolean onEscapePressed() { return onEscape != null && onEscape.getAsBoolean(); }
        @Override public void onShown(OverlayHandle handle) {
            this.handle = handle;
            anchor.addOnAttachStateChangeListener(this);
            content.addOnAttachStateChangeListener(this);
        }
        @Override public void onClosed(OverlayCloseReason reason) {
            anchor.removeOnAttachStateChangeListener(this);
            content.removeOnAttachStateChangeListener(this);
            handle = null;
            if (onClosed != null) onClosed.accept(reason);
        }
        @Override public void onViewAttachedToWindow(View view) {}
        @Override public void onViewDetachedFromWindow(View view) {
            if (handle != null) handle.requestClose(OverlayCloseReason.OWNER_CLOSED);
        }
    }
}
