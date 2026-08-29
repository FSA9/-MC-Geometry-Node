package com.mine.geometry_node.core.engine.graph.runtime;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Comparator;

/** Drives graph-family runtime ticks and server shutdown from one shared hook. */
public final class GraphRuntimeDriver {
    private static final Comparator<GraphRuntime> TICK_ORDER = Comparator
            .comparingInt(GraphRuntime::tickOrder)
            .thenComparing(GraphRuntime::id);

    private static boolean registered;

    private GraphRuntimeDriver() {
    }

    public static synchronized void init() {
        if (registered) return;
        registered = true;
        TickEvent.SERVER_LEVEL_POST.register(GraphRuntimeDriver::tickLevel);
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> shutdown(event.getServer()));
    }

    private static void tickLevel(ServerLevel level) {
        GraphRuntimeRegistry.INSTANCE.all().stream()
                .sorted(TICK_ORDER)
                .forEach(runtime -> runtime.tickLevel(level));
    }

    private static void shutdown(MinecraftServer server) {
        GraphRuntimeRegistry.INSTANCE.all().stream()
                .sorted(TICK_ORDER)
                .forEach(runtime -> runtime.shutdown(server));
    }
}
