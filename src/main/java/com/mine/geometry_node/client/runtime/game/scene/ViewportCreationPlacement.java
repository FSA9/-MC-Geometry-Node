package com.mine.geometry_node.client.runtime.game.scene;

import com.mine.geometry_node.client.runtime.game.input.GameViewportInput;
import com.mine.geometry_node.client.runtime.game.render.PresentedViewportFrame;
import com.mine.geometry_node.client.scene.ClientSceneService;
import com.mine.geometry_node.core.engine.system.viewport.entity.ViewportBlockRaycast;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/** Capture displayed-image math on the UI thread; query loaded blocks on the game thread. */
public final class ViewportCreationPlacement {
    private ViewportCreationPlacement() {}

    public record Request(long generation, String dimension, ViewportSceneMath.Ray ray, Vec3 fallback) {
        public boolean current(ClientSceneService.Snapshot scene) {
            return scene.worldGeneration() == generation && scene.dimension().equals(dimension) && scene.canEdit();
        }
    }

    public static Request capture(PresentedViewportFrame frame, double u, double v) {
        if (frame == null || frame.scene() == null) return null;
        if (!Double.isFinite(u) || !Double.isFinite(v) || u < 0 || u > 1 || v < 0 || v > 1) u = v = .5;
        var ray = ViewportSceneMath.ray(frame, u, v);
        if (ray == null) return null;
        var camera = frame.camera();
        var eye = new Vec3(camera.x(), camera.y(), camera.z());
        // Orthographic near planes can lie behind the eye. A miss stays four blocks
        // in front of the displayed camera plane, rather than at a buried orbit pivot.
        double distance = Math.clamp(eye.subtract(ray.origin()).dot(ray.direction()) + 4, 0, ray.length());
        return new Request(frame.scene().worldGeneration(), frame.scene().dimension(), ray,
                ray.origin().add(ray.direction().scale(distance)));
    }

    public static void resolve(Request request, Consumer<Vec3> resolved) {
        if (request == null) return;
        Minecraft.getInstance().execute(() -> {
            var mc = Minecraft.getInstance();
            if (mc.level == null || mc.getConnection() == null || !GameViewportInput.isEditor(mc.screen)
                    || !request.current(ClientSceneService.INSTANCE.snapshot())
                    || !request.dimension().equals(mc.level.dimension().identifier().toString())) return;
            var ray = request.ray();
            var hit = ViewportBlockRaycast.clip(mc.level, ray.origin(), ray.origin().add(ray.direction().scale(ray.length())));
            resolved.accept(position(request, hit));
        });
    }

    public static Vec3 position(Request request, BlockHitResult hit) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return request.fallback();
        var face = hit.getDirection();
        // Keep a surface-mounted entity's feet just outside the hit surface.
        return hit.getLocation().add(face.getStepX() * .01, face.getStepY() * .01, face.getStepZ() * .01);
    }
}
