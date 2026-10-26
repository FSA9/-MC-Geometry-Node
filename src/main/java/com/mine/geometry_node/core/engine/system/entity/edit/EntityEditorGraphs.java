package com.mine.geometry_node.core.engine.system.entity.edit;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.attachment.GraphBindingService;
import com.mine.geometry_node.core.engine.behavior.BehaviorTreeRuntime;
import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.graph.storage.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.*;
import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataDataValues;
import com.mine.geometry_node.core.metadata.TagPolicy;
import com.mine.geometry_node.core.metadata.TagSet;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.HolderLookup;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.*;

/** Entity binding validation and bounded presentation from the published server repository. */
final class EntityEditorGraphs {
    private static final int MAX_BINDINGS = 256, MAX_AVAILABLE = 256;
    private static final int MAX_PICKER_BYTES = 48 * 1024;
    private static final Comparator<GraphBindingKey> ORDER = Comparator.comparing((GraphBindingKey key) -> key.kind().name()).thenComparing(GraphBindingKey::graphId);
    private EntityEditorGraphs() {}
    static Set<GraphBindingKey> bindings(Entity entity) { return Set.copyOf(entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT).getBindings()); }
    static List<Map<String, Object>> values(Set<GraphBindingKey> bindings) {
        return bindings.stream().sorted(ORDER).map(key -> Map.<String, Object>of("id", key.graphId(), "kind", key.kind().name())).toList();
    }
    static List<Map<String, Object>> available(Entity entity, EntityEditorAdapter adapter) {
        MinecraftServer server = entity.level().getServer();
        return available(bindings(entity), ServerGraphRepository.INSTANCE.getGraphIds(server),
                id -> ServerGraphRepository.INSTANCE.getGraph(server, id),
                graph -> supported(entity, adapter, graph.runtimeKind())
                        && (graph.runtimeKind() != GraphKind.BLUEPRINT
                        || BlueprintRuntime.INSTANCE.getGraphIndex(server, graph.graphId()) != null),
                server.registryAccess());
    }

    /** Bound presentations take priority over addable choices; binding snapshots stay strictly id/kind. */
    static List<Map<String, Object>> available(Set<GraphBindingKey> bound, Set<String> graphIds,
                                             Function<String, GraphAssetDescriptor> lookup,
                                             Predicate<GraphAssetDescriptor> addable,
                                             HolderLookup.Provider registries) {
        Set<String> ordered = new LinkedHashSet<>();
        bound.stream().sorted(ORDER).map(GraphBindingKey::graphId).forEach(ordered::add);
        ordered.addAll(new TreeSet<>(graphIds));
        List<Map<String, Object>> result = new ArrayList<>();
        int envelopeBytes = GraphMetadataDataValues.encode(Map.of("availableGraphs", List.of()), registries, MAX_PICKER_BYTES).length;
        int pickerBytes = envelopeBytes;
        for (String id : ordered) {
            if (result.size() == MAX_AVAILABLE) break;
            if (id.length() > 1024) continue;
            GraphAssetDescriptor graph = lookup.apply(id);
            if (graph == null) continue;
            GraphBindingKey key = new GraphBindingKey(graph.runtimeKind(), id);
            boolean bindable = addable.test(graph);
            if (!bound.contains(key) && !bindable) continue;
            Map<String, Object> entry = Map.of("id", id, "kind", graph.runtimeKind().name(),
                    "name", graph.name(), "graphType", graph.type().id(), "tags", graph.tags());
            try {
                // Presentation tags must also fit the shared tag-reader contract. Never shorten a tag list.
                TagSet.normalize(graph.tags(), TagPolicy.EXACT);
                // The common wire encoder accounts for modified UTF, nested lists/maps and type labels.
                // Each list element adds the same bytes whether encoded alone or in the complete list.
                int entryBytes = GraphMetadataDataValues.encode(Map.of("availableGraphs", List.of(entry)),
                        registries, MAX_PICKER_BYTES).length - envelopeBytes;
                if (entryBytes > MAX_PICKER_BYTES - pickerBytes) continue;
                pickerBytes += entryBytes;
                result.add(entry);
            } catch (IllegalArgumentException | EncoderException tooLarge) {
                // Omit the entire unrepresentable presentation; later smaller entries may still fit.
            }
        }
        return List.copyOf(result);
    }
    static Set<GraphBindingKey> validate(Entity entity, EntityEditorAdapter adapter, Object value) {
        Set<GraphBindingKey> requested = parse(value), current = bindings(entity);
        MinecraftServer server = entity.level().getServer();
        for (GraphBindingKey key : requested) {
            // Keep stale references visible/removable; only new bindings require an available compiled asset.
            if (current.contains(key)) continue;
            if (!supported(entity, adapter, key.kind()) || ServerGraphRepository.INSTANCE.getArtifact(server, key.graphId(), key.kind()) == null
                    || key.kind() == GraphKind.BLUEPRINT && BlueprintRuntime.INSTANCE.getGraphIndex(server, key.graphId()) == null)
                throw new IllegalArgumentException("Graph unavailable or unsupported");
        }
        return requested;
    }
    static Set<GraphBindingKey> parse(Object value) {
        if (!(value instanceof List<?> list) || list.size() > MAX_BINDINGS) throw new IllegalArgumentException("Invalid graph list");
        Set<GraphBindingKey> result = new LinkedHashSet<>();
        for (Object item : list) {
            Map<String, Object> map = EntityEditorChanges.dictionary(item);
            if (!map.keySet().equals(Set.of("id", "kind")) || !(map.get("id") instanceof String id) || id.length() > 1024
                    || !(map.get("kind") instanceof String kind)) throw new IllegalArgumentException("Invalid graph binding");
            GraphBindingKey key = new GraphBindingKey(GraphKind.valueOf(kind), id);
            if (!key.graphId().equals(id) || !result.add(key)) throw new IllegalArgumentException("Duplicate/noncanonical graph binding");
        }
        return Set.copyOf(result);
    }
    private static boolean supported(Entity entity, EntityEditorAdapter adapter, GraphKind kind) {
        return kind == GraphKind.BLUEPRINT || kind == GraphKind.BEHAVIOR_TREE && entity instanceof Mob && adapter.supportsBehaviorTrees(entity);
    }
    static void replace(Entity entity, Set<GraphBindingKey> desired) {
        Set<GraphBindingKey> current = bindings(entity);
        for (GraphBindingKey key : current.stream().sorted(ORDER).toList()) {
            if (desired.contains(key)) continue;
            if (key.kind() == GraphKind.BLUEPRINT) BlueprintRuntime.INSTANCE.unbindGraph(entity, key.graphId());
            else if (entity instanceof Mob mob) BehaviorTreeRuntime.INSTANCE.unbind(mob, key.graphId());
            else GraphBindingService.INSTANCE.unbind(entity, key); // Historical invalid references can still be removed.
        }
        for (GraphBindingKey key : desired.stream().sorted(ORDER).toList()) {
            if (current.contains(key)) continue;
            if (key.kind() == GraphKind.BLUEPRINT) BlueprintRuntime.INSTANCE.bindGraph(entity, key.graphId());
            else BehaviorTreeRuntime.INSTANCE.bind((Mob) entity, key.graphId());
        }
        if (!bindings(entity).equals(desired)) throw new IllegalStateException("Graph binding mutation rejected");
    }
}
