package com.mine.geometry_node.core.engine.runtime;

import com.mine.geometry_node.GeometryNode;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
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
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((EntityJoinLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                entityJoined(level, event.getEntity());
            }
        });
        bus.addListener((EntityLeaveLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                entityLeft(level, event.getEntity());
            }
        });
        bus.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) levelUnloaded(level);
        });
        bus.addListener((ServerStoppingEvent event) -> shutdown(event.getServer()));
    }

    private static void tickLevel(ServerLevel level) {
        ServerEngineRegistry.INSTANCE.all().stream()
                .sorted(TICK_ORDER)
                .forEach(engine -> engine.tickLevel(level));
    }

    private static void shutdown(MinecraftServer server) {
        for (ServerEngine engine : orderedEngines()) {
            invokeLifecycle(engine, "shutdown", () -> engine.shutdown(server));
        }
    }

    private static void entityJoined(ServerLevel level, Entity entity) {
        for (ServerEngine engine : ServerEngineRegistry.INSTANCE.all()) {
            invokeLifecycle(engine, "entity join", () -> engine.entityJoined(level, entity));
        }
    }

    private static void entityLeft(ServerLevel level, Entity entity) {
        for (ServerEngine engine : ServerEngineRegistry.INSTANCE.all()) {
            invokeLifecycle(engine, "entity leave", () -> engine.entityLeft(level, entity));
        }
    }

    private static void levelUnloaded(ServerLevel level) {
        for (ServerEngine engine : ServerEngineRegistry.INSTANCE.all()) {
            invokeLifecycle(engine, "level unload", () -> engine.levelUnloaded(level));
        }
    }

    private static java.util.List<ServerEngine> orderedEngines() {
        return ServerEngineRegistry.INSTANCE.all().stream().sorted(TICK_ORDER).toList();
    }

    private static void invokeLifecycle(ServerEngine engine, String operation, Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException exception) {
            GeometryNode.LOGGER.error("Server engine {} failed during {}", engine.id(), operation, exception);
        }
    }
}
