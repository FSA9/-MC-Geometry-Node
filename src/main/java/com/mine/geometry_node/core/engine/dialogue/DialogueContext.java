package com.mine.geometry_node.core.engine.dialogue;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Per-execution dialogue defaults shared by following dialogue page nodes.
 */
public record DialogueContext(
        @Nullable ServerPlayer player,
        @Nullable String speaker,
        String styleId
) {
    public static final String TEMP_KEY = "geometry_node.dialogue.context";

    public DialogueContext {
        styleId = styleId == null || styleId.isBlank() ? "default" : styleId;
    }
}
