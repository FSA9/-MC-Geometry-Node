package com.mine.geometry_node.client.runtime.game;

import com.mine.geometry_node.client.input.ClientBlueprintInputManager;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Render-thread mouse/keyboard capture belonging only to an editor camera. */
public final class EditViewportFlightInput {
    private static volatile Object owner;
    private static EditViewportCamera camera;
    private static EditViewportFlight flight;
    private static EditViewportFlightBindings bindings;
    private static ClientLevel level;
    private static long lastFrame;
    private static double returnX, returnY;
    private static final Set<Integer> HELD_KEYS = new HashSet<>();
    private record CaptureRequest(Object owner, EditViewportCamera camera) {}
    private static final AtomicReference<CaptureRequest> PENDING = new AtomicReference<>();

    private EditViewportFlightInput() {}
    public static boolean isCaptured() { return owner != null; }
    public static boolean isCaptured(Object candidate) { return candidate != null && owner == candidate; }

    public static void requestCapture(Object candidate, EditViewportCamera target) {
        if (candidate == null || Boolean.getBoolean("gn.standalone")) return;
        var request = new CaptureRequest(candidate, target);
        PENDING.set(request);
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (!PENDING.compareAndSet(request, null)) return;
            if (!EditViewportRuntime.ownsCamera(candidate, target)
                    || !EditViewportRuntime.navigateAllowed(candidate) || !canUse(mc)) return;
            GameViewportInput.releaseAll();
            flight = new EditViewportFlight(target);
            if (!flight.start()) { flight = null; return; }
            bindings = new EditViewportFlightBindings(ConfigManager.INSTANCE.getConfig().keyBindings.gameView);
            camera = target;
            level = mc.level;
            lastFrame = System.nanoTime();
            returnX = mc.mouseHandler.xpos();
            returnY = mc.mouseHandler.ypos();
            ClientBlueprintInputManager.releasePressedInputs();
            HELD_KEYS.clear();
            owner = candidate;
            try {
                mc.mouseHandler.grabMouse();
                KeyMapping.releaseAll();
                mc.mouseHandler.setIgnoreFirstMove();
                if (!mc.mouseHandler.isMouseGrabbed()) releaseNow(mc);
            } catch (RuntimeException | Error failure) {
                releaseNow(mc);
                throw failure;
            }
        });
    }

    private static boolean canUse(Minecraft mc) {
        return GameViewportInput.isEditor(mc.screen) && mc.level != null && mc.player != null
                && !mc.player.isDeadOrDying() && !mc.player.isSleeping()
                && mc.isWindowActive() && mc.getOverlay() == null && !GameViewportInput.isWorkspaceBlocked();
    }

    public static void validate() {
        if (owner == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (!canUse(mc) || mc.level != level || !EditViewportRuntime.ownsCamera(owner, camera)
                || !mc.mouseHandler.isMouseGrabbed()) releaseNow(mc);
    }

    /** Called before publishing the frame's immutable camera snapshot. */
    static void updateFrame(Object nextOwner, EditViewportCamera nextCamera) {
        validate();
        if (owner == null) return;
        if (owner != nextOwner || camera != nextCamera) { releaseNow(Minecraft.getInstance()); return; }
        long now = System.nanoTime();
        flight.advance((now - lastFrame) * 1.0e-9);
        lastFrame = now;
    }

    /** Consume all captured keys before player mappings, blueprint binds or UI. */
    public static boolean handleKey(int action, int key) {
        boolean captured = isCaptured();
        validate();
        if (owner == null) {
            if (action == GLFW.GLFW_PRESS) HELD_KEYS.remove(key);
            boolean consumed = HELD_KEYS.contains(key);
            if (action == GLFW.GLFW_RELEASE) HELD_KEYS.remove(key);
            return captured || consumed;
        }
        if (action == GLFW.GLFW_RELEASE) HELD_KEYS.remove(key);
        else HELD_KEYS.add(key);
        if (action != GLFW.GLFW_RELEASE && bindings.shouldExit(HELD_KEYS)) {
            releaseNow(Minecraft.getInstance());
            return true;
        }
        bindings.update(flight, HELD_KEYS);
        Minecraft.getInstance().getFramerateLimitTracker().onInputReceived();
        return true;
    }

    public static void look(double dx, double dy) {
        validate();
        if (owner == null) return;
        Minecraft mc = Minecraft.getInstance();
        double sensitivity = mc.options.sensitivity().get() * 0.6 + 0.2;
        double degrees = sensitivity * sensitivity * sensitivity * 8.0 * 0.15;
        flight.look(dx * degrees, dy * degrees);
        if (dx != 0 || dy != 0) mc.getFramerateLimitTracker().onInputReceived();
    }

    public static void release(Object candidate) {
        PENDING.updateAndGet(request -> request != null && request.owner() == candidate ? null : request);
        if (!isCaptured(candidate) || Boolean.getBoolean("gn.standalone")) return;
        Minecraft mc = Minecraft.getInstance();
        ViewportInputDispatch.run(mc, () -> { if (owner == candidate) releaseNow(mc); });
    }

    public static void releaseAll() {
        PENDING.set(null);
        if (Boolean.getBoolean("gn.standalone")) return;
        Minecraft mc = Minecraft.getInstance();
        ViewportInputDispatch.run(mc, () -> releaseNow(mc));
    }

    private static void releaseNow(Minecraft mc) {
        if (owner == null) return;
        owner = null;
        flight.stop();
        flight = null;
        bindings = null;
        camera = null;
        level = null;
        KeyMapping.releaseAll();
        mc.mouseHandler.releaseMouse();
        if (mc.isWindowActive() && GameViewportInput.isEditor(mc.screen)) {
            var window = mc.getWindow();
            GLFW.glfwSetCursorPos(window.handle(),
                    Math.clamp(returnX, 0, Math.max(0, window.getScreenWidth() - 1)),
                    Math.clamp(returnY, 0, Math.max(0, window.getScreenHeight() - 1)));
        }
        mc.mouseHandler.setIgnoreFirstMove();
        // Retain held key identities until their releases, including the exit binding, so a
        // captured key cannot subsequently close/activate the workspace.
    }
}
