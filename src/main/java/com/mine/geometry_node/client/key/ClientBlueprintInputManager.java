package com.mine.geometry_node.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketPlayerInput;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class ClientBlueprintInputManager {

    private static final String[] DIRECT_KEY_IDS = {"space", "tab", "enter"};
    private static final int[] DIRECT_KEY_CODES = {GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER};

    // 状态记录器：3个直接按键 + 3个修饰键 (Ctrl, Shift, Alt) + 10个技能键
    private static final int TOTAL_TRACKED = DIRECT_KEY_IDS.length + KeyBindings.SKILL_COUNT + 3;
    private static final boolean[] lastStates = new boolean[TOTAL_TRACKED];
    private static final long[] pressTicks = new long[TOTAL_TRACKED];
    private static long tickCounter;

    public static void tick() {
        long currentTick = ++tickCounter;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        for (int i = 0; i < TOTAL_TRACKED; i++) {
            boolean isCurrentlyDown;
            String keyId;

            if (i < DIRECT_KEY_IDS.length) {
                isCurrentlyDown = InputConstants.isKeyDown(mc.getWindow(), DIRECT_KEY_CODES[i]);
                keyId = DIRECT_KEY_IDS[i];
            }
            // 接下来 3 个是硬编码的修饰键状态同步
            else if (i < DIRECT_KEY_IDS.length + 3) {
                int modIndex = i - DIRECT_KEY_IDS.length;
                isCurrentlyDown = switch (modIndex) {
                    case 0 -> mc.hasControlDown();
                    case 1 -> mc.hasShiftDown();
                    case 2 -> mc.hasAltDown();
                    default -> false;
                };
                keyId = switch (modIndex) {
                    case 0 -> "ctrl";
                    case 1 -> "shift";
                    case 2 -> "alt";
                    default -> "unknown";
                };
            }
            // 后 10 个是蓝图技能键
            else {
                int skillIndex = i - DIRECT_KEY_IDS.length - 3;
                isCurrentlyDown = KeyBindings.BLUEPRINT_KEYS[skillIndex].isDown();
                keyId = "skill_" + (skillIndex + 1);
            }

            boolean wasDown = lastStates[i];

            // 1. 触发按下
            if (isCurrentlyDown && !wasDown) {
                pressTicks[i] = currentTick;
                sendInputPacket(keyId, "PRESS", 0, mc.player.getDeltaMovement());
            }

            // 2. 触发抬起
            if (!isCurrentlyDown && wasDown) {
                int durationTicks = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, currentTick - pressTicks[i]));
                sendInputPacket(keyId, "RELEASE", durationTicks, mc.player.getDeltaMovement());
            }

            lastStates[i] = isCurrentlyDown;
        }
    }

    private static void sendInputPacket(String keyId, String action, int durationTicks, Vec3 clientVelocity) {
        NetworkHandler.sendToServer(new PacketPlayerInput(keyId, action, durationTicks, clientVelocity));
    }
}
