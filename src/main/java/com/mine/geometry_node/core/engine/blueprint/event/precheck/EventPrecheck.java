package com.mine.geometry_node.core.engine.blueprint.event.precheck;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** A compiled, immutable precheck that may be shared by every binding of one graph plan. */
@FunctionalInterface
public interface EventPrecheck {
    EventPrecheck ALWAYS = (level, target, eventData) -> true;

    boolean test(ServerLevel level, @Nullable Entity target, @Nullable Map<String, Object> eventData);
}
