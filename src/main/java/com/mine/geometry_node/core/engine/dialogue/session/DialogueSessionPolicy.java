package com.mine.geometry_node.core.engine.dialogue.session;

/**
 * Lifecycle policy copied from BeginDialogue into each runtime session.
 */
public record DialogueSessionPolicy(
        boolean allowMultiPlayer
) {
    public static final DialogueSessionPolicy DEFAULT = new DialogueSessionPolicy(true);
}
