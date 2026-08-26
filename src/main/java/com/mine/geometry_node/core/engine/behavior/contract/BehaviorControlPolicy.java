package com.mine.geometry_node.core.engine.behavior.contract;

import java.util.Objects;

/** Frozen ownership and cross-runtime rules for the first complete runtime. */
public record BehaviorControlPolicy(int activeTreesPerOwner,
                                    NativeAiMode nativeAiMode,
                                    ReloadMode reloadMode,
                                    UnloadMode unloadMode,
                                    DimensionChangeMode dimensionChangeMode,
                                    RestartMode restartMode,
                                    BlueprintInterop blueprintInterop) {
    public static final BehaviorControlPolicy DEFAULT = new BehaviorControlPolicy(
            1, NativeAiMode.RESOURCE_LEASES, ReloadMode.ABORT_AND_RESTART,
            UnloadMode.DISCARD_RUNNING_STATE, DimensionChangeMode.ABORT_AND_RESTART,
            RestartMode.RESTART_FROM_ROOT, BlueprintInterop.EXPLICIT_ASYNC_REQUEST_ONLY);

    public BehaviorControlPolicy {
        if (activeTreesPerOwner != 1) {
            throw new IllegalArgumentException("P0 permits exactly one active behavior tree per owner");
        }
        nativeAiMode = Objects.requireNonNull(nativeAiMode, "nativeAiMode");
        reloadMode = Objects.requireNonNull(reloadMode, "reloadMode");
        unloadMode = Objects.requireNonNull(unloadMode, "unloadMode");
        dimensionChangeMode = Objects.requireNonNull(dimensionChangeMode, "dimensionChangeMode");
        restartMode = Objects.requireNonNull(restartMode, "restartMode");
        blueprintInterop = Objects.requireNonNull(blueprintInterop, "blueprintInterop");
    }

    public enum NativeAiMode { RESOURCE_LEASES }
    public enum ReloadMode { ABORT_AND_RESTART }
    public enum UnloadMode { DISCARD_RUNNING_STATE }
    public enum DimensionChangeMode { ABORT_AND_RESTART }
    public enum RestartMode { RESTART_FROM_ROOT }
    public enum BlueprintInterop { EXPLICIT_ASYNC_REQUEST_ONLY }
}
