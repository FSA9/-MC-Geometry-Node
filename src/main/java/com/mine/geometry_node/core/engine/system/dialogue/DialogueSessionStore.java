package com.mine.geometry_node.core.engine.system.dialogue;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal registry. DialogueRuntime is the sole lifecycle command owner.
 */
final class DialogueSessionStore {
    private final Map<UUID, DialogueSession> sessions = new LinkedHashMap<>();

    DialogueSession create(UUID playerId, String graphId) {
        if (findForPlayer(playerId) != null) {
            throw new IllegalStateException("Player already has an active dialogue document: " + playerId);
        }
        DialogueSession session = new DialogueSession(UUID.randomUUID(), playerId, graphId);
        sessions.put(session.getSessionId(), session);
        return session;
    }

    @Nullable
    DialogueSession find(UUID sessionId) {
        return sessions.get(sessionId);
    }

    Collection<DialogueSession> view() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    List<DialogueSession> snapshot() {
        return new ArrayList<>(sessions.values());
    }

    @Nullable
    DialogueSession findForPlayer(UUID playerId) {
        for (DialogueSession session : sessions.values()) {
            if (session.isActive() && session.getPlayerId().equals(playerId)) {
                return session;
            }
        }
        return null;
    }

    @Nullable
    DialogueSession remove(UUID sessionId) {
        return sessions.remove(sessionId);
    }

    @Nullable
    DialogueSession findEntityOccupant(UUID entityId,
                                       @Nullable UUID exceptPlayerId,
                                       boolean includeSharedSessions) {
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
            if (entityId.equals(session.getDialogueContext().dialogueEntityId())) {
                return session;
            }
        }
        return null;
    }
}
