package com.mine.geometry_node.core.engine.system.entity.index;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetDescriptor;
import com.mine.geometry_node.core.engine.graph.storage.ServerGraphRepository;
import com.mine.geometry_node.core.engine.metadata.state.OwnerMetadata;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceCapture;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearances;
import com.mine.geometry_node.core.engine.system.scene.SceneNativeEntities;
import com.mine.geometry_node.core.engine.system.faction.service.EntityFactions;
import com.mine.geometry_node.core.engine.system.worldquery.WorldObjectGraphMetadata;
import com.mine.geometry_node.core.engine.system.worldquery.model.*;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.*;
import java.util.function.Function;

/** Reads only allowlisted appearance and this mod's PUBLIC owner metadata, never full entity NBT. */
final class EntityIndexSnapshots {
    private EntityIndexSnapshots() {}
    static boolean ordinary(Entity entity) { return entity != null && !(entity instanceof Interaction); }
    static EntityIndexRecord capture(Entity entity, long revision, EntityIndexRecord previous) {
        return capture(entity, revision, previous, false);
    }
    static EntityIndexRecord capture(Entity entity, long revision, EntityIndexRecord previous,
                                     boolean preserveGraphDescriptions) {
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
        WorldObjectEntry entry = row(entity, false, attachment, metadata.tags(), previewStatus,
                preserveGraphDescriptions && previous != null ? previous.entry().graphs() : List.of());
        return new EntityIndexRecord(entity.getUUID(), revision, System.currentTimeMillis(), entity.isAlive(), entry, appearance, metadata,
                EntityFactions.read(entity).memberships().keySet(), EntityAppearances.get(entity));
    }
    static WorldObjectEntry row(Entity entity, boolean loaded) {
        var attachment = attachment(entity);
        return row(entity, loaded, attachment, attachment == null ? List.of() : attachment.customTags(), "", List.of());
    }
    private static WorldObjectEntry row(Entity entity, boolean loaded, EntityGraphAttachment attachment,
                                       List<String> tags, String previewStatus, List<WorldObjectGraph> previousGraphs) {
        Map<String, String> references = new LinkedHashMap<>(); references.put("entityUuid", entity.getUUID().toString());
        if (!previewStatus.isEmpty()) references.put("previewStatus", previewStatus);
        if (entity instanceof ItemEntity item && !item.getItem().isEmpty())
            references.put("itemId", BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString());
        List<WorldObjectGraph> graphs = attachment == null ? List.of() : graphs(attachment.getBindings(),
                id -> ServerGraphRepository.INSTANCE.getGraph(entity.level().getServer(), id), previousGraphs);
        return new WorldObjectEntry(WorldObjectEntry.key("native", "", entity.getUUID().toString()), "native",
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                entity.hasCustomName() ? entity.getCustomName().getString() : "", entity.level().dimension().identifier().toString(),
                entity.position(), entity.getBoundingBox(), tags, "",
                loaded ? "loaded" : "unloaded", loaded && (entity instanceof Interaction || SceneNativeEntities.eligible(entity))
                    ? entity.getUUID() : null, loaded ? entity.getId() : null, references, graphs);
    }
    static List<WorldObjectGraph> graphs(Collection<GraphBindingKey> bindings,
                                         Function<String, GraphAssetDescriptor> lookup, List<WorldObjectGraph> previous) {
        if (bindings.size() > WorldObjectEntry.MAX_GRAPHS)
            throw new IllegalArgumentException("Too many bound graphs");
        Map<GraphKind, Map<String, WorldObjectGraph>> previousByKind = previous.isEmpty()
                ? Map.of() : new EnumMap<>(GraphKind.class);
        for (var old : previous) {
            var type = GraphTypeRegistry.INSTANCE.get(old.type());
            if (type != null)
                previousByKind.computeIfAbsent(type.runtimeKind(), ignored -> new HashMap<>())
                        .putIfAbsent(old.id(), old);
        }
        List<WorldObjectGraph> graphs = new ArrayList<>(bindings.size());
        for (var binding : bindings) {
            var descriptor = lookup.apply(binding.graphId());
            var old = previousByKind.getOrDefault(binding.kind(), Map.of()).get(binding.graphId());
            graphs.add(WorldObjectGraphMetadata.describe(binding, descriptor, old));
        }
        return graphs;
    }
    private static EntityGraphAttachment attachment(Entity entity) {
        return entity.hasData(GeometryNode.GRAPH_DATA_ATTACHMENT) ? entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT) : null;
    }
}
