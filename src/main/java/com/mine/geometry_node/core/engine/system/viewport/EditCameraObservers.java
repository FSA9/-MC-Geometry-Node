package com.mine.geometry_node.core.engine.system.viewport;

import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Shared bounds for renderer admission and the server's additional observers. */
public final class EditCameraObservers {
    public static final int MAX_VIEWS = 4;
    private EditCameraObservers() {}

    public static void checkCount(int count) {
        if (count < 0 || count > MAX_VIEWS) throw new IllegalArgumentException("Expected 0.." + MAX_VIEWS + " editor observers");
    }
    public static boolean validPosition(Vec3 position) {
        return position != null && Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z)
                && Math.abs(position.x) < 30_000_000 && Math.abs(position.y) < 30_000_000 && Math.abs(position.z) < 30_000_000;
    }
    public static List<Vec3> copyPositions(List<Vec3> positions) {
        checkCount(positions.size());
        return List.copyOf(positions);
    }
    /** Vanilla entity tracking uses horizontal distance; always retain the real player. */
    public static Vec3 nearest(Vec3 player, List<Vec3> cameras, Vec3 target) {
        Vec3 nearest = player;
        double distance = distanceSquared(player, target);
        for (Vec3 camera : cameras) {
            double candidate = distanceSquared(camera, target);
            if (candidate < distance) { distance = candidate; nearest = camera; }
        }
        return nearest;
    }
    private static double distanceSquared(Vec3 from, Vec3 to) {
        double dx = from.x - to.x, dz = from.z - to.z;
        return dx * dx + dz * dz;
    }
}
