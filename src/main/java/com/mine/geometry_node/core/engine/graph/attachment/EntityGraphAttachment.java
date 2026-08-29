package com.mine.geometry_node.core.engine.graph.attachment;

import com.mine.geometry_node.core.engine.blueprint.attachment.BlueprintEntityProcessHost;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintProcess;
import com.mine.geometry_node.core.engine.graph.scoped.OwnerScopedStateStore;
import com.mine.geometry_node.core.engine.graph.runtime.GraphCloseMode;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.Entity;

import java.util.*;

/**
 * [实体附加层 - 组合版]
 * 作为实体的背包，持有绑定关系，并委托 GraphContainer 执行具体逻辑。
 */
public class EntityGraphAttachment {

    private final GraphBindingSet boundGraphs = new GraphBindingSet();
    private final OwnerScopedStateStore ownerScopedState =
            new OwnerScopedStateStore();
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

    public void bindGraph(String graphId) {
        bind(GraphBindingKey.blueprint(graphId));
    }

    public void bind(GraphBindingKey binding) {
        this.boundGraphs.add(binding);
    }

    public void unbindGraph(String graphId) {
        unbindGraph(graphId, GraphCloseMode.IMMEDIATE);
    }

    public void unbindGraph(String graphId, GraphCloseMode closeMode) {
        this.boundGraphs.remove(GraphBindingKey.blueprint(graphId));
        this.blueprints.removeProcess(graphId, closeMode);
    }

    public Set<String> getBoundGraphs() {
        return boundGraphs.graphIds(GraphKind.BLUEPRINT);
    }

    public Set<GraphBindingKey> getBindings() {
        return boundGraphs.all();
    }

    // Behavior-tree-only binding and active-tree selection API.
    public boolean bindBehaviorTree(String graphId) {
        return boundGraphs.add(GraphBindingKey.behaviorTree(graphId));
    }

    public boolean unbindBehaviorTree(String graphId) {
        boolean removed = boundGraphs.remove(GraphBindingKey.behaviorTree(graphId));
        if (removed && Objects.equals(selectedBehaviorTree, graphId)) selectedBehaviorTree = null;
        return removed;
    }

    public boolean clearBehaviorTrees() {
        selectedBehaviorTree = null;
        return boundGraphs.clear(GraphKind.BEHAVIOR_TREE);
    }

    public Set<String> getBoundBehaviorTrees() {
        return boundGraphs.graphIds(GraphKind.BEHAVIOR_TREE);
    }

    public String getSelectedBehaviorTree() {
        return selectedBehaviorTree;
    }

    public void selectBehaviorTree(String graphId) {
        String normalized = graphId != null ? graphId.trim() : "";
        if (!boundGraphs.contains(GraphBindingKey.behaviorTree(normalized))) {
            throw new IllegalArgumentException("Selected behavior tree must be bound: " + normalized);
        }
        selectedBehaviorTree = normalized;
    }

    public void clearSelectedBehaviorTree() {
        selectedBehaviorTree = null;
    }

    public void clearGraphs() {
        this.boundGraphs.clear(GraphKind.BLUEPRINT);
        this.blueprints.clear();
    }

    // --- API 委托层 ---

    public void addProcess(BlueprintProcess process) {
        blueprints.addProcess(process);
    }
    public void removeProcess(String graphId) { blueprints.removeProcess(graphId); }
    public void removeProcess(String graphId, GraphCloseMode closeMode) { blueprints.removeProcess(graphId, closeMode); }
    public Collection<BlueprintProcess> getProcesses() { return blueprints.processes(); }
    public long getNextScheduledTick() { return blueprints.nextScheduledTick(); }
    public BlueprintProcess getProcess(String graphId) { return blueprints.getProcess(graphId); }
    public OwnerScopedStateStore ownerScopedState() { return ownerScopedState; }

    // --- 序列化层 ---

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
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
        String selected = tag.getStringOr("SelectedBehaviorTree", "");
        selectedBehaviorTree = getBoundBehaviorTrees().contains(selected) ? selected : null;
        ownerScopedState.load(tag.getCompoundOrEmpty("OwnerScopedState"), provider);
        blueprints.load(tag, provider);
    }
}
