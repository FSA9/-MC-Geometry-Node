package com.mine.geometry_node.core.engine.graph.attachment;

import com.mine.geometry_node.core.engine.blueprint.attachment.GraphContainer;
import com.mine.geometry_node.core.engine.blueprint.event.BlueprintEventHandler;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * [实体附加层 - 组合版]
 * 作为实体的背包，持有绑定关系，并委托 GraphContainer 执行具体逻辑。
 */
public class EntityGraphAttachment {

    private final GraphBindingSet boundGraphs = new GraphBindingSet();
    private final OwnerScopedStateStore ownerScopedState =
            new OwnerScopedStateStore();
    private String selectedBehaviorTree;
    private WeakReference<Entity> ownerRef = new WeakReference<>(null);

    private final GraphContainer container = new GraphContainer(() -> {}, this::onScheduleChanged);

    public EntityGraphAttachment() {}

    /**
     * [心跳驱动] 委托给底座
     */
    public void tick(Entity entity) {
        attachOwner(entity);
        if (entity.level() instanceof ServerLevel serverLevel) {
            container.tick(serverLevel, entity);
        }
    }

    public void attachOwner(Entity entity) {
        this.ownerRef = new WeakReference<>(entity);
        for (BlueprintProcess process : container.getProcesses()) {
            process.setGraphOwner(entity);
        }
    }

    private void onScheduleChanged() {
        Entity owner = this.ownerRef.get();
        if (owner != null) {
            BlueprintEventHandler.markActive(owner);
        }
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
        this.container.removeProcess(graphId, closeMode);
    }

    public Set<String> getBoundGraphs() {
        return boundGraphs.graphIds(GraphKind.BLUEPRINT);
    }

    public Set<GraphBindingKey> getBindings() {
        return boundGraphs.all();
    }

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
        this.container.clear();
    }

    // --- API 委托层 ---

    public void addProcess(BlueprintProcess process) {
        Entity owner = ownerRef.get();
        process.setGraphOwner(owner);
        container.addProcess(process);
    }
    public void removeProcess(String graphId) { container.removeProcess(graphId); }
    public void removeProcess(String graphId, GraphCloseMode closeMode) { container.removeProcess(graphId, closeMode); }
    public Collection<BlueprintProcess> getProcesses() { return container.getProcesses(); }
    public long getNextScheduledTick() { return container.getNextScheduledTick(); }
    public BlueprintProcess getProcess(String graphId) {
        BlueprintProcess process = container.getProcess(graphId);
        if (process != null) {
            process.setGraphOwner(ownerRef.get());
        }
        return process;
    }
    public OwnerScopedStateStore ownerScopedState() { return ownerScopedState; }

    // --- 序列化层 ---

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (!getBoundGraphs().isEmpty()) {
            ListTag boundList = new ListTag();
            for (String graphId : getBoundGraphs()) boundList.add(StringTag.valueOf(graphId));
            tag.put("BoundGraphs", boundList);
        }
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
        return container.save(tag, provider);
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
        container.load(tag, provider);
    }
}
