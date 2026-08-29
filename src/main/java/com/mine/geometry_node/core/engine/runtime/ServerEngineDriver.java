package com.mine.geometry_node.core.engine.runtime;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Comparator;

/** Drives all registered server engines from one tick and shutdown hook. */
public final class ServerEngineDriver {
    private static final Comparator<ServerEngine> TICK_ORDER = Comparator
            .comparingInt(ServerEngine::tickOrder)
            .thenComparing(ServerEngine::id);
    private static boolean registered;

    private ServerEngineDriver() {
    }

    public static synchronized void init() {
        if (registered) return;
        registered = true;
        TickEvent.SERVER_LEVEL_POST.register(ServerEngineDriver::tickLevel);
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> shutdown(event.getServer()));
    }

    private static void tickLevel(ServerLevel level) {
        ServerEngineRegistry.INSTANCE.all().stream()
                .sorted(TICK_ORDER)
                .forEach(engine -> engine.tickLevel(level));
    }

    private static void shutdown(MinecraftServer server) {
        ServerEngineRegistry.INSTANCE.all().stream()
                .sorted(TICK_ORDER)
                .forEach(engine -> engine.shutdown(server));
    }
}
