package com.mine.geometry_node.core.engine.dialogue;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory registry for active dialogue sessions.
 */
public class DialogueSessionManager {

    private final Map<UUID, DialogueSession> sessions = new LinkedHashMap<>();

    public DialogueSession createSession(UUID playerId, String graphId) {
        DialogueSession session = new DialogueSession(UUID.randomUUID(), playerId, graphId);
        sessions.put(session.getSessionId(), session);
        return session;
    }

    @Nullable
    public DialogueSession getSession(UUID sessionId) {
        return sessions.get(sessionId);
    }

    public Collection<DialogueSession> getSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    @Nullable
    public DialogueSession closeSession(UUID sessionId) {
        DialogueSession session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
        }
        return session;
    }

    public void closeSessionsForPlayer(UUID playerId) {
        sessions.values().removeIf(session -> {
            if (!session.getPlayerId().equals(playerId)) {
                return false;
            }
            session.close();
            return true;
        });
    }
}
