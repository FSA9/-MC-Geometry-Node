package com.mine.geometry_node.core.engine.system.entity.index;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import com.mine.geometry_node.core.engine.graph.storage.ServerGraphRepository;
import com.mine.geometry_node.core.engine.metadata.OwnerMetadata;
import com.mine.geometry_node.core.engine.system.entity.preview.appearance.EntityAppearanceCapture;
import com.mine.geometry_node.core.engine.system.scene.SceneNativeEntities;
import com.mine.geometry_node.core.engine.system.faction.service.EntityFactions;
import com.mine.geometry_node.core.engine.system.worldquery.model.*;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.*;

/** Reads only allowlisted appearance and this mod's PUBLIC owner metadata, never full entity NBT. */
final class EntityIndexSnapshots {
    private EntityIndexSnapshots() {}
    static boolean ordinary(Entity entity) { return entity != null && !(entity instanceof Interaction); }
    static EntityIndexRecord capture(Entity entity, long revision, EntityIndexRecord previous) {
        EntityGraphAttachment attachment = attachment(entity);
        OwnerMetadata metadata = attachment == null ? OwnerMetadata.EMPTY : OwnerMetadata.of(attachment.customTags(),
                attachment.ownerScopedState().readValues(ScopedStateNamespace.PUBLIC, entity.registryAccess()), entity.registryAccess());
        EntityTemplateValue appearance;
        boolean previewFailed = false;
        try { appearance = EntityAppearanceCapture.template(entity); }
        catch (RuntimeException failure) {
            appearance = previous == null ? EntityTemplateValue.EMPTY : previous.appearance();
            previewFailed = true;
        }
        WorldObjectEntry entry = row(entity, false);
        Map<String, String> references = new LinkedHashMap<>(entry.references());
        if (previewFailed) references.put("previewStatus", "appearance_unavailable");
        else if (appearance.isEmpty()) references.put("previewStatus", "unsupported_entity");
        entry = new WorldObjectEntry(entry.key(), entry.provider(), entry.type(), entry.name(), entry.dimension(),
                entry.position(), entry.bounds(), metadata.tags(), entry.source(), "unloaded", null, null, references, entry.graphs());
        return new EntityIndexRecord(entity.getUUID(), revision, System.currentTimeMillis(), entity.isAlive(), entry, appearance, metadata,
                EntityFactions.read(entity).memberships().keySet());
    }
    static WorldObjectEntry row(Entity entity, boolean loaded) {
        var attachment = attachment(entity);
        Map<String, String> references = new LinkedHashMap<>(); references.put("entityUuid", entity.getUUID().toString());
        if (entity instanceof ItemEntity item && !item.getItem().isEmpty())
            references.put("itemId", BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString());
        List<WorldObjectGraph> graphs = new ArrayList<>();
        if (attachment != null) {
            if (attachment.getBindings().size() > WorldObjectEntry.MAX_GRAPHS)
                throw new IllegalArgumentException("Too many bound graphs");
            for (var binding : attachment.getBindings()) {
                var descriptor = ServerGraphRepository.INSTANCE.getGraph(entity.level().getServer(), binding.graphId());
                boolean current = descriptor != null && descriptor.runtimeKind() == binding.kind();
                graphs.add(new WorldObjectGraph(binding.graphId(), current ? descriptor.type().id() : binding.kind().id(),
                        current ? descriptor.name() : binding.graphId()));
            }
        }
        return new WorldObjectEntry(WorldObjectEntry.key("native", "", entity.getUUID().toString()), "native",
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                entity.hasCustomName() ? entity.getCustomName().getString() : "", entity.level().dimension().identifier().toString(),
                entity.position(), entity.getBoundingBox(), attachment == null ? List.of() : attachment.customTags(), "",
                loaded ? "loaded" : "unloaded", loaded && (entity instanceof Interaction || SceneNativeEntities.eligible(entity))
                    ? entity.getUUID() : null, loaded ? entity.getId() : null, references, graphs);
    }
    private static EntityGraphAttachment attachment(Entity entity) {
        return entity.hasData(GeometryNode.GRAPH_DATA_ATTACHMENT) ? entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT) : null;
    }
}
