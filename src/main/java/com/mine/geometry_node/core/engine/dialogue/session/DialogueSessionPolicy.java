package com.mine.geometry_node.core.engine.dialogue.session;

/**
 * Lifecycle policy copied from BeginDialogue into each runtime session.
 */
public record DialogueSessionPolicy(
        boolean allowMultiPlayer,
        int timeoutSeconds
) {
    public static final DialogueSessionPolicy DEFAULT = new DialogueSessionPolicy(true, 0);

    public DialogueSessionPolicy {
        timeoutSeconds = Math.max(0, timeoutSeconds);
    }

    public boolean hasTimeout() {
        return timeoutSeconds > 0;
    }
}
