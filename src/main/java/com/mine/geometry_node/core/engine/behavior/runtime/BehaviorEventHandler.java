package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.engine.behavior.BehaviorTreeRuntime;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Bridges server lifecycle events into the behavior runtime service. */
public final class BehaviorEventHandler {
    private static boolean registered;

    private BehaviorEventHandler() {
    }

    public static synchronized void init() {
        if (registered) return;
        registered = true;
        TickEvent.SERVER_LEVEL_POST.register(BehaviorTreeRuntime.INSTANCE::tickLevel);
        var bus = NeoForge.EVENT_BUS;
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
        bus.addListener((ServerStoppingEvent event) ->
                BehaviorTreeRuntime.INSTANCE.shutdown(event.getServer()));
    }
}
