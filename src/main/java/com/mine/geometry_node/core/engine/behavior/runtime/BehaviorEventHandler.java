package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.engine.behavior.BehaviorTreeRuntime;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Bridges server lifecycle events into the behavior-tree engine. */
public final class BehaviorEventHandler {
    private static boolean registered;

    private BehaviorEventHandler() {
    }

    public static synchronized void init() {
        if (registered) return;
        registered = true;
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((EntityJoinLevelEvent event) -> {
            if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof net.minecraft.world.entity.Mob mob)) {
                return;
            }
            if (BehaviorTreeRuntime.INSTANCE.boundGraph(mob) == null
                    || BehaviorTreeRuntime.INSTANCE.getForOwner(mob) != null) return;
            try {
                BehaviorTreeRuntime.INSTANCE.startBound(mob);
            } catch (RuntimeException exception) {
                com.mine.geometry_node.GeometryNode.LOGGER.warn(
                        "Unable to restore behavior tree for entity {}", mob.getUUID(), exception);
            }
        });
        bus.addListener((LivingDeathEvent event) -> BehaviorTreeRuntime.INSTANCE.ownerUnavailable(
                event.getEntity(), BehaviorTerminationReason.OWNER_INVALID));
        bus.addListener((EntityTravelToDimensionEvent event) -> BehaviorTreeRuntime.INSTANCE.ownerUnavailable(
                event.getEntity(), BehaviorTerminationReason.DIMENSION_CHANGED));
        bus.addListener((EntityLeaveLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel) {
                BehaviorTreeRuntime.INSTANCE.ownerUnavailable(
                        event.getEntity(), BehaviorTerminationReason.CHUNK_UNLOADED);
            }
        });
    }
}
