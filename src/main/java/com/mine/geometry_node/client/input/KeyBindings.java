package com.mine.geometry_node.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.mine.geometry_node.GeometryNode;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    private static final KeyMapping.Category MAIN_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(GeometryNode.MODID, "geometry_node")
    );
    private static final KeyMapping.Category SKILLS_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(GeometryNode.MODID, "geometry_node_skills")
    );

    public static final KeyMapping OPEN_EDITOR = new KeyMapping(
            "input.geometry_node.open_editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            MAIN_CATEGORY
    );

    public static final KeyMapping OPEN_QUEST_SCREEN = new KeyMapping(
            "input.geometry_node.open_quest_screen",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            MAIN_CATEGORY
    );

    // 统一定义技能按键的数量
    public static final int SKILL_COUNT = 10;

    public static final KeyMapping[] BLUEPRINT_KEYS = new KeyMapping[SKILL_COUNT];

    static {
        for (int i = 0; i < SKILL_COUNT; i++) {
            BLUEPRINT_KEYS[i] = new KeyMapping(
                    "input.geometry_node.blueprint_skill_" + (i + 1),
                    InputConstants.Type.KEYSYM,
                    InputConstants.UNKNOWN.getValue(),
                    SKILLS_CATEGORY
            );
        }
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_EDITOR);
        event.register(OPEN_QUEST_SCREEN);
        for (KeyMapping key : BLUEPRINT_KEYS) {
            event.register(key);
        }
    }
}
