package com.mine.geometry_node.core.engine.dialogue.payload;

import com.mine.geometry_node.core.engine.graph.runtime.ExternalWaitRequest;
import net.minecraft.server.level.ServerPlayer;

/**
 * Request emitted by a blueprint node when execution waits for a dialogue choice.
 */
public record DialogueWaitRequest(
        ServerPlayer player,
        DialoguePagePayload page
) implements ExternalWaitRequest {
}
