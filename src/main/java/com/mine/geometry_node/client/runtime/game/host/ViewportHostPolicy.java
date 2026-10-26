package com.mine.geometry_node.client.runtime.game.host;

import net.minecraft.client.gui.screens.Screen;

import java.util.Objects;
import java.util.function.Function;

/** Host identity and composition color supplied by the application, without concrete UI dependencies. */
public final class ViewportHostPolicy {
    /** A managed screen can replace a retained workspace without being a workspace itself.
     * Ordinary native screens leave this false so the editor can resume after they close.
     */
    public record Snapshot(boolean workspace, int background, boolean replacesRetainedWorkspace) {
        public static final Snapshot NONE = new Snapshot(false, 0);
        public Snapshot(boolean workspace, int background) { this(workspace, background, workspace); }
        public boolean opaque() { return workspace && (background >>> 24) == 255; }
    }

    private static volatile Function<Screen, Snapshot> policy = screen -> Snapshot.NONE;

    private ViewportHostPolicy() {}

    public static void install(Function<Screen, Snapshot> policy) {
        ViewportHostPolicy.policy = Objects.requireNonNull(policy);
    }

    public static Snapshot snapshot(Screen screen) {
        return Objects.requireNonNull(policy.apply(screen));
    }
}
