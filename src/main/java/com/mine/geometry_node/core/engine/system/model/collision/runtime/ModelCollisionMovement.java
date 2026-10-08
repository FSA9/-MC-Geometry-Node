package com.mine.geometry_node.core.engine.system.model.collision.runtime;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Continuous collision detection, with walkable-plane lift and wall sliding. */
public final class ModelCollisionMovement {
    private static final double SKIN = 1.0e-7;
    private static final double WALKABLE_Y = 0.5;
    private static final int MAX_CONTACTS = 12;

    private ModelCollisionMovement() {}

    @FunctionalInterface
    public interface VanillaClip {
        Vec3 clip(AABB box, Vec3 movement);
    }

    public static Vec3 resolve(AABB initial, Vec3 requested, boolean grounded, double stepHeight,
                               List<ModelCollisionScene.Candidate> candidates, VanillaClip vanilla) {
        Vec3 ordinary = resolve(initial, requested, grounded, candidates, vanilla);
        boolean blocked = Math.abs(ordinary.x - requested.x) > 1.0e-6 || Math.abs(ordinary.z - requested.z) > 1.0e-6;
        if (stepHeight <= 0 || !blocked || requested.y > 0
                || !(grounded || ordinary.y > requested.y + 1.0e-6)) return ordinary;
        var sample = ModelCollisionMetrics.currentMovement();
        if (sample != null) sample.stepAttempts++;
        Vec3 up = resolve(initial, new Vec3(0, stepHeight, 0), false, candidates, vanilla);
        if (up.y <= SKIN) return ordinary;
        Vec3 across = resolve(initial.move(up), new Vec3(requested.x, 0, requested.z), false, candidates, vanilla);
        AABB raised = initial.move(up).move(across);
        Vec3 downRequest = new Vec3(0, -up.y - Math.max(0.02, -requested.y), 0);
        Vec3 down = resolve(raised, downRequest, false, candidates, vanilla);
        Vec3 stepped = up.add(across).add(down);
        if (down.y <= downRequest.y + SKIN || stepped.y > stepHeight + SKIN) return ordinary;
        return stepped.horizontalDistanceSqr() > ordinary.horizontalDistanceSqr() + 1.0e-10 ? stepped : ordinary;
    }

    public static Vec3 resolve(AABB initial, Vec3 requested, boolean grounded,
                               List<ModelCollisionScene.Candidate> candidates, VanillaClip vanilla) {
        if (candidates.isEmpty() || requested.lengthSqr() == 0) return requested;
        Vec3 moved = Vec3.ZERO;
        Vec3 remaining = requested;
        var sample = ModelCollisionMetrics.currentMovement();
        for (int iteration = 0; iteration < MAX_CONTACTS && remaining.lengthSqr() > 1.0e-16; iteration++) {
            AABB box = initial.move(moved);
            if (sample != null) sample.vanillaClips++;
            Vec3 clipped = vanilla.clip(box, remaining);
            if (remaining.y > SKIN && clipped.y < remaining.y - SKIN) {
                // A ceiling clipping only Y would tilt a ramp-following segment into the ramp.
                double fraction = Math.max(0, clipped.y / remaining.y);
                if (sample != null) sample.vanillaClips++;
                clipped = vanilla.clip(box, remaining.scale(fraction));
            }
            remaining = clipped;
            ModelConvexCollider.Hit hit = firstHit(box, remaining, candidates);
            if (hit == null) { moved = moved.add(remaining); remaining = Vec3.ZERO; break; }
            if (sample != null) sample.contacts++;
            double time = Math.max(0, hit.time() - SKIN / Math.max(SKIN, remaining.length()));
            moved = moved.add(remaining.scale(time));
            remaining = remaining.scale(1 - time);
            Vec3 n = hit.normal();
            double into = remaining.dot(n);
            if (into >= 0) break;
            if (n.y >= WALKABLE_Y) {
                // Keep input horizontal speed on walkable ramps; gravity alone must not cause lateral sliding.
                remaining = new Vec3(remaining.x, -(remaining.x * n.x + remaining.z * n.z) / n.y, remaining.z);
            } else {
                remaining = remaining.subtract(n.scale(into));
            }
        }
        if (grounded && requested.y <= 0 && requested.horizontalDistanceSqr() > 1.0e-12) {
            AABB box = initial.move(moved);
            if (sample != null) sample.vanillaClips++;
            Vec3 down = vanilla.clip(box, new Vec3(0, -Math.min(0.6, requested.horizontalDistance() * 1.8 + 0.02), 0));
            ModelConvexCollider.Hit support = firstHit(box, down, candidates);
            if (support != null && support.normal().y >= WALKABLE_Y) {
                moved = moved.add(down.scale(Math.max(0, support.time() - SKIN / Math.max(SKIN, down.length()))));
            }
        }
        return moved;
    }

    private static ModelConvexCollider.Hit firstHit(AABB box, Vec3 movement,
                                                    List<ModelCollisionScene.Candidate> candidates) {
        var sample = ModelCollisionMetrics.currentMovement();
        long started = sample == null ? 0 : System.nanoTime();
        try {
        ModelConvexCollider.Hit closest = null;
        for (ModelCollisionScene.Candidate candidate : candidates) {
            ModelConvexCollider.Hit hit = candidate.sweep(box, movement);
            if (hit != null && (closest == null || hit.time() < closest.time())) closest = hit;
        }
        return closest;
        } finally {
            if (sample != null) sample.satNanos += System.nanoTime() - started;
        }
    }
}
