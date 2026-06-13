package com.mine.geometry_node.core.engine.blueprint.spatial;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

public final class RotatedBoxEntityQuery {
    private RotatedBoxEntityQuery() {
    }

    public static List<Entity> find(ServerLevel level,
                                    Vec3 center,
                                    Vec3 size,
                                    Vec3 rotation,
                                    Predicate<Entity> predicate) {
        return AreaEntityQuery.find(level, AreaShape.BOX, center, size, rotation, predicate);
    }

    public static Vec3 sanitizeSize(Vec3 size) {
        return AreaEntityQuery.sanitizeSize(size);
    }
}
