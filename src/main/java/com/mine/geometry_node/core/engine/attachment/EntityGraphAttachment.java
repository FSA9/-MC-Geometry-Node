package com.mine.geometry_node.core.engine.attachment;

import com.mine.geometry_node.core.engine.blueprint.attachment.BlueprintEntityProcessHost;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintProcess;
import com.mine.geometry_node.core.engine.graph.scoped.storage.OwnerScopedStateStore;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintCloseMode;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingSet;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetId;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.Entity;

import java.util.*;
import java.util.function.UnaryOperator;
import com.mine.geometry_node.core.metadata.TagPolicy;
import com.mine.geometry_node.core.metadata.TagSet;

/**
 * [实体附加层 - 组合版]
 * 作为实体的背包，持有绑定关系，并委托 BlueprintProcessContainer 执行具体逻辑。
 */
public class EntityGraphAttachment {

    private final GraphBindingSet boundGraphs = new GraphBindingSet();
    private final OwnerScopedStateStore ownerScopedState =
            new OwnerScopedStateStore();
    private List<String> customTags = List.of();
    private long customTagRevision;
    private boolean updatingCustomTags;
    // Behavior-tree-only state: blueprints do not select one active asset per owner.
    private String selectedBehaviorTree;
    private final BlueprintEntityProcessHost blueprints = new BlueprintEntityProcessHost();

    public EntityGraphAttachment() {}

    /**
     * [心跳驱动] 委托给底座
     */
    public void tick(Entity entity) {
        blueprints.tick(entity);
    }

    public void attachOwner(Entity entity) {
        blueprints.attachOwner(entity);
    }

    // --- 实体独占绑定逻辑 ---

    boolean addBinding(GraphBindingKey binding) {
        return boundGraphs.add(binding);
    }

    boolean removeBinding(GraphBindingKey binding) {
        boolean removed = boundGraphs.remove(binding);
        if (removed && binding.kind() == GraphKind.BEHAVIOR_TREE
                && Objects.equals(selectedBehaviorTree, binding.graphId())) {
            selectedBehaviorTree = null;
        }
        return removed;
    }

    boolean clearBindings(GraphKind kind) {
        boolean removed = boundGraphs.clear(kind);
        if (kind == GraphKind.BEHAVIOR_TREE) selectedBehaviorTree = null;
        return removed;
    }

    public Set<String> getBoundGraphs() {
        return boundGraphs.graphIds(GraphKind.BLUEPRINT);
    }

    public Set<GraphBindingKey> getBindings() {
        return boundGraphs.all();
    }

    // Behavior-tree-only binding and active-tree selection API.
    public Set<String> getBoundBehaviorTrees() {
        return boundGraphs.graphIds(GraphKind.BEHAVIOR_TREE);
    }

    public String getSelectedBehaviorTree() {
        return selectedBehaviorTree;
    }

    public void selectBehaviorTree(String graphId) {
        String normalized = GraphAssetId.require(graphId);
        if (!boundGraphs.contains(GraphBindingKey.behaviorTree(normalized))) {
            throw new IllegalArgumentException("Selected behavior tree must be bound: " + normalized);
        }
        selectedBehaviorTree = normalized;
    }

    public void clearSelectedBehaviorTree() {
        selectedBehaviorTree = null;
    }

    public void clearBlueprintProcesses() {
        this.blueprints.clear();
    }

    // --- API 委托层 ---

    public void addProcess(BlueprintProcess process) {
        blueprints.addProcess(process);
    }
    public void removeProcess(String graphId) { blueprints.removeProcess(graphId); }
    public void removeProcess(String graphId, BlueprintCloseMode closeMode) { blueprints.removeProcess(graphId, closeMode); }
    public Collection<BlueprintProcess> getProcesses() { return blueprints.processes(); }
    public long getNextScheduledTick() { return blueprints.nextScheduledTick(); }
    public BlueprintProcess getProcess(String graphId) { return blueprints.getProcess(graphId); }
    public OwnerScopedStateStore ownerScopedState() { return ownerScopedState; }

    public List<String> customTags() { return customTags; }
    public long customTagRevision() { return customTagRevision; }
    public boolean updateCustomTags(UnaryOperator<List<String>> transformation) {
        if (updatingCustomTags) throw new IllegalStateException("Reentrant entity custom tag mutation");
        updatingCustomTags = true;
        try {
            List<String> candidate = TagSet.normalize(
                    Objects.requireNonNull(transformation.apply(customTags), "custom tags"), TagPolicy.EXACT);
            if (new LinkedHashSet<>(customTags).equals(new LinkedHashSet<>(candidate))) return false;
            customTags = candidate;
            customTagRevision++;
            return true;
        } finally { updatingCustomTags = false; }
    }

    public void checkpointBlueprintExternalWaits(String reason) {
        blueprints.checkpointExternalWaits(reason);
    }

    // --- 序列化层 ---

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (!customTags.isEmpty()) {
            ListTag tags = new ListTag();
            customTags.forEach(value -> tags.add(StringTag.valueOf(value)));
            tag.put("CustomTags", tags);
        }
        if (!getBoundGraphs().isEmpty()) {
            ListTag boundList = new ListTag();
            for (String graphId : getBoundGraphs()) boundList.add(StringTag.valueOf(graphId));
            tag.put("BoundGraphs", boundList);
        }
        // Behavior-tree-only persistence for bound candidates and the selected active tree.
        if (!getBoundBehaviorTrees().isEmpty()) {
            ListTag behaviorList = new ListTag();
            for (String graphId : getBoundBehaviorTrees()) {
                behaviorList.add(StringTag.valueOf(graphId));
            }
            tag.put("BehaviorTrees", behaviorList);
        }
        if (selectedBehaviorTree != null && getBoundBehaviorTrees().contains(selectedBehaviorTree)) {
            tag.putString("SelectedBehaviorTree", selectedBehaviorTree);
        }
        if (!ownerScopedState.isEmpty()) {
            tag.put("OwnerScopedState",
                    ownerScopedState.save(new CompoundTag(), provider));
        }
        return blueprints.save(tag, provider);
    }

    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        List<String> loadedTags = new ArrayList<>();
        for (var raw : tag.getListOrEmpty("CustomTags")) {
            raw.asString().ifPresent(loadedTags::add);
        }
        updateCustomTags(ignored -> loadedTags);
        this.boundGraphs.clear();
        ListTag list = tag.getListOrEmpty("BoundGraphs");
        for (int i = 0; i < list.size(); i++) {
            String graphId = list.getStringOr(i, "");
            if (!graphId.isEmpty()) {
                this.boundGraphs.add(GraphBindingKey.blueprint(graphId));
            }
        }
        // Behavior-tree-only persistence counterpart to the save block above.
        ListTag behaviorList = tag.getListOrEmpty("BehaviorTrees");
        for (int i = 0; i < behaviorList.size(); i++) {
            String graphId = behaviorList.getStringOr(i, "");
            if (!graphId.isEmpty()) {
                this.boundGraphs.add(GraphBindingKey.behaviorTree(graphId));
            }
        }
        String selected = GraphAssetId.canonicalize(tag.getStringOr("SelectedBehaviorTree", ""));
        selectedBehaviorTree = getBoundBehaviorTrees().contains(selected) ? selected : null;
        ownerScopedState.load(tag.getCompoundOrEmpty("OwnerScopedState"), provider);
        blueprints.load(tag, provider);
    }
}
