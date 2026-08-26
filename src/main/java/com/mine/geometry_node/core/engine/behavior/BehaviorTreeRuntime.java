package com.mine.geometry_node.core.engine.behavior;

import com.mine.geometry_node.core.engine.behavior.compile.BehaviorTreeCompiler;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompilationService;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntime;

/**
 * Behavior-tree family facade. P2 owns compiler registration here; execution
 * lifecycle and instance services are introduced separately in P3.
 */
public final class BehaviorTreeRuntime implements GraphRuntime {
    public static final BehaviorTreeRuntime INSTANCE = new BehaviorTreeRuntime();

    private BehaviorTreeRuntime() {
    }

    @Override
    public GraphKind kind() {
        return GraphKind.BEHAVIOR_TREE;
    }

    @Override
    public String id() {
        return "geometry_node:behavior_tree";
    }

    @Override
    public void init() {
        GraphCompilationService.INSTANCE.register(BehaviorTreeCompiler.INSTANCE);
    }
}
