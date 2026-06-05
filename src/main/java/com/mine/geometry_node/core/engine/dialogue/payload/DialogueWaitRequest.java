package com.mine.geometry_node.core.engine.dialogue.payload;

import com.mine.geometry_node.core.engine.dialogue.context.DialogueContext;
import com.mine.geometry_node.core.engine.graph.runtime.ExternalWaitRequest;

import java.util.Objects;

/**
 * Request emitted by a blueprint node when execution waits for a dialogue choice.
 */
public record DialogueWaitRequest(
        DialogueContext context,
        DialoguePagePayload page
) implements ExternalWaitRequest {
    public DialogueWaitRequest {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(page, "page");
    }
}
