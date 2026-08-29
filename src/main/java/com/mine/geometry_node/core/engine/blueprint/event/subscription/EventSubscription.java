package com.mine.geometry_node.core.engine.blueprint.event.subscription;

import com.mine.geometry_node.core.engine.blueprint.event.precheck.EventPrecheck;
import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public record EventSubscription(String graphId,
                                BlueprintPlan index,
                                int nodeId,
                                String eventType,
                                EventPrecheck precheck) {
    public boolean shouldDispatch(ServerLevel level, @Nullable Entity target, @Nullable Map<String, Object> eventData) {
        return precheck == null || precheck.test(level, target, eventData);
    }
}
