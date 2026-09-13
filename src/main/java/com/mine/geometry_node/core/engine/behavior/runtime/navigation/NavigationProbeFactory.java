package com.mine.geometry_node.core.engine.behavior.runtime.navigation;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.jetbrains.annotations.Nullable;

/** Explicit opt-in for safely mirroring a custom Navigation implementation. */
public interface NavigationProbeFactory {
    boolean supports(Mob navigationMob, PathNavigation liveNavigation);

    /** Returns an equality-stable snapshot of custom state that affects reachability. */
    @Nullable
    default Object configurationKey(Mob navigationMob, PathNavigation liveNavigation) {
        return null;
    }

    @Nullable
    PathNavigation createProbe(Mob navigationMob, PathNavigation liveNavigation);

    /** Mirrors custom mutable state before every probe request. */
    default void synchronize(Mob navigationMob, PathNavigation liveNavigation,
                             PathNavigation probeNavigation) {
    }

    /** Releases resources owned by a cached custom probe. */
    default void dispose(PathNavigation probeNavigation) {
    }
}
