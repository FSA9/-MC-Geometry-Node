package com.mine.geometry_node.core.engine.system.entityindex;

import com.mine.geometry_node.core.engine.metadata.OwnerMetadata;
import com.mine.geometry_node.core.engine.system.entitypreview.appearance.EntityAppearanceSanitizer;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import com.mine.geometry_node.core.engine.system.faction.model.FactionRules;

/** Last observation of an explicitly registered instance, never a respawn or entity-save payload. */
public record EntityIndexRecord(UUID uuid, long revision, long observedAtMillis, boolean alive,
                                WorldObjectEntry entry, EntityTemplateValue appearance, OwnerMetadata metadata,
                                Set<UUID> factions) {
    public EntityIndexRecord {
        Objects.requireNonNull(uuid); Objects.requireNonNull(entry);
        Objects.requireNonNull(appearance); Objects.requireNonNull(metadata);
        if (factions.size() > FactionRules.MAX_ENTITY_REFERENCES)
            throw new IllegalArgumentException("Too many entity faction memberships");
        factions = Set.copyOf(factions);
        if (revision <= 0 || observedAtMillis < 0 || !entry.provider().equals("native")
                || entry.type().equals("minecraft:interaction") || entry.entityId() != null || entry.sceneObjectId() != null
                || !entry.lifetime().equals("unloaded") || entry.entityState() != null
                || !java.util.Set.of("entityUuid", "itemId", "previewStatus").containsAll(entry.references().keySet())
                || !entry.key().equals(WorldObjectEntry.key("native", "", uuid.toString()))
                || !uuid.toString().equals(entry.references().get("entityUuid"))
                || !entry.tags().equals(metadata.tags()))
            throw new IllegalArgumentException("Invalid registered entity snapshot");
        if (entry.estimatedBytes() > com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectPage.MAX_ENTRY_BYTES)
            throw new IllegalArgumentException("Entity snapshot metadata byte budget exceeded");
        if (!appearance.isEmpty()) {
            if (!appearance.entityTypeId().equals(entry.type())) throw new IllegalArgumentException("Mismatched entity appearance");
            appearance = new EntityTemplateValue(entry.type(), EntityAppearanceSanitizer.sanitize(appearance.data()));
        }
    }
    public EntityIndexRecord(UUID uuid, long revision, long observedAtMillis, boolean alive,
                             WorldObjectEntry entry, EntityTemplateValue appearance, OwnerMetadata metadata) {
        this(uuid, revision, observedAtMillis, alive, entry, appearance, metadata, Set.of());
    }
    public EntityIndexEntry asEntry() {
        return new EntityIndexEntry(entry, false, true, alive, observedAtMillis, revision, factions);
    }
}
