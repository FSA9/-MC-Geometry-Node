package com.mine.geometry_node.core.node.nodes.quest;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import net.minecraft.world.entity.Entity;

final class QuestNodeContext {
    private QuestNodeContext() {
    }

    static String resolveTaskKey(ExecutionContext context, String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            return configuredPath;
        }
        return isQuestGraph(context) ? context.getGraphId() : "";
    }

    static Entity resolveOwner(ExecutionContext context, Entity configuredOwner) {
        if (configuredOwner != null) {
            return configuredOwner;
        }
        return isQuestGraph(context) ? context.getGraphOwnerEntity() : null;
    }

    private static boolean isQuestGraph(ExecutionContext context) {
        if (context == null || context.getGraphId() == null || context.getGraphId().isBlank()) {
            return false;
        }
        BlueprintPlan index = BlueprintRuntime.INSTANCE.getGraphIndex(context.getGraphId());
        return index != null && GraphTypeRegistry.QUEST.id().equals(index.getGraphTypeId());
    }
}
