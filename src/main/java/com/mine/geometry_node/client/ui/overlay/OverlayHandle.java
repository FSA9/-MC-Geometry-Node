package com.mine.geometry_node.client.ui.overlay;

/** Stable handle for one mounted overlay instance. */
public interface OverlayHandle extends AutoCloseable {
    boolean isOpen();

    boolean requestClose(OverlayCloseReason reason);

    @Override
    default void close() {
        requestClose(OverlayCloseReason.PROGRAMMATIC);
    }
}
