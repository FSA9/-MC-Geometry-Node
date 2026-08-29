package com.mine.geometry_node.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.mine.geometry_node.core.engine.blueprint.event.PlayerInputKeys;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketPlayerInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class ClientBlueprintInputManager {

    private static final int[] DIRECT_KEY_CODES = {GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER};

    // Tracks direct keys, modifiers, and configurable blueprint skill keys.
    private static final int TOTAL_TRACKED = PlayerInputKeys.trackedCount();
    private static final boolean[] lastStates = new boolean[TOTAL_TRACKED];
    private static int interceptionMask;

    public static void setInterceptionMask(int mask) {
        int validMask = (1 << TOTAL_TRACKED) - 1;
        interceptionMask = mask & validMask;
    }

    public static void reset() {
        interceptionMask = 0;
        java.util.Arrays.fill(lastStates, false);
        for (KeyMapping mapping : KeyBindings.BLUEPRINT_KEYS) {
            mapping.setDown(false);
        }
    }

    /** Returns true when vanilla processing for this world key event must be cancelled. */
    public static boolean interceptKeyboard(int action, KeyEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || interceptionMask == 0) return false;

        boolean intercept = matchesInterceptedDirectKey(event.key());
        boolean[] matchingSkills = new boolean[PlayerInputKeys.skillCount()];
        for (int i = 0; i < PlayerInputKeys.skillCount(); i++) {
            matchingSkills[i] = KeyBindings.BLUEPRINT_KEYS[i].matches(event);
            if (matchingSkills[i] && isIntercepted(PlayerInputKeys.skillOffset() + i)) {
                intercept = true;
            }
        }
        if (!intercept) return false;

        boolean down = action != GLFW.GLFW_RELEASE;
        KeyMapping.set(InputConstants.getKey(event), false);
        for (int i = 0; i < matchingSkills.length; i++) {
            if (matchingSkills[i]) KeyBindings.BLUEPRINT_KEYS[i].setDown(down);
        }
        return true;
    }

    /** Returns true when vanilla processing for this world mouse event must be cancelled. */
    public static boolean interceptMouse(int action, MouseButtonInfo buttonInfo) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || interceptionMask == 0) return false;

        MouseButtonEvent event = new MouseButtonEvent(0.0D, 0.0D, buttonInfo);
        boolean intercept = false;
        boolean[] matchingSkills = new boolean[PlayerInputKeys.skillCount()];
        for (int i = 0; i < PlayerInputKeys.skillCount(); i++) {
            matchingSkills[i] = KeyBindings.BLUEPRINT_KEYS[i].matchesMouse(event);
            if (matchingSkills[i] && isIntercepted(PlayerInputKeys.skillOffset() + i)) {
                intercept = true;
            }
        }
        if (!intercept) return false;

        boolean down = action != GLFW.GLFW_RELEASE;
        KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(buttonInfo.button()), false);
        for (int i = 0; i < matchingSkills.length; i++) {
            if (matchingSkills[i]) KeyBindings.BLUEPRINT_KEYS[i].setDown(down);
        }
        return true;
    }

    private static boolean matchesInterceptedDirectKey(int keyCode) {
        for (int i = 0; i < DIRECT_KEY_CODES.length; i++) {
            if (keyCode == DIRECT_KEY_CODES[i] && isIntercepted(i)) return true;
        }
        return (isIntercepted(PlayerInputKeys.indexOf(PlayerInputKeys.CTRL))
                && (keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL))
                || (isIntercepted(PlayerInputKeys.indexOf(PlayerInputKeys.SHIFT))
                && (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT))
                || (isIntercepted(PlayerInputKeys.indexOf(PlayerInputKeys.ALT))
                && (keyCode == GLFW.GLFW_KEY_LEFT_ALT || keyCode == GLFW.GLFW_KEY_RIGHT_ALT));
    }

    private static boolean isIntercepted(int index) {
        return (interceptionMask & (1 << index)) != 0;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        for (int i = 0; i < TOTAL_TRACKED; i++) {
            boolean isCurrentlyDown;
            String keyId;

            if (i < PlayerInputKeys.DIRECT_KEYS.size()) {
                isCurrentlyDown = InputConstants.isKeyDown(mc.getWindow(), DIRECT_KEY_CODES[i]);
                keyId = PlayerInputKeys.DIRECT_KEYS.get(i);
            }
            // 接下来 3 个是硬编码的修饰键状态同步
            else if (i < PlayerInputKeys.skillOffset()) {
                int modIndex = i - PlayerInputKeys.DIRECT_KEYS.size();
                keyId = PlayerInputKeys.MODIFIER_KEYS.get(modIndex);
                isCurrentlyDown = switch (keyId) {
                    case PlayerInputKeys.CTRL -> mc.hasControlDown();
                    case PlayerInputKeys.SHIFT -> mc.hasShiftDown();
                    case PlayerInputKeys.ALT -> mc.hasAltDown();
                    default -> false;
                };
            }
            // Remaining entries are configurable blueprint skill keys.
            else {
                int skillIndex = i - PlayerInputKeys.skillOffset();
                isCurrentlyDown = KeyBindings.BLUEPRINT_KEYS[skillIndex].isDown();
                keyId = PlayerInputKeys.skillId(skillIndex);
            }

            boolean wasDown = lastStates[i];

            // 1. 触发按下
            if (isCurrentlyDown && !wasDown) {
                sendInputPacket(keyId, "PRESS", mc.player.getDeltaMovement());
            }

            // 2. 触发抬起
            if (!isCurrentlyDown && wasDown) {
                sendInputPacket(keyId, "RELEASE", mc.player.getDeltaMovement());
            }

            lastStates[i] = isCurrentlyDown;
        }
    }

    private static void sendInputPacket(String keyId, String action, Vec3 clientVelocity) {
        NetworkHandler.sendToServer(new PacketPlayerInput(keyId, action, clientVelocity));
    }
}
