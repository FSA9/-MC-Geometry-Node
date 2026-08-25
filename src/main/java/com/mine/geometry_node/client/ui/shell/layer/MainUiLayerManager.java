package com.mine.geometry_node.client.ui.shell.layer;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.ui.shell.layer.modal.MainUiModal;
import com.mine.geometry_node.client.ui.shell.layer.modal.ModalOptions;
import com.mine.geometry_node.client.ui.shell.layer.ephemeral.TransientOptions;
import com.mine.geometry_node.client.ui.shell.layer.ephemeral.TransientOverlay;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** Owns every global transient and modal overlay mounted in one MainUI. */
public final class MainUiLayerManager implements AutoCloseable {
    private static final float MODAL_LAYER_Z = 100.0f;
    private static final float EPHEMERAL_LAYER_Z = 200.0f;

    private final Context context;
    private final MainUiLayerHost transientHost;
    private final MainUiLayerHost modalHost;
    private final Map<MainUiOverlay, Entry> mounted = new IdentityHashMap<>();
    private final Deque<Entry> modalStack = new ArrayDeque<>();

    private Entry transientEntry;
    private boolean closed;

    public MainUiLayerManager(Context context, MainUiLayerHost transientHost, MainUiLayerHost modalHost) {
        this.context = Objects.requireNonNull(context, "context");
        this.transientHost = Objects.requireNonNull(transientHost, "transientHost");
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        if (transientHost == modalHost) {
            throw new IllegalArgumentException("Transient and modal overlays require different hosts");
        }
        modalHost.setZ(MODAL_LAYER_Z);
        transientHost.setZ(EPHEMERAL_LAYER_Z);
    }

    public OverlayHandle showTransient(TransientOverlay overlay) {
        return showTransient(overlay, TransientOptions.defaults());
    }

    public OverlayHandle showTransient(TransientOverlay overlay, TransientOptions options) {
        Core.checkUiThread();
        ensureOpen();
        Objects.requireNonNull(overlay, "overlay");
        Objects.requireNonNull(options, "options");
        ensureNotMounted(overlay);

        if (transientEntry != null
                && !closeEntry(transientEntry, OverlayCloseReason.REPLACED, false)) {
            throw new IllegalStateException("The active transient overlay refused replacement");
        }

        Entry entry = mount(overlay, transientHost, false,
                options.closeOnOutsideClick(), options.closeOnEscape(), options.returnFocusTarget(), 0);
        transientEntry = entry;
        transientHost.setVisibility(View.VISIBLE);
        notifyShown(entry);
        return entry.handle;
    }

    public OverlayHandle showModal(MainUiModal modal) {
        return showModal(modal, ModalOptions.defaults());
    }

    public OverlayHandle showModal(MainUiModal modal, ModalOptions options) {
        Core.checkUiThread();
        ensureOpen();
        Objects.requireNonNull(modal, "modal");
        Objects.requireNonNull(options, "options");
        ensureNotMounted(modal);

        if (transientEntry != null
                && !closeEntry(transientEntry, OverlayCloseReason.REPLACED, false)) {
            throw new IllegalStateException("The active transient overlay refused modal presentation");
        }

        Entry previousTop = modalStack.peekLast();
        Entry entry = mount(modal, modalHost, true,
                options.closeOnOutsideClick(), options.closeOnEscape(), options.returnFocusTarget(),
                options.scrimColor());
        if (previousTop != null) {
            previousTop.container.setEnabled(false);
            previousTop.container.setFocusable(false);
        }
        modalStack.addLast(entry);
        modalHost.setVisibility(View.VISIBLE);
        notifyShown(entry);
        focusTopModal();
        return entry.handle;
    }

    public boolean closeTransient(OverlayCloseReason reason) {
        Objects.requireNonNull(reason, "reason");
        return transientEntry == null || closeEntry(transientEntry, reason, false);
    }

    public boolean closeTopModal(OverlayCloseReason reason) {
        Objects.requireNonNull(reason, "reason");
        Entry top = modalStack.peekLast();
        return top == null || closeEntry(top, reason, false);
    }

    public boolean hasTransientOverlay() {
        return transientEntry != null;
    }

    public boolean hasModalOverlay() {
        return !modalStack.isEmpty();
    }

    public int modalCount() {
        return modalStack.size();
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        if (transientEntry != null) {
            closeEntry(transientEntry, OverlayCloseReason.HOST_DESTROYED, true);
        }
        while (!modalStack.isEmpty()) {
            closeEntry(modalStack.peekLast(), OverlayCloseReason.HOST_DESTROYED, true);
        }
        transientHost.removeAllViews();
        transientHost.setVisibility(View.GONE);
        modalHost.removeAllViews();
        modalHost.setVisibility(View.GONE);
        mounted.clear();
    }

    private Entry mount(MainUiOverlay overlay, MainUiLayerHost host, boolean modal,
                        boolean closeOnOutsideClick, boolean closeOnEscape, View returnFocusTarget,
                        int scrimColor) {
        View content = Objects.requireNonNull(overlay.createView(context), "overlay view");
        if (content.getParent() != null) {
            throw new IllegalStateException("Overlay view is already attached to a parent");
        }

        Entry entry = new Entry(overlay, host, modal, closeOnOutsideClick, closeOnEscape, returnFocusTarget);
        OverlayContainer container = new OverlayContainer(context, entry);
        if (scrimColor != 0) {
            ShapeDrawable scrim = new ShapeDrawable();
            scrim.setShape(ShapeDrawable.RECTANGLE);
            scrim.setColor(scrimColor);
            container.setBackground(scrim);
        }
        entry.container = container;
        entry.content = content;
        try {
            container.addView(content, Objects.requireNonNull(
                    overlay.createLayoutParams(host), "overlay layout params"));
            host.addView(container, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            mounted.put(overlay, entry);
            return entry;
        } catch (RuntimeException failure) {
            entry.open = false;
            mounted.remove(overlay);
            try {
                if (container.getParent() == host) host.removeView(container);
                if (content.getParent() == container) container.removeView(content);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private void notifyShown(Entry entry) {
        try {
            entry.overlay.onShown(entry.handle);
        } catch (RuntimeException exception) {
            GeometryNode.LOGGER.error("Failed to show a MainUI overlay", exception);
            closeEntry(entry, OverlayCloseReason.PROGRAMMATIC, true);
            throw exception;
        }
        if (entry.open && entry.container.findFocus() == null) {
            entry.container.setFocusable(true);
            entry.container.setFocusableInTouchMode(true);
            entry.container.requestFocus();
        }
    }

    private boolean closeEntry(Entry entry, OverlayCloseReason reason, boolean force) {
        if (!entry.open) {
            return true;
        }
        if (!force && !canClose(entry, reason)) {
            return false;
        }

        entry.open = false;
        mounted.remove(entry.overlay);
        if (entry == transientEntry) {
            transientEntry = null;
        }
        if (entry.modal) {
            modalStack.remove(entry);
        }
        if (entry.container.getParent() == entry.host) {
            entry.host.removeView(entry.container);
        }
        repairEmptyModalHost();
        updateHostVisibility(entry.host);
        notifyClosed(entry, reason);
        restoreFocusAfterClose(entry);
        return true;
    }

    private boolean canClose(Entry entry, OverlayCloseReason reason) {
        try {
            return entry.overlay.canClose(reason);
        } catch (RuntimeException exception) {
            GeometryNode.LOGGER.error("MainUI overlay close check failed", exception);
            return false;
        }
    }

    private void notifyClosed(Entry entry, OverlayCloseReason reason) {
        try {
            entry.overlay.onClosed(reason);
        } catch (RuntimeException exception) {
            GeometryNode.LOGGER.error("MainUI overlay close callback failed", exception);
        }
        try {
            entry.overlay.onDestroyed();
        } catch (RuntimeException exception) {
            GeometryNode.LOGGER.error("MainUI overlay destroy callback failed", exception);
        }
    }

    private void restoreFocusAfterClose(Entry entry) {
        if (entry.modal && !modalStack.isEmpty()) {
            focusTopModal();
            Entry top = modalStack.peekLast();
            if (top != null && entry.returnFocusTarget != null
                    && isDescendantOf(entry.returnFocusTarget, top.container)) {
                entry.returnFocusTarget.requestFocus();
            }
            return;
        }
        if (entry.returnFocusTarget != null && entry.returnFocusTarget.isAttachedToWindow()) {
            entry.returnFocusTarget.requestFocus();
        }
    }

    private static boolean isDescendantOf(View view, ViewGroup ancestor) {
        View current = view;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent() instanceof View parent ? parent : null;
        }
        return false;
    }

    private void focusTopModal() {
        Entry top = modalStack.peekLast();
        if (top != null && top.open) {
            top.container.setEnabled(true);
            top.container.setFocusable(true);
            top.container.setFocusableInTouchMode(true);
            if (top.container.findFocus() == null) {
                top.container.requestFocus();
            }
        }
    }

    private void updateHostVisibility(MainUiLayerHost host) {
        host.setVisibility(host.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private void repairEmptyModalHost() {
        if (!modalStack.isEmpty() || modalHost.getChildCount() == 0) return;
        GeometryNode.LOGGER.error("Modal stack is empty but the modal host still has {} child views; clearing stale input layer",
                modalHost.getChildCount());
        modalHost.removeAllViews();
    }

    private void ensureNotMounted(MainUiOverlay overlay) {
        if (mounted.containsKey(overlay)) {
            throw new IllegalStateException("The same overlay instance is already mounted");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("MainUiLayerManager is closed");
        }
    }

    private final class OverlayContainer extends FrameLayout {
        private final Entry entry;
        private boolean outsideGesture;

        private OverlayContainer(Context context, Entry entry) {
            super(context);
            this.entry = entry;
            setClickable(true);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (entry.open && entry.closeOnEscape
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEY_ESCAPE) {
                if (!consumeOverlayEscape(entry)) {
                    closeEntry(entry, OverlayCloseReason.ESCAPE, false);
                }
                return true;
            }
            if (super.dispatchKeyEvent(event)) {
                return true;
            }
            return true;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                outsideGesture = !isInsideContent(event.getX(), event.getY());
                if (outsideGesture && entry.closeOnOutsideClick) {
                    closeEntry(entry, OverlayCloseReason.OUTSIDE_CLICK, false);
                    return true;
                }
            }
            if (outsideGesture) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    outsideGesture = false;
                }
                return true;
            }
            super.dispatchTouchEvent(event);
            return true;
        }

        @Override
        public boolean dispatchGenericMotionEvent(MotionEvent event) {
            if (consumeOverlayHover(entry, event.getRawX(), event.getRawY())) {
                return true;
            }
            if (isInsideContent(event.getX(), event.getY())) {
                super.dispatchGenericMotionEvent(event);
            }
            return true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return true;
        }

        @Override
        public boolean onGenericMotionEvent(MotionEvent event) {
            return true;
        }

        private boolean isInsideContent(float x, float y) {
            View content = entry.content;
            return content != null
                    && content.getVisibility() == View.VISIBLE
                    && x >= content.getLeft()
                    && x < content.getRight()
                    && y >= content.getTop()
                    && y < content.getBottom();
        }
    }

    private boolean consumeOverlayEscape(Entry entry) {
        try {
            return entry.overlay.onEscapePressed();
        } catch (RuntimeException exception) {
            GeometryNode.LOGGER.error("MainUI overlay Escape handler failed", exception);
            return false;
        }
    }

    private boolean consumeOverlayHover(Entry entry, float screenX, float screenY) {
        try {
            return entry.overlay.onPointerHover(screenX, screenY);
        } catch (RuntimeException exception) {
            GeometryNode.LOGGER.error("MainUI overlay hover handler failed", exception);
            return false;
        }
    }

    private final class Entry {
        private final MainUiOverlay overlay;
        private final MainUiLayerHost host;
        private final boolean modal;
        private final boolean closeOnOutsideClick;
        private final boolean closeOnEscape;
        private final View returnFocusTarget;
        private final OverlayHandle handle = new EntryHandle(this);

        private OverlayContainer container;
        private View content;
        private boolean open = true;

        private Entry(MainUiOverlay overlay, MainUiLayerHost host, boolean modal,
                      boolean closeOnOutsideClick, boolean closeOnEscape, View returnFocusTarget) {
            this.overlay = overlay;
            this.host = host;
            this.modal = modal;
            this.closeOnOutsideClick = closeOnOutsideClick;
            this.closeOnEscape = closeOnEscape;
            this.returnFocusTarget = returnFocusTarget;
        }
    }

    private final class EntryHandle implements OverlayHandle {
        private final Entry entry;

        private EntryHandle(Entry entry) {
            this.entry = entry;
        }

        @Override
        public boolean isOpen() {
            return entry.open;
        }

        @Override
        public boolean requestClose(OverlayCloseReason reason) {
            return closeEntry(entry, Objects.requireNonNull(reason, "reason"), false);
        }
    }
}
