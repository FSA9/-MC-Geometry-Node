package com.mine.geometry_node.core.engine.dialogue.session;

/**
 * Lifecycle policy copied from BeginDialogue into each runtime session.
 */
public record DialogueSessionPolicy(
        double maxDistance,
        boolean allowMultiPlayer,
        int timeoutSeconds,
        String busyTextKey
) {
    public static final DialogueSessionPolicy DEFAULT = new DialogueSessionPolicy(0.0, true, 0, "");

    public DialogueSessionPolicy {
        maxDistance = Math.max(0.0, maxDistance);
        timeoutSeconds = Math.max(0, timeoutSeconds);
        busyTextKey = busyTextKey == null ? "" : busyTextKey;
    }

    public boolean hasDistanceLimit() {
        return maxDistance > 0.0;
    }

    public boolean hasTimeout() {
        return timeoutSeconds > 0;
    }
}
