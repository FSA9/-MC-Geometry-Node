package com.mine.geometry_node.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final KeyMapping OPEN_EDITOR = new KeyMapping(
            "key.geometry_node.open_editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.geometry_node"
    );

    // 统一定义技能按键的数量
    public static final int SKILL_COUNT = 10;

    public static final KeyMapping[] BLUEPRINT_KEYS = new KeyMapping[SKILL_COUNT];

    static {
        for (int i = 0; i < SKILL_COUNT; i++) {
            BLUEPRINT_KEYS[i] = new KeyMapping(
                    "key.geometry_node.blueprint_skill_" + (i + 1),
                    InputConstants.Type.KEYSYM,
                    InputConstants.UNKNOWN.getValue(),
                    "key.categories.geometry_node_skills"
            );
        }
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_EDITOR);
        for (KeyMapping key : BLUEPRINT_KEYS) {
            event.register(key);
        }
    }
}