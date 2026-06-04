package com.mine.geometry_node.core.engine.dialogue;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Bridges game lifecycle events into DialogueRuntime.
 */
public final class DialogueEventHandler {
    private static boolean registered;

    private DialogueEventHandler() {
    }

    public static void init() {
        if (registered) {
            return;
        }
        registered = true;

        TickEvent.SERVER_LEVEL_POST.register(level -> DialogueRuntime.INSTANCE.onServerLevelTick(level));

        var bus = NeoForge.EVENT_BUS;
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
                DialogueRuntime.INSTANCE.onPlayerLogout(player);
            }
        });
        bus.addListener((LivingDeathEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                DialogueRuntime.INSTANCE.onEntityDeath(event.getEntity());
            }
        });
        bus.addListener((EntityTravelToDimensionEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                DialogueRuntime.INSTANCE.onEntityChangeDimension(event.getEntity());
            }
        });
        bus.addListener((ServerStoppingEvent event) -> DialogueRuntime.INSTANCE.onServerStopping());
    }
}
