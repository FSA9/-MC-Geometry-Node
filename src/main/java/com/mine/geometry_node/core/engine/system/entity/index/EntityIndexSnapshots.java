package com.mine.geometry_node.core.engine.system.entity.index;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import com.mine.geometry_node.core.engine.graph.storage.ServerGraphRepository;
import com.mine.geometry_node.core.engine.metadata.state.OwnerMetadata;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceCapture;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearances;
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
        String previewStatus = previewFailed ? "appearance_unavailable" : appearance.isEmpty() ? "unsupported_entity" : "";
        WorldObjectEntry entry = row(entity, false, attachment, metadata.tags(), previewStatus);
        return new EntityIndexRecord(entity.getUUID(), revision, System.currentTimeMillis(), entity.isAlive(), entry, appearance, metadata,
                EntityFactions.read(entity).memberships().keySet(), EntityAppearances.get(entity));
    }
    static WorldObjectEntry row(Entity entity, boolean loaded) {
        var attachment = attachment(entity);
        return row(entity, loaded, attachment, attachment == null ? List.of() : attachment.customTags(), "");
    }
    private static WorldObjectEntry row(Entity entity, boolean loaded, EntityGraphAttachment attachment,
                                       List<String> tags, String previewStatus) {
        Map<String, String> references = new LinkedHashMap<>(); references.put("entityUuid", entity.getUUID().toString());
        if (!previewStatus.isEmpty()) references.put("previewStatus", previewStatus);
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
                entity.position(), entity.getBoundingBox(), tags, "",
                loaded ? "loaded" : "unloaded", loaded && (entity instanceof Interaction || SceneNativeEntities.eligible(entity))
                    ? entity.getUUID() : null, loaded ? entity.getId() : null, references, graphs);
    }
    private static EntityGraphAttachment attachment(Entity entity) {
        return entity.hasData(GeometryNode.GRAPH_DATA_ATTACHMENT) ? entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT) : null;
    }
}
