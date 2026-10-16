package com.mine.geometry_node.client.runtime.game.render;

import com.mine.geometry_node.client.runtime.game.camera.EditViewportCamera;
import com.mine.geometry_node.client.runtime.game.chunk.EditCameraChunkClient;
import com.mine.geometry_node.client.runtime.game.diagnostic.GameViewTiming;
import com.mine.geometry_node.client.runtime.game.input.EditViewportFlightInput;
import com.mine.geometry_node.client.runtime.game.input.GameViewportInput;

import com.mine.geometry_node.client.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.core.engine.system.viewport.EditCameraObservers;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import icyllis.modernui.graphics.Canvas;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/** Budgeted multi-view rendering: independent terrain contexts, one complete world pass per frame. */
public final class EditViewportRuntime {
    private static final Object GAME = new Object();
    private static final Map<Object, Request> VISIBLE = new LinkedHashMap<>();
    private static final Map<Object, Session> SESSIONS = new LinkedHashMap<>();
    private static final ViewportFrameScheduler<Object> SCHEDULER = new ViewportFrameScheduler<>();
    private static volatile Session frame;
    private static volatile boolean navigationAllowed;
    private static volatile boolean basicShading;
    private static volatile GameFrameImage.Size framebuffer = new GameFrameImage.Size(0, 0);
    private static final Map<EditViewportCamera, WeakReference<Object>> CAMERA_LEVELS = new WeakHashMap<>();
    private static Object currentLevel;
    private static Object preferred;
    private static boolean targetActive;
    private static boolean workspaceFrame;
    private static boolean budgetSources;
    private static Object frameSource;

    private static final class Request {
        final EditViewportCamera camera;
        double demand = 1;
        Request(EditViewportCamera camera) { this.camera = camera; }
    }
    private static final class Session implements AutoCloseable {
        final EditViewportCamera camera;
        final EditorRenderCamera renderCamera = new EditorRenderCamera();
        final ViewportFrameSurface surface = new ViewportFrameSurface(new GameFrameImage.Channel());
        final ViewportResolution resolution = new ViewportResolution();
        EditViewportRenderContext context;
        TextureTarget target;
        GameFrameImage.Size requestedSize = new GameFrameImage.Size(0, 0);
        Session(EditViewportCamera camera) { this.camera = camera; }
        @Override public void close() {
            surface.clear();
            if (target != null) { target.destroyBuffers(); target = null; }
            if (context != null) { context.close(); context = null; }
            renderCamera.reset();
        }
    }

    private EditViewportRuntime() {}

    public static synchronized void setVisible(Object owner, EditViewportCamera camera, boolean visible) {
        if (visible) VISIBLE.computeIfAbsent(owner, key -> new Request(camera));
        else {
            VISIBLE.remove(owner);
            if (preferred == owner) preferred = null;
            EditViewportFlightInput.release(owner);
        }
    }
    public static synchronized void setDemand(Object owner, double scale) {
        Request request = VISIBLE.get(owner);
        if (request != null) request.demand = Double.isFinite(scale) ? Math.clamp(scale, 0, 1) : 0;
    }
    public static synchronized void setPreferred(Object owner, boolean active) {
        if (active) preferred = owner;
        else if (preferred == owner) preferred = null;
    }
    public static synchronized void touch(Object owner) { preferred = owner; }
    public static GameFrameImage.Size framebufferSize() { return framebuffer; }
    public static synchronized boolean hasVisibleViews() {
        for (Request request : VISIBLE.values()) if (request.demand > 0) return true;
        return false;
    }
    public static boolean isRendering() { return frame != null; }
    public static boolean isBudgetIdleFrame() { return workspaceFrame && budgetSources && frameSource == null; }
    public static boolean hasScheduledWorld() { return frameSource != null; }
    public static boolean shouldAdvanceWorld(boolean advance) { return advance && (!workspaceFrame || hasScheduledWorld()); }
    static synchronized void frameCompleted() { SCHEDULER.completed(frameSource, System.nanoTime()); }
    /** Membership, independent of whose frame is being rendered now. */
    public static synchronized boolean isOwner(Object owner) {
        // Status, input and drawing all ask this on the UI hot path. Keep the
        // same insertion-order admission rule without constructing a list per query.
        int remaining = EditCameraObservers.MAX_VIEWS;
        for (var entry : VISIBLE.entrySet()) {
            if (entry.getValue().demand <= 0) continue;
            if (Objects.equals(entry.getKey(), owner)) return true;
            if (--remaining == 0) break;
        }
        return false;
    }
    public static synchronized boolean isLimited(Object owner) { return VISIBLE.containsKey(owner) && !isOwner(owner); }
    public static synchronized boolean ownsCamera(Object owner, EditViewportCamera camera) {
        Request request = VISIBLE.get(owner);
        return request != null && request.camera == camera && isOwner(owner);
    }
    public static boolean navigateAllowed(Object owner) {
        return navigationAllowed && isOwner(owner) && !GameViewportInput.isWorkspaceBlocked();
    }
    public static boolean useBasicShading() { return basicShading; }
    public static synchronized boolean isAvailable(Object owner) {
        Session session = SESSIONS.get(owner);
        return isOwner(owner) && session != null && session.surface.image.isAvailable();
    }
    public static synchronized GameFrameImage.Size size(Object owner) {
        Session session = SESSIONS.get(owner);
        return isOwner(owner) && session != null ? session.surface.image.size() : new GameFrameImage.Size(0, 0);
    }
    public static synchronized boolean draw(Object owner, Canvas canvas, GameFrameImage.Size size,
                                             float left, float top, float right, float bottom) {
        Session session = SESSIONS.get(owner);
        return isOwner(owner) && session != null && session.surface.image.draw(canvas, size, left, top, right, bottom);
    }
    private static List<Object> admitted() {
        return VISIBLE.entrySet().stream().filter(e -> e.getValue().demand > 0)
                .limit(EditCameraObservers.MAX_VIEWS).map(Map.Entry::getKey).toList();
    }

    /** Freeze membership and camera before the normal update/extract/render sequence. */
    public static synchronized void beginFrame(Camera playerCamera) {
        Minecraft mc = Minecraft.getInstance();
        targetActive = false;
        frame = null;
        frameSource = null;
        budgetSources = false;
        var nextFramebuffer = new GameFrameImage.Size(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        if (!nextFramebuffer.equals(framebuffer)) {
            for (Session session : SESSIONS.values()) session.surface.clear();
            SharedGameFrame.clearImage();
        }
        framebuffer = nextFramebuffer;
        if (currentLevel != mc.level) {
            clearSessions();
            SharedGameFrame.clearImage();
            currentLevel = mc.level;
        }
        boolean workspace = GameViewportInput.isEditor(mc.screen) && mc.level != null && mc.player != null;
        workspaceFrame = workspace;
        navigationAllowed = workspace && mc.isWindowActive() && mc.getOverlay() == null;
        List<Object> admitted = admitted();
        // UI visibility can change mid-frame; retire only here, never from a View callback.
        var iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!admitted.contains(entry.getKey())) { entry.getValue().close(); iterator.remove(); }
        }
        basicShading = workspace && !admitted.isEmpty();
        if (!workspace) { EditViewportFlightInput.validate(); return; }
        for (Object owner : admitted) {
            Request request = VISIBLE.get(owner);
            Session session = SESSIONS.computeIfAbsent(owner, key -> {
                var previous = CAMERA_LEVELS.get(request.camera);
                if (previous == null || previous.get() != mc.level) {
                    request.camera.reset(playerCamera.position().x, playerCamera.position().y, playerCamera.position().z,
                            playerCamera.yRot(), playerCamera.xRot());
                    CAMERA_LEVELS.put(request.camera, new WeakReference<>(mc.level));
                }
                return new Session(request.camera);
            });
            session.requestedSize = session.resolution.select(framebuffer.width(), framebuffer.height(),
                    request.demand, System.nanoTime());
        }
        EditViewportFlightInput.updateFrame();
        List<Object> sources = new ArrayList<>(admitted);
        if (SharedGameFrame.hasConsumers()) sources.add(GAME);
        budgetSources = !sources.isEmpty();
        Object priority = EditViewportFlightInput.capturedOwner();
        if (priority == null) priority = GameViewportInput.isGameInputActive() ? GAME : preferred;
        if (!sources.contains(priority) && SharedGameFrame.containsConsumer(priority)) priority = GAME;
        int backgroundFps = ConfigManager.INSTANCE.get(BuiltinConfigEntries.GAME_VIEW_BACKGROUND_FPS);
        frameSource = SCHEDULER.next(sources, priority, System.nanoTime(), backgroundFps);
        frame = SESSIONS.get(frameSource);
        if (frame != null) {
            frame.renderCamera.configure(mc.level, mc.player, frame.camera.snapshot());
            // Construction is lazy: at most one terrain context is created per client frame.
            try (var ignored = EditViewportShaderScope.enter(true)) {
                if (frame.context == null || frame.context.isClosed()) {
                    long creationStart = GameViewTiming.start();
                    frame.context = new EditViewportRenderContext(mc);
                    GameViewTiming.finish("create independent terrain context", creationStart, true);
                }
            }
        }
    }

    public static Camera camera() { return frame.renderCamera; }
    public static boolean isOrthographic() { return frame != null && frame.renderCamera.isOrthographic(); }
    public static EditViewportRenderContext renderContext() { return frame == null ? null : frame.context; }
    public static synchronized void tickCamera() {
        for (Session session : SESSIONS.values()) session.renderCamera.tick();
    }
    public static synchronized List<Vec3> observerPositions() {
        List<Object> owners = new ArrayList<>(admitted());
        if (owners.remove(preferred)) owners.addFirst(preferred);
        List<Vec3> result = new ArrayList<>();
        for (Object owner : owners) {
            var camera = VISIBLE.get(owner).camera;
            if (camera.isInitialized()) {
                var pose = camera.snapshot(); result.add(new Vec3(pose.x(), pose.y(), pose.z()));
            }
        }
        return List.copyOf(result);
    }

    /** After real-window resize, before the render method resolves its world target. */
    public static void beginWorldTarget() {
        Session session = frame;
        if (session == null) return;
        var size = session.requestedSize;
        if (!size.available()) return;
        if (session.target == null || session.target.width != size.width() || session.target.height != size.height()) {
            long allocationStart = GameViewTiming.start();
            if (session.target != null) session.target.destroyBuffers();
            session.target = new TextureTarget("GeometryNode edit world", size.width(), size.height(), true);
            GameViewTiming.finish("edit world target " + size.width() + "x" + size.height(), allocationStart, true);
        }
        // Outlines are attachments too; never leave a full-size depth/color target in this pass.
        session.context.resize(size.width(), size.height());
        targetActive = true;
    }
    public static RenderTarget worldTarget(RenderTarget original) {
        Session session = frame;
        return targetActive && session != null && session.target != null ? session.target : original;
    }
    public static int worldWidth(int original) { return targetActive ? frame.target.width : original; }
    public static int worldHeight(int original) { return targetActive ? frame.target.height : original; }
    public static void endWorldTarget() { targetActive = false; }
    static void captureFrame() {
        Session session = frame;
        if (session != null && session.target != null) session.surface.capture(session.target.getColorTexture());
    }

    public static void resetToPlayer(Object owner) {
        if (Boolean.getBoolean("gn.standalone")) return;
        Minecraft.getInstance().execute(() -> {
            synchronized (EditViewportRuntime.class) {
                Request request = VISIBLE.get(owner);
                Minecraft mc = Minecraft.getInstance();
                if (request != null && mc.level != null && mc.player != null) {
                    Camera player = mc.gameRenderer.getMainCamera();
                    request.camera.reset(player.position().x, player.position().y, player.position().z, player.yRot(), player.xRot());
                }
            }
        });
    }
    private static void clearSessions() {
        for (Session session : SESSIONS.values()) session.close();
        SESSIONS.clear();
        SCHEDULER.reset();
    }
    public static synchronized void close() {
        EditViewportFlightInput.releaseAll();
        EditCameraChunkClient.release();
        frame = null;
        frameSource = null;
        workspaceFrame = budgetSources = false;
        navigationAllowed = basicShading = targetActive = false;
        currentLevel = null;
        clearSessions();
    }
}
