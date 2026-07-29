package com.mine.geometry_node.core.engine.dialogue.session;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    public List<DialogueSession> snapshotSessions() {
        return new ArrayList<>(sessions.values());
    }

    @Nullable
    public DialogueSession getSessionForPlayer(UUID playerId) {
        for (DialogueSession session : sessions.values()) {
            if (session.isActive() && session.getPlayerId().equals(playerId)) {
                return session;
            }
        }
        return null;
    }

    @Nullable
    public DialogueSession removeSession(UUID sessionId) {
        return sessions.remove(sessionId);
    }

    @Nullable
    public DialogueSession closeSession(UUID sessionId) {
        return closeSession(sessionId, DialogueCloseReason.CLOSED);
    }

    @Nullable
    public DialogueSession closeSession(UUID sessionId, String reason) {
        DialogueSession session = sessions.remove(sessionId);
        if (session != null) {
            session.close(reason);
        }
        return session;
    }

    public void closeSessionsForPlayer(UUID playerId) {
        closeSessionsForPlayer(playerId, DialogueCloseReason.CLOSED);
    }

    public void closeSessionsForPlayer(UUID playerId, String reason) {
        List<DialogueSession> closing = new ArrayList<>();
        for (DialogueSession session : sessions.values()) {
            if (session.getPlayerId().equals(playerId)) {
                closing.add(session);
            }
        }
        for (DialogueSession session : closing) {
            sessions.remove(session.getSessionId());
            session.close(reason);
        }
    }

    @Nullable
    public DialogueSession findEntityOccupant(UUID entityId, @Nullable UUID exceptPlayerId) {
        return findEntityOccupant(entityId, exceptPlayerId, false);
    }

    @Nullable
    public DialogueSession findEntityOccupant(UUID entityId, @Nullable UUID exceptPlayerId, boolean includeSharedSessions) {
        if (entityId == null) {
            return null;
        }
        for (DialogueSession session : sessions.values()) {
            if (!session.isActive()) {
                continue;
            }
            if (exceptPlayerId != null && session.getPlayerId().equals(exceptPlayerId)) {
                continue;
            }
            if (session.getDialogueContext() == null) {
                continue;
            }
            if (!includeSharedSessions && session.getPolicy().allowMultiPlayer()) {
                continue;
            }
            UUID dialogueEntityId = session.getDialogueContext().dialogueEntityId();
            if (entityId.equals(dialogueEntityId)) {
                return session;
            }
        }
        return null;
    }

    public void closeAll(String reason) {
        List<DialogueSession> closing = new ArrayList<>(sessions.values());
        sessions.clear();
        for (DialogueSession session : closing) {
            session.close(reason);
        }
    }
}
