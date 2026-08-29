package com.mine.geometry_node.core.engine.behavior.blackboard;

import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateProviderResolver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

/** Resolves declared scope references to stable server-side provider identities. */
public final class BehaviorScopedStateProviders {
    private BehaviorScopedStateProviders() {
    }

    public static void install(BehaviorBlackboard blackboard,
                               ServerLevel level, Entity owner) {
        Objects.requireNonNull(blackboard, "blackboard");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(owner, "owner");
        ScopedStateNamespace namespace = ScopedStateNamespace.PUBLIC;
        blackboard.installProvider(ScopedStateProviderResolver.owner(owner, namespace));
        blackboard.installProvider(ScopedStateProviderResolver.shared(level, namespace));
        blackboard.installProvider(ScopedStateProviderResolver.currentGroup(owner, namespace));
        blackboard.installProvider(ScopedStateProviderResolver.world(level, namespace));
    }
}
