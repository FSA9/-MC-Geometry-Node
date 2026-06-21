package com.mine.geometry_node.client.key;

import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketPlayerInput;
import net.minecraft.client.Minecraft;

public class ClientBlueprintInputManager {

    private static final long DOUBLE_CLICK_THRESHOLD = 300;

    // 状态记录器：10个技能键 + 3个修饰键 (Ctrl, Shift, Alt)
    private static final int TOTAL_TRACKED = KeyBindings.SKILL_COUNT + 3;
    private static final boolean[] lastStates = new boolean[TOTAL_TRACKED];
    private static final long[] pressTimes = new long[TOTAL_TRACKED];
    private static final long[] lastReleaseTimes = new long[TOTAL_TRACKED];

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        long currentTime = System.currentTimeMillis();

        for (int i = 0; i < TOTAL_TRACKED; i++) {
            boolean isCurrentlyDown;
            String keyId;

            // 前 10 个是蓝图技能键
            if (i < KeyBindings.SKILL_COUNT) {
                isCurrentlyDown = KeyBindings.BLUEPRINT_KEYS[i].isDown();
                keyId = "skill_" + (i + 1);
            }
            // 后 3 个是硬编码的修饰键状态同步
            else {
                int modIndex = i - KeyBindings.SKILL_COUNT;
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

            boolean wasDown = lastStates[i];

            // 1. 触发按下
            if (isCurrentlyDown && !wasDown) {
                pressTimes[i] = currentTime;
                if (currentTime - lastReleaseTimes[i] <= DOUBLE_CLICK_THRESHOLD) {
                    sendInputPacket(keyId, "DOUBLE_CLICK", 0);
                    lastReleaseTimes[i] = 0;
                } else {
                    sendInputPacket(keyId, "PRESS", 0);
                }
            }

            // 2. 触发抬起
            if (!isCurrentlyDown && wasDown) {
                long durationMs = currentTime - pressTimes[i];
                lastReleaseTimes[i] = currentTime;
                sendInputPacket(keyId, "RELEASE", durationMs);
            }

            lastStates[i] = isCurrentlyDown;
        }
    }

    private static void sendInputPacket(String keyId, String action, long durationMs) {
        NetworkHandler.sendToServer(new PacketPlayerInput(keyId, action, durationMs));
    }
}
