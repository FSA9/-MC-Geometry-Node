package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.node.NodeCapabilities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** Runtime host adapter. Entity control integration can arbitrate declared resources later. */
public interface BehaviorRuntimeHost {
    String identity();

    boolean isValid();

    long gameTick();

    long nanoTime();

    @Nullable ServerLevel level();

    @Nullable Entity owner();

    default boolean acquireResources(int nodeIndex, Set<NodeCapabilities.ResourceUse> resources) {
        return true;
    }

    default void releaseResources(int nodeIndex, Set<NodeCapabilities.ResourceUse> resources) {
    }

    /** Assigns a target whose selection remains owned by this behavior instance until cleared. */
    @Nullable
    default LivingEntity setAttackTarget(@Nullable LivingEntity target) {
        return null;
    }

    default void releasePersistentControls() {
    }
}
