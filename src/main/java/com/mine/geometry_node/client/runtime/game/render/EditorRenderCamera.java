package com.mine.geometry_node.client.runtime.game.render;

import com.mine.geometry_node.client.runtime.game.camera.EditViewportCamera;
import com.mine.geometry_node.client.runtime.game.camera.EditViewportProjection;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;

/** A detached render camera, never an entity and never installed as Minecraft's camera entity. */
final class EditorRenderCamera extends Camera {
    private EditViewportCamera.Snapshot snapshot;
    private ClientLevel cameraLevel;
    private final Matrix4f projection = new Matrix4f();
    private float projectionFar;
    private float projectionAspect, projectionDistance;
    private boolean projectionZeroToOne;
    private Frustum frustum = new Frustum(new Matrix4f(), new Matrix4f());

    void configure(ClientLevel level, LocalPlayer player, EditViewportCamera.Snapshot snapshot) {
        if (cameraLevel != level) {
            super.reset();
            cameraLevel = level;
        }
        setLevel(level);
        setEntity(player);
        this.snapshot = snapshot;
    }

    @Override public void reset() {
        super.reset();
        cameraLevel = null;
        snapshot = null;
    }

    boolean isOrthographic() { return snapshot != null && snapshot.orthographic(); }

    @Override public void update(DeltaTracker delta) {
        // Keep vanilla fluid/environment initialization, but replace the pose and
        // BOTH projection consumers before any terrain/entity visibility update.
        super.update(delta);
        var pose = snapshot;
        if (pose == null) return;
        setPosition(pose.x(), pose.y(), pose.z());
        setRotation(pose.yaw(), pose.pitch(), 0);
        Minecraft mc = Minecraft.getInstance();
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        float far = Math.max(mc.options.getEffectiveRenderDistance() * 64f, mc.options.cloudRange().get() * 16f);
        boolean zeroToOne = RenderSystem.getDevice().isZZeroToOne();
        EditViewportProjection.set(projection, pose, aspect, PROJECTION_Z_NEAR, far, zeroToOne);
        projectionFar = pose.orthographic() ? (float) pose.distance() + far : far;
        projectionAspect = aspect;
        projectionDistance = far;
        projectionZeroToOne = zeroToOne;
        frustum = new Frustum(getViewRotationMatrix(new Matrix4f()), projection);
        frustum.prepare(pose.x(), pose.y(), pose.z());
    }

    PresentedViewportFrame presented(long id, com.mine.geometry_node.client.scene.ClientSceneService.Snapshot scene) {
        return snapshot == null ? null : new PresentedViewportFrame(id, snapshot, projectionAspect,
                PROJECTION_Z_NEAR, projectionDistance, projectionZeroToOne, scene,
                getViewRotationProjectionMatrix(new Matrix4f()));
    }

    @Override public Frustum getCullFrustum() { return frustum; }
    @Override public Matrix4f getViewRotationProjectionMatrix(Matrix4f dest) {
        return dest.set(projection).mul(getViewRotationMatrix(new Matrix4f()));
    }
    @Override public boolean isDetached() { return true; }
    @Override public float getFov() { return snapshot == null ? BASE_HUD_FOV : (float) snapshot.verticalFovDegrees(); }

    @Override public void extractRenderState(CameraRenderState state, float partialTick) {
        super.extractRenderState(state, partialTick);
        state.projectionMatrix.set(projection);
        state.depthFar = projectionFar;
        state.cullFrustum.set(frustum);
        // Navigation is unaffected by the player's hurt animation, walk bob or bed.
        state.entityRenderState.isLiving = false;
        state.entityRenderState.isPlayer = false;
        state.entityRenderState.isSleeping = false;
        state.entityRenderState.hurtTime = 0;
        state.entityRenderState.deathTime = 0;
    }
}
