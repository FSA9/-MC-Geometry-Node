package com.mine.geometry_node.core.engine.system.worldquery.model;

import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import com.mine.geometry_node.core.engine.system.faction.model.FactionRules;

/** Server-observed entity state. Revision identifies a registration, not an appearance refresh. */
public record WorldObjectEntityState(UUID entityUuid, boolean loaded, boolean registered, boolean alive,
                                     long capturedAtMillis, long revision, Set<UUID> factions) {
    public WorldObjectEntityState {
        Objects.requireNonNull(entityUuid);
        if (factions.size() > FactionRules.MAX_ENTITY_REFERENCES)
            throw new IllegalArgumentException("Too many entity faction memberships");
        factions = Set.copyOf(factions);
        if (capturedAtMillis < 0 || revision < 0 || registered != (revision > 0))
            throw new IllegalArgumentException("Invalid entity registration state");
    }
    public WorldObjectEntityState(UUID entityUuid, boolean loaded, boolean registered, boolean alive,
                                  long capturedAtMillis, long revision) {
        this(entityUuid, loaded, registered, alive, capturedAtMillis, revision, Set.of());
    }
}
