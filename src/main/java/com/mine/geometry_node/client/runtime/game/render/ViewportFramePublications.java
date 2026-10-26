package com.mine.geometry_node.client.runtime.game.render;

import java.util.Objects;
import java.util.function.Supplier;

/** Application-installed publication factory. Surfaces remain the sole owners of created channels. */
public final class ViewportFramePublications {
    private static volatile Supplier<? extends ViewportFramePublication> factory;

    private ViewportFramePublications() {}

    /** Install before the first capture. Each invocation must create an independent channel.
     * Replacing the factory (for example in tests) never replaces an existing surface's channel.
     */
    public static void installFactory(Supplier<? extends ViewportFramePublication> factory) {
        ViewportFramePublications.factory = Objects.requireNonNull(factory);
    }

    static ViewportFramePublication create() {
        var installed = factory;
        if (installed == null) {
            throw new IllegalStateException("Viewport frame publication factory must be installed before capture");
        }
        return Objects.requireNonNull(installed.get(), "Viewport frame publication factory returned null");
    }
}
