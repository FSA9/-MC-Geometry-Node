package com.mine.geometry_node.client.runtime.game.interop;

/** Render-thread handoff: Arc3D changes GL state outside Blaze3D's command encoder. */
public final class UiRenderInterop {
    private static long revision;

    private UiRenderInterop() {}

    public static long revision() { return revision; }

    public static void afterUiRendering() { revision++; }
}
