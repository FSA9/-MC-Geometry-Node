package com.mine.geometry_node.client.runtime.game.render;

import com.mine.geometry_node.client.runtime.game.camera.EditViewportCamera;
import com.mine.geometry_node.client.runtime.game.camera.EditViewportProjection;
import com.mine.geometry_node.client.scene.ClientSceneService;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** The immutable camera/scene that produced one published image, never the latest UI camera. */
public final class PresentedViewportFrame {
    private final long frameId;
    private final EditViewportCamera.Snapshot camera;
    private final float aspect, near, far;
    private final boolean zeroToOne;
    private final ClientSceneService.Snapshot scene;
    private final java.util.List<com.mine.geometry_node.client.runtime.game.selection.ViewportTarget> targets;
    private final Matrix4d viewProjection;
    private final Matrix4d inverseViewProjection;

    public PresentedViewportFrame(long frameId, EditViewportCamera.Snapshot camera, float aspect,
                                  float near, float far, boolean zeroToOne, ClientSceneService.Snapshot scene) {
        this(frameId, camera, aspect, near, far, zeroToOne, scene, projection(camera, aspect, near, far, zeroToOne));
    }

    PresentedViewportFrame(long frameId, EditViewportCamera.Snapshot camera, float aspect,
                           float near, float far, boolean zeroToOne, ClientSceneService.Snapshot scene,
                           Matrix4fc actualViewProjection) {
        this(frameId, camera, aspect, near, far, zeroToOne, scene, actualViewProjection, java.util.List.of());
    }

    private PresentedViewportFrame(long frameId, EditViewportCamera.Snapshot camera, float aspect,
            float near, float far, boolean zeroToOne, ClientSceneService.Snapshot scene, Matrix4fc actualViewProjection,
            java.util.List<com.mine.geometry_node.client.runtime.game.selection.ViewportTarget> targets) {
        this.targets = java.util.List.copyOf(targets);
        this.frameId = frameId;
        this.camera = camera;
        this.aspect = aspect;
        this.near = near;
        this.far = far;
        this.zeroToOne = zeroToOne;
        this.scene = scene;
        viewProjection = new Matrix4d(actualViewProjection);
        inverseViewProjection = new Matrix4d(viewProjection).invert();
    }

    private static Matrix4f projection(EditViewportCamera.Snapshot camera, float aspect,
                                       float near, float far, boolean zeroToOne) {
        // Matches Minecraft's rotation-only view: world translation is kept double precision.
        return EditViewportProjection.set(new Matrix4f(), camera, aspect, near, far, zeroToOne)
                .rotateX((float) Math.toRadians(camera.pitch()))
                .rotateY((float) Math.toRadians(camera.yaw() + 180));
    }

    public PresentedViewportFrame withTargets(java.util.List<com.mine.geometry_node.client.runtime.game.selection.ViewportTarget> targets) {
        return new PresentedViewportFrame(frameId, camera, aspect, near, far, zeroToOne, scene,
                new Matrix4f(viewProjection), targets);
    }
    public java.util.List<com.mine.geometry_node.client.runtime.game.selection.ViewportTarget> targets() { return targets; }
    public long frameId() { return frameId; }
    public EditViewportCamera.Snapshot camera() { return camera; }
    public float aspect() { return aspect; }
    public float near() { return near; }
    public float far() { return far; }
    public boolean zeroToOne() { return zeroToOne; }
    public ClientSceneService.Snapshot scene() { return scene; }
    public Matrix4dc viewProjection(Matrix4d destination) { return destination.set(viewProjection); }
    public Matrix4dc inverseViewProjection(Matrix4d destination) { return destination.set(inverseViewProjection); }
}
