package com.mine.geometry_node.core.engine.behavior;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.behavior.compile.BehaviorTreeCompiler;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorRuntimeBudget;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.plan.BehaviorTreePlan;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorEventHandler;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutorRegistry;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorRuntimeService;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorTreeInstance;
import com.mine.geometry_node.core.engine.behavior.runtime.debug.BehaviorDebugAccess;
import com.mine.geometry_node.core.engine.behavior.runtime.debug.BehaviorDebugSnapshot;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompilationService;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntime;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetLifecycleIndex;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.List;
import java.util.Set;

/** Behavior-tree family facade and server-authoritative runtime entry point. */
public final class BehaviorTreeRuntime implements GraphRuntime {
    public static final BehaviorTreeRuntime INSTANCE = new BehaviorTreeRuntime();

    private final BehaviorRuntimeService service = new BehaviorRuntimeService(
            BehaviorNodeExecutorRegistry.INSTANCE, BehaviorRuntimeBudget.DEFAULT);

    private BehaviorTreeRuntime() {
    }

    @Override
    public GraphKind kind() {
        return GraphKind.BEHAVIOR_TREE;
    }

    @Override
    public String id() {
        return "geometry_node:behavior_tree";
    }

    @Override
    public void init() {
        BehaviorNodeExecutorRegistry.INSTANCE.registerCoreExecutors();
        GraphCompilationService.INSTANCE.register(BehaviorTreeCompiler.INSTANCE);
        GraphAssetLifecycleIndex.INSTANCE.addChangeListener(
                GraphKind.BEHAVIOR_TREE, this::onGraphAssetsChanged);
        BehaviorEventHandler.init();
    }

    public BehaviorTreeInstance start(ServerLevel level, Mob owner, String graphId) {
        return service.start(level, owner, graphId);
    }

    public boolean bind(Mob owner, String graphId) {
        requireServerOwner(owner);
        String normalized = requireAvailable(graphId);
        return owner.getData(GeometryNode.GRAPH_DATA_ATTACHMENT).bindBehaviorTree(normalized);
    }

    public BehaviorTreeInstance startBound(Mob owner) {
        ServerLevel level = requireServerOwner(owner);
        if (service.getForOwner(owner) != null) {
            throw new IllegalStateException("Owner already has a running behavior tree");
        }
        String graphId = selectedGraph(owner);
        if (graphId == null) throw new IllegalStateException("Owner has no selected behavior tree");
        return service.start(level, owner, graphId);
    }

    public BehaviorTreeInstance switchTo(Mob owner, String graphId) {
        ServerLevel level = requireServerOwner(owner);
        String normalized = requireAvailable(graphId);
        if (!boundGraphs(owner).contains(normalized)) {
            throw new IllegalStateException("Behavior tree is not bound: " + normalized);
        }
        BehaviorTreeInstance current = service.getForOwner(owner);
        if (current != null) {
            service.stop(level.getServer(), current.instanceId(), BehaviorTerminationReason.TREE_STOPPED);
        }
        owner.getData(GeometryNode.GRAPH_DATA_ATTACHMENT).selectBehaviorTree(normalized);
        return service.start(level, owner, normalized);
    }

    public boolean unbind(Mob owner, String graphId) {
        ServerLevel level = requireServerOwner(owner);
        String normalized = graphId != null ? graphId.trim() : "";
        if (normalized.isEmpty()) throw new IllegalArgumentException("Behavior graph id cannot be empty");
        if (!boundGraphs(owner).contains(normalized)) return false;
        BehaviorTreeInstance current = service.getForOwner(owner);
        if (current != null && current.graphId().equals(normalized)) {
            service.stop(level.getServer(), current.instanceId(), BehaviorTerminationReason.UNBOUND);
        }
        return owner.getData(GeometryNode.GRAPH_DATA_ATTACHMENT).unbindBehaviorTree(normalized);
    }

    public boolean unbindAll(Mob owner) {
        ServerLevel level = requireServerOwner(owner);
        BehaviorTreeInstance current = service.getForOwner(owner);
        if (current != null) {
            service.stop(level.getServer(), current.instanceId(), BehaviorTerminationReason.UNBOUND);
        }
        return owner.getData(GeometryNode.GRAPH_DATA_ATTACHMENT).clearBehaviorTrees();
    }

    public Set<String> boundGraphs(Entity owner) {
        if (owner == null) return Set.of();
        return owner.getData(GeometryNode.GRAPH_DATA_ATTACHMENT).getBoundBehaviorTrees();
    }

    @Nullable
    public String selectedGraph(Entity owner) {
        if (owner == null) return null;
        return owner.getData(GeometryNode.GRAPH_DATA_ATTACHMENT).getSelectedBehaviorTree();
    }

    /** Compatibility name for callers that previously treated the sole binding as the selection. */
    @Nullable
    public String boundGraph(Entity owner) {
        return selectedGraph(owner);
    }

    public BehaviorTreeInstance start(ServerLevel level, Mob owner, BehaviorTreePlan plan) {
        return service.start(level, owner, plan);
    }

    @Nullable
    public BehaviorTreeInstance getForOwner(Entity owner) {
        return service.getForOwner(owner);
    }

    @Nullable
    public BehaviorTreeInstance get(MinecraftServer server, UUID instanceId) {
        return service.get(server, instanceId);
    }

    @Nullable
    public BehaviorDebugSnapshot debugSnapshot(MinecraftServer server, UUID instanceId) {
        return service.debugSnapshot(server, instanceId);
    }

    @Nullable
    public BehaviorDebugAccess debugAccess(MinecraftServer server, UUID instanceId) {
        return service.debugAccess(server, instanceId);
    }

    @Nullable
    public BehaviorDebugSnapshot debugSnapshotForOwner(MinecraftServer server, UUID ownerId) {
        return service.debugSnapshotForOwner(server, ownerId);
    }

    public List<BehaviorDebugSnapshot> debugSnapshotsForAsset(MinecraftServer server, String assetId) {
        return service.debugSnapshotsForAsset(server, assetId);
    }

    @Nullable
    public BehaviorTerminationReason lastStopReasonForOwner(MinecraftServer server, UUID ownerId) {
        return service.lastStopReasonForOwner(server, ownerId);
    }

    public boolean suspend(MinecraftServer server, UUID instanceId) {
        return service.suspend(server, instanceId);
    }

    public boolean resume(MinecraftServer server, UUID instanceId) {
        return service.resume(server, instanceId);
    }

    public boolean wake(MinecraftServer server, UUID instanceId) {
        return service.wake(server, instanceId);
    }

    public boolean wake(Entity owner) {
        BehaviorTreeInstance instance = service.getForOwner(owner);
        return instance != null && owner.level() instanceof ServerLevel level
                && service.wake(level.getServer(), instance.instanceId());
    }

    public boolean stop(MinecraftServer server, UUID instanceId, BehaviorTerminationReason reason) {
        return service.stop(server, instanceId, reason);
    }

    public void tickLevel(ServerLevel level) {
        service.tickLevel(level);
    }

    public void ownerUnavailable(Entity owner, BehaviorTerminationReason reason) {
        service.ownerUnavailable(owner, reason);
    }

    public void shutdown(MinecraftServer server) {
        service.shutdown(server);
    }

    public int activeCount(MinecraftServer server) {
        return service.activeCount(server);
    }

    private static ServerLevel requireServerOwner(Mob owner) {
        if (owner == null || !(owner.level() instanceof ServerLevel level)
                || owner.isRemoved() || !owner.isAlive()) {
            throw new IllegalArgumentException("Behavior owner must be a live server Mob");
        }
        return level;
    }

    private static String requireAvailable(String graphId) {
        String normalized = graphId != null ? graphId.trim() : "";
        if (normalized.isEmpty()) throw new IllegalArgumentException("Behavior graph id cannot be empty");
        if (GraphAssetLifecycleIndex.INSTANCE.getArtifact(
                normalized, GraphKind.BEHAVIOR_TREE) == null) {
            throw new IllegalArgumentException("Behavior tree is unavailable: " + normalized);
        }
        return normalized;
    }

    private void onGraphAssetsChanged(GraphAssetLifecycleIndex.Change change) {
        service.graphAssetsChanged(change.server(), change.affectedAssetIds());
    }
}
