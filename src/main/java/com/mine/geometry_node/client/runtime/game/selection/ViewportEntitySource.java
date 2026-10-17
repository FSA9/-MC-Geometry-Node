package com.mine.geometry_node.client.runtime.game.selection;

import com.mine.geometry_node.client.runtime.game.render.PresentedViewportFrame;
import com.mine.geometry_node.client.runtime.game.scene.ViewportSceneMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.*;

/** Captures only entities extracted by the actual viewport renderer, at their interpolated pose. */
public final class ViewportEntitySource implements ViewportSelectionSources.Source {
    public static final String ID = "geometry_node:entity";
    private static final List<ViewportTarget> EXTRACTED = new ArrayList<>();
    static List<ViewportTarget> extractedTargets() { return List.copyOf(EXTRACTED); }
    public static void begin() { EXTRACTED.clear(); }
    public static void extract(Entity entity, EntityRenderState state) {
        if (entity instanceof net.minecraft.world.entity.Interaction || entity.isRemoved() || !entity.isAlive() || state.isInvisible || entity.isSpectator()) return;
        Vec3 offset = new Vec3(state.x - entity.getX(), state.y - entity.getY(), state.z - entity.getZ());
        EXTRACTED.add(new ViewportTarget(new ViewportTarget.Key(ID, entity.getUUID().toString()),
                entity.getBoundingBox().move(offset), ViewportTarget.Decoration.DISC, List.of()));
    }
    @Override public String id() { return ID; }
    @Override public List<ViewportTarget> capture(PresentedViewportFrame frame) {
        var level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        List<ViewportTarget> result = new ArrayList<>();
        for (var target : EXTRACTED) {
            AABB box = target.bounds();
            if (!ViewportSceneMath.intersects(frame, box, 0, 0, 1, 1)) continue;
            List<Vec3> visible = new ArrayList<>();
            Vec3 center = box.getCenter();
            // Center and six inset face centers handle partial terrain occlusion without GPU readback.
            for (int i = 0; i < 7; i++) {
                Vec3 point = switch (i) {
                    case 1 -> new Vec3(box.minX + box.getXsize() * .05, center.y, center.z);
                    case 2 -> new Vec3(box.maxX - box.getXsize() * .05, center.y, center.z);
                    case 3 -> new Vec3(center.x, box.minY + box.getYsize() * .05, center.z);
                    case 4 -> new Vec3(center.x, box.maxY - box.getYsize() * .05, center.z);
                    case 5 -> new Vec3(center.x, center.y, box.minZ + box.getZsize() * .05);
                    case 6 -> new Vec3(center.x, center.y, box.maxZ - box.getZsize() * .05);
                    default -> center;
                };
                var p = ViewportSceneMath.project(frame, point);
                if (!p.visible()) continue;
                var ray = ViewportSceneMath.ray(frame, p.u(), p.v());
                if (ray == null) continue;
                var hit = level.clip(new ClipContext(ray.origin(), point, ClipContext.Block.VISUAL,
                        ClipContext.Fluid.NONE, CollisionContext.empty()));
                if (hit.getType() == HitResult.Type.MISS) { visible.add(point); break; }
            }
            if (!visible.isEmpty()) result.add(new ViewportTarget(target.key(), box, target.decoration(), visible));
        }
        return result;
    }
    @Override public boolean action(Set<String> ids, PresentedViewportFrame frame,
            ViewportSceneMath.Ray ray, ViewportWorldActions feedback) {
        feedback.moveEntities(ids.stream().map(UUID::fromString).toList(), frame, ray);
        return true;
    }
}
