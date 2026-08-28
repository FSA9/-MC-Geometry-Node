package com.mine.geometry_node.core.engine.behavior.blackboard;

import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import org.jetbrains.annotations.Nullable;

/** Read-only scoped blackboard capability exposed to behavior data nodes. */
public interface BehaviorBlackboardView {
    @Nullable Object getBlackboard(ScopedStateScope scope, String name);
    boolean hasBlackboard(ScopedStateScope scope, String name);
}
