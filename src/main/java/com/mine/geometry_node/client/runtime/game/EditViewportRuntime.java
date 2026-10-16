package com.mine.geometry_node.client.runtime.game;

import icyllis.modernui.graphics.Canvas;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/** E1 schedules one editor camera into the normal world pass. No second world pass is hidden here. */
public final class EditViewportRuntime {
    private static final Map<Object, EditViewportCamera> VISIBLE = new LinkedHashMap<>();
    private static final GameFrameImage.Channel IMAGE = new GameFrameImage.Channel();
    private static final EditorRenderCamera RENDER_CAMERA = new EditorRenderCamera();
    private static volatile Object frameOwner;
    private static volatile boolean rendering;
    private static volatile boolean navigationAllowed;
    private static final Map<EditViewportCamera, WeakReference<Object>> CAMERA_LEVELS = new WeakHashMap<>();

    private EditViewportRuntime() {}

    public static synchronized void setVisible(Object owner, EditViewportCamera camera, boolean visible) {
        if (visible) VISIBLE.put(owner, camera);
        else VISIBLE.remove(owner);
        if (visible) GameViewportInput.releaseAll();
        else EditViewportFlightInput.release(owner);
    }

    public static synchronized boolean hasVisibleViews() { return !VISIBLE.isEmpty(); }
    public static boolean isRendering() { return rendering; }
    public static boolean isOwner(Object owner) { return rendering && frameOwner == owner; }
    public static synchronized boolean ownsCamera(Object owner, EditViewportCamera camera) {
        return isOwner(owner) && VISIBLE.get(owner) == camera;
    }
    public static boolean navigateAllowed(Object owner) {
        return isOwner(owner) && navigationAllowed && !GameViewportInput.isWorkspaceBlocked();
    }
    public static boolean isAvailable(Object owner) { return isOwner(owner) && IMAGE.isAvailable(); }
    public static GameFrameImage.Size size(Object owner) {
        return isOwner(owner) ? IMAGE.size() : new GameFrameImage.Size(0, 0);
    }
    public static synchronized boolean draw(Object owner, Canvas canvas, GameFrameImage.Size size,
                               float left, float top, float right, float bottom) {
        return isOwner(owner) && IMAGE.draw(canvas, size, left, top, right, bottom);
    }

    /** Render thread, before Camera/LevelRenderer.update. Selection is frozen through extraction/render. */
    public static synchronized void beginFrame(Camera playerCamera) {
        Minecraft mc = Minecraft.getInstance();
        Object nextOwner = null;
        EditViewportCamera nextCamera = null;
        if (GameViewportInput.isEditor(mc.screen) && mc.level != null && mc.player != null && !VISIBLE.isEmpty()) {
            var entry = VISIBLE.entrySet().iterator().next();
            nextOwner = entry.getKey();
            nextCamera = entry.getValue();
            var previousLevel = CAMERA_LEVELS.get(nextCamera);
            if (previousLevel == null || previousLevel.get() != mc.level) {
                reset(nextCamera, playerCamera);
                CAMERA_LEVELS.put(nextCamera, new WeakReference<>(mc.level));
            }
            nextCamera.initialize(playerCamera.position().x, playerCamera.position().y, playerCamera.position().z,
                    playerCamera.yRot(), playerCamera.xRot());
        }
        EditViewportFlightInput.updateFrame(nextOwner, nextCamera);
        if (nextCamera != null) RENDER_CAMERA.configure(mc.level, mc.player, nextCamera.snapshot());
        if (nextOwner != frameOwner) IMAGE.clear();
        frameOwner = nextOwner;
        rendering = nextCamera != null;
        navigationAllowed = rendering && mc.isWindowActive() && mc.getOverlay() == null;
    }

    public static Camera camera() { return RENDER_CAMERA; }
    public static boolean isOrthographic() { return rendering && RENDER_CAMERA.isOrthographic(); }
    static Object frameOwner() { return rendering ? frameOwner : null; }
    public static void tickCamera() { if (rendering) RENDER_CAMERA.tick(); }
    static GameFrameImage.Channel image() { return IMAGE; }

    public static void resetToPlayer(Object owner) {
        if (Boolean.getBoolean("gn.standalone")) return;
        Minecraft.getInstance().execute(() -> {
            synchronized (EditViewportRuntime.class) {
                EditViewportCamera camera = VISIBLE.get(owner);
                Minecraft mc = Minecraft.getInstance();
                if (camera != null && mc.level != null && mc.player != null) {
                    // Called between render frames, when GameRenderer exposes the real player camera.
                    reset(camera, mc.gameRenderer.getMainCamera());
                }
            }
        });
    }

    private static void reset(EditViewportCamera camera, Camera player) {
        camera.reset(player.position().x, player.position().y, player.position().z, player.yRot(), player.xRot());
    }

    public static synchronized void close() {
        EditViewportFlightInput.releaseAll();
        EditCameraChunkClient.release();
        rendering = false;
        navigationAllowed = false;
        frameOwner = null;
        CAMERA_LEVELS.clear();
        IMAGE.clear();
        RENDER_CAMERA.reset();
    }
}
