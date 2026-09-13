package com.mine.geometry_node.core.engine.behavior.runtime.navigation;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Supplies navigation-appropriate destinations without performing a full path search.
 * Implementations must honor the requested ranges and return only finite coordinates.
 */
public interface WanderCandidateProvider {
    boolean supports(Mob mob, PathNavigation navigation);

    @Nullable
    Vec3 findCandidate(WanderCandidateContext context);
}
