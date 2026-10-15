package com.mine.geometry_node.client.runtime.game;

import com.mine.geometry_node.client.input.ClientBlueprintInputManager;
import com.mine.geometry_node.client.ui.MainUI;
import icyllis.modernui.mc.MuiScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** One game input owner, independent of the number of views displaying the game frame. */
public final class GameViewportInput {
    private static final Set<Object> VISIBLE = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Object> BLOCKING_WORKSPACES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static volatile Object capturedOwner;
    private static Screen suspendedEditor;
    private static boolean consumeEscapeRelease;

    private GameViewportInput() {}

    public static void setVisible(Object owner, boolean visible) {
        synchronized (VISIBLE) {
            if (visible) VISIBLE.add(owner);
            else VISIBLE.remove(owner);
        }
        if (!visible) release(owner);
    }

    public static boolean hasVisibleViews() {
        synchronized (VISIBLE) { return !VISIBLE.isEmpty(); }
    }

    public static void setWorkspaceBlocked(Object workspace, boolean blocked) {
        synchronized (BLOCKING_WORKSPACES) {
            if (blocked) BLOCKING_WORKSPACES.add(workspace);
            else BLOCKING_WORKSPACES.remove(workspace);
        }
        if (blocked) releaseAll();
    }

    private static boolean isWorkspaceBlocked() {
        synchronized (BLOCKING_WORKSPACES) { return !BLOCKING_WORKSPACES.isEmpty(); }
    }

    /** Call after the activation click has been released, never on mouse down. */
    public static void requestCapture(Object owner) {
        if (owner == null || Boolean.getBoolean("gn.standalone")) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            synchronized (VISIBLE) { if (!VISIBLE.contains(owner)) return; }
            if (isWorkspaceBlocked() || !isEditor(mc.screen) || mc.player == null || mc.level == null
                    || mc.player.isDeadOrDying() || !mc.isWindowActive() || mc.getOverlay() != null) return;
            releaseNow(mc);
            capturedOwner = owner;
            // The mouse mixin suppresses grabMouse's setScreen(null) only for this capture.
            mc.mouseHandler.grabMouse();
            // macOS may restore physically held keys in grabMouse; activation starts clean.
            KeyMapping.releaseAll();
            mc.mouseHandler.setIgnoreFirstMove();
        });
    }

    public static boolean isCaptured(Object owner) { return owner != null && capturedOwner == owner; }

    public static void release(Object owner) {
        if (owner == null || Boolean.getBoolean("gn.standalone")) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> { if (capturedOwner == owner) releaseNow(mc); });
    }

    public static void releaseAll() {
        if (Boolean.getBoolean("gn.standalone")) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> releaseNow(mc));
    }

    private static void releaseNow(Minecraft mc) {
        if (capturedOwner == null) return;
        capturedOwner = null;
        ClientBlueprintInputManager.releasePressedInputs();
        KeyMapping.releaseAll();
        mc.mouseHandler.releaseMouse();
        mc.mouseHandler.setIgnoreFirstMove();
        if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        if (mc.player != null && mc.player.isUsingItem() && mc.gameMode != null) {
            mc.gameMode.releaseUsingItem(mc.player);
        }
    }

    public static boolean isEditor(Screen screen) {
        return screen instanceof MuiScreen mui && mui.getFragment() instanceof MainUI;
    }

    public static boolean isGameInputActive() {
        if (capturedOwner == null) return false;
        Minecraft mc = Minecraft.getInstance();
        return isEditor(mc.screen) && mc.player != null && mc.level != null
                && !isWorkspaceBlocked() && mc.isWindowActive() && mc.getOverlay() == null;
    }

    /** Only keyboard/mouse handlers and handleKeybinds use this scoped Screen view. */
    public static Screen screenForGameInput(Screen screen) {
        return isGameInputActive() && isEditor(screen) ? null : screen;
    }

    public static boolean handleEscape(int action, int key) {
        if (key != GLFW.GLFW_KEY_ESCAPE) return false;
        if (action == GLFW.GLFW_RELEASE && consumeEscapeRelease) {
            consumeEscapeRelease = false;
            return true;
        }
        if (action != GLFW.GLFW_RELEASE && isGameInputActive()) {
            consumeEscapeRelease = true;
            releaseNow(Minecraft.getInstance());
            return true;
        }
        if (action == GLFW.GLFW_PRESS && suspendedEditor != null) {
            // Let the vanilla screen consume this press. If it restores the
            // workspace, repeats and the release still belong to that screen.
            consumeEscapeRelease = true;
            return false;
        }
        return consumeEscapeRelease;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (capturedOwner != null && !isGameInputActive()) releaseNow(mc);
        if (isEditor(mc.screen) && hasVisibleViews() && mc.player != null && mc.level != null
                && mc.player.isDeadOrDying()) {
            // Let vanilla select its death Screen. The editor is retained until respawn.
            suspendedEditor = mc.screen;
            releaseNow(mc);
            mc.setScreen(null);
        } else if (isEditor(mc.screen) && hasVisibleViews() && mc.player != null && mc.level != null
                && mc.player.isSleeping()) {
            suspendedEditor = mc.screen;
            releaseNow(mc);
            mc.gui.getChat().openScreen(ChatComponent.ChatMethod.MESSAGE, InBedChatScreen::new);
        }
    }

    /** Called at setScreen's argument boundary; never changes Minecraft.screen directly. */
    public static Screen prepareScreenChange(Screen requested) {
        Minecraft mc = Minecraft.getInstance();
        if (suspendedEditor != null) {
            if (requested == null && mc.level != null && mc.player != null && !mc.player.isDeadOrDying()) {
                return suspendedEditor;
            }
            if (requested instanceof MuiScreen && requested != suspendedEditor) discardSuspendedEditor();
        }
        if (isGameInputActive() && requested != null && requested != mc.screen) {
            if (!(requested instanceof MuiScreen)) suspendedEditor = mc.screen;
            releaseNow(mc);
        }
        return requested;
    }

    public static boolean retainEditor(Screen screen) { return screen == suspendedEditor; }

    /** Container onClose first closes the player's menu, restoring our editor,
     * then calls Screen.onClose, which would pop the restored editor again.
     * A stale screen must not close the editor that has replaced it.
     */
    public static boolean ignoreStaleScreenClose(Screen closingScreen) {
        Screen current = Minecraft.getInstance().screen;
        return closingScreen != current && isEditor(current);
    }

    public static void screenChanged() {
        Minecraft mc = Minecraft.getInstance();
        if (suspendedEditor != null && mc.screen == suspendedEditor) suspendedEditor = null;
        if (capturedOwner != null && !isEditor(mc.screen)) releaseNow(mc);
    }

    /** ModernUI otherwise forwards global mouse events to its retained fragment. */
    public static boolean suppressModernUiInput() {
        return isGameInputActive() || suspendedEditor != null;
    }

    public static void reset() {
        releaseAll();
        consumeEscapeRelease = false;
        discardSuspendedEditor();
    }

    private static void discardSuspendedEditor() {
        Screen previous = suspendedEditor;
        suspendedEditor = null;
        if (previous != null) previous.removed();
    }
}
