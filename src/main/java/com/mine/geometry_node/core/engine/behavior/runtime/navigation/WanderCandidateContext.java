package com.mine.geometry_node.core.engine.behavior.runtime.navigation;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

import java.util.Objects;
import java.util.Random;

/** Inputs available to one cheap Wander candidate proposal round. */
public record WanderCandidateContext(Mob mob, PathNavigation navigation,
                                     int horizontalRange, int verticalRange, Random random) {
    public WanderCandidateContext {
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(navigation, "navigation");
        Objects.requireNonNull(random, "random");
        if (horizontalRange <= 0 || verticalRange < 0) {
            throw new IllegalArgumentException("Invalid Wander candidate range");
        }
    }
}
