package com.mine.geometry_node.client.ui.overlay;

/** Stable handle for one logical overlay instance, including while suspended. */
public interface OverlayHandle extends AutoCloseable {
    /** True until the logical overlay is closed, including while suspended. */
    boolean isOpen();

    /** Retains the instance while releasing its presentation. Unsupported handles return false. */
    default boolean suspend() {
        return false;
    }

    /** Restores the retained instance without another onShown callback. */
    default boolean resume() {
        return false;
    }

    /** Includes suspension while covered by a nested modal. */
    default boolean isSuspended() {
        return false;
    }

    /** Owner/host termination is final cleanup and must bypass dismissal validation. */
    boolean requestClose(OverlayCloseReason reason);

    @Override
    default void close() {
        requestClose(OverlayCloseReason.PROGRAMMATIC);
    }
}
