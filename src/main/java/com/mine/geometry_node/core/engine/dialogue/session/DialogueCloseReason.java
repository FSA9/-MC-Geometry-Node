package com.mine.geometry_node.core.engine.dialogue.session;

/**
 * Stable close reason ids for dialogue sessions.
 */
public final class DialogueCloseReason {
    public static final String REPLACED = "replaced";
    public static final String CLIENT = "client";
    public static final String CHOSEN = "chosen";
    public static final String NODE = "node";
    public static final String PLAYER_LOGOUT = "player_logout";
    public static final String PLAYER_DEAD = "player_dead";
    public static final String ACTOR_DEAD = "actor_dead";
    public static final String ACTOR_REMOVED = "actor_removed";
    public static final String DIMENSION_CHANGED = "dimension_changed";
    public static final String FORCED = "forced";
    public static final String SERVER_SHUTDOWN = "server_shutdown";
    public static final String CLOSED = "closed";

    private DialogueCloseReason() {
    }
}
