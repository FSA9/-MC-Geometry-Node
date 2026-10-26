package com.mine.geometry_node.client.runtime.model.diagnostic;

/** Runtime diagnostic controls, independent of HUD rendering and theme classes. */
public final class ModelHudState {
    private static volatile boolean enabled;
    private static volatile long revision;

    private ModelHudState() {}

    public static boolean enabled() { return enabled; }
    public static long revision() { return revision; }

    public static void setEnabled(boolean value) {
        enabled = value;
        invalidate();
    }

    public static synchronized void invalidate() { revision++; }
}
