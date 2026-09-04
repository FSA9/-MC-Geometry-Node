package com.mine.geometry_node.core.engine.behavior;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.behavior.compile.BehaviorTreeCompiler;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorRuntimeBudget;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorEventHandler;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutorRegistry;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorTreeEngine;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorTreeProcess;
import com.mine.geometry_node.core.engine.behavior.debug.BehaviorTreeDebugAccess;
import com.mine.geometry_node.core.engine.behavior.debug.BehaviorTreeDebugSnapshot;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompilationService;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntime;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetLifecycleIndex;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetId;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceLifecycleManager;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceScope;
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

    private final BehaviorTreeEngine engine = new BehaviorTreeEngine(
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
        GraphCompilationService.INSTANCE.register(BehaviorTreeCompiler.INSTANCE);
        GraphAssetLifecycleIndex.INSTANCE.addChangeListener(
                GraphKind.BEHAVIOR_TREE, this::onGraphAssetsChanged);
        BehaviorEventHandler.init();
    }

    @Override
    public int tickOrder() {
        return 200;
    }

    public BehaviorTreeProcess start(ServerLevel level, Mob owner, String graphId) {
        return engine.start(level, owner, graphId);
    }

    public boolean bind(Mob owner, String graphId) {
        requireServerOwner(owner);
        String normalized = requireAvailable(graphId);
        return owner.getData(GeometryNode.GRAPH_DATA_ATTACHMENT).bindBehaviorTree(normalized);
    }

    public BehaviorTreeProcess startBound(Mob owner) {
        ServerLevel level = requireServerOwner(owner);
        if (engine.getForOwner(owner) != null) {
            throw new IllegalStateException("Owner already has a running behavior tree");
        }
        String graphId = selectedGraph(owner);
        if (graphId == null) throw new IllegalStateException("Owner has no selected behavior tree");
        return engine.start(level, owner, graphId);
    }

    public BehaviorTreeProcess switchTo(Mob owner, String graphId) {
        ServerLevel level = requireServerOwner(owner);
        String normalized = requireAvailable(graphId);
        if (!boundGraphs(owner).contains(normalized)) {
            throw new IllegalStateException("Behavior tree is not bound: " + normalized);
        }
        BehaviorTreeProcess current = engine.getForOwner(owner);
        if (current != null) {
            engine.stop(level.getServer(), current.instanceId(), BehaviorTerminationReason.TREE_STOPPED);
        }
        owner.getData(GeometryNode.GRAPH_DATA_ATTACHMENT).selectBehaviorTree(normalized);
        return engine.start(level, owner, normalized);
    }

    public boolean unbind(Mob owner, String graphId) {
        ServerLevel level = requireServerOwner(owner);
        String normalized = GraphAssetId.require(graphId);
        if (!boundGraphs(owner).contains(normalized)) return false;
        BehaviorTreeProcess current = engine.getForOwner(owner);
        if (current != null && current.graphId().equals(normalized)) {
            engine.stop(level.getServer(), current.instanceId(), BehaviorTerminationReason.UNBOUND);
        }
        boolean removed = owner.getData(GeometryNode.GRAPH_DATA_ATTACHMENT).unbindBehaviorTree(normalized);
        if (removed) {
            GraphResourceLifecycleManager.INSTANCE.releaseBinding(level.getServer(),
                    new GraphResourceScope.EntityScope(level.dimension(), owner.getUUID()),
                    GraphBindingKey.behaviorTree(normalized));
        }
        return removed;
    }

    public boolean unbindAll(Mob owner) {
        ServerLevel level = requireServerOwner(owner);
        Set<String> bindings = Set.copyOf(boundGraphs(owner));
        BehaviorTreeProcess current = engine.getForOwner(owner);
        if (current != null) {
            engine.stop(level.getServer(), current.instanceId(), BehaviorTerminationReason.UNBOUND);
        }
        boolean removed = owner.getData(GeometryNode.GRAPH_DATA_ATTACHMENT).clearBehaviorTrees();
        if (removed) {
            GraphResourceScope scope = new GraphResourceScope.EntityScope(level.dimension(), owner.getUUID());
            for (String graphId : bindings) {
                GraphResourceLifecycleManager.INSTANCE.releaseBinding(level.getServer(), scope,
                        GraphBindingKey.behaviorTree(graphId));
            }
        }
        return removed;
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

    @Nullable
    public BehaviorTreeProcess getForOwner(Entity owner) {
        return engine.getForOwner(owner);
    }

    @Nullable
    public BehaviorTreeProcess get(MinecraftServer server, UUID instanceId) {
        return engine.get(server, instanceId);
    }

    @Nullable
    public BehaviorTreeDebugSnapshot debugSnapshot(MinecraftServer server, UUID instanceId) {
        return engine.debugSnapshot(server, instanceId);
    }

    @Nullable
    public BehaviorTreeDebugAccess debugAccess(MinecraftServer server, UUID instanceId) {
        return engine.debugAccess(server, instanceId);
    }

    @Nullable
    public BehaviorTreeDebugSnapshot debugSnapshotForOwner(MinecraftServer server, UUID ownerId) {
        return engine.debugSnapshotForOwner(server, ownerId);
    }

    public List<BehaviorTreeDebugSnapshot> debugSnapshotsForAsset(MinecraftServer server, String assetId) {
        return engine.debugSnapshotsForAsset(server, assetId);
    }

    @Nullable
    public BehaviorTerminationReason lastStopReasonForOwner(MinecraftServer server, UUID ownerId) {
        return engine.lastStopReasonForOwner(server, ownerId);
    }

    public boolean suspend(MinecraftServer server, UUID instanceId) {
        return engine.suspend(server, instanceId);
    }

    public boolean resume(MinecraftServer server, UUID instanceId) {
        return engine.resume(server, instanceId);
    }

    public boolean wake(MinecraftServer server, UUID instanceId) {
        return engine.wake(server, instanceId);
    }

    public boolean wake(Entity owner) {
        BehaviorTreeProcess instance = engine.getForOwner(owner);
        return instance != null && owner.level() instanceof ServerLevel level
                && engine.wake(level.getServer(), instance.instanceId());
    }

    public boolean stop(MinecraftServer server, UUID instanceId, BehaviorTerminationReason reason) {
        return engine.stop(server, instanceId, reason);
    }

    @Override
    public void tickLevel(ServerLevel level) {
        engine.tickLevel(level);
    }

    public void ownerUnavailable(Entity owner, BehaviorTerminationReason reason) {
        engine.ownerUnavailable(owner, reason);
    }

    @Override
    public void shutdown(MinecraftServer server) {
        engine.shutdown(server);
    }

    public int activeCount(MinecraftServer server) {
        return engine.activeCount(server);
    }

    private static ServerLevel requireServerOwner(Mob owner) {
        if (owner == null || !(owner.level() instanceof ServerLevel level)
                || owner.isRemoved() || !owner.isAlive()) {
            throw new IllegalArgumentException("Behavior owner must be a live server Mob");
        }
        return level;
    }

    private static String requireAvailable(String graphId) {
        String normalized = GraphAssetId.require(graphId);
        if (GraphAssetLifecycleIndex.INSTANCE.getArtifact(
                normalized, GraphKind.BEHAVIOR_TREE) == null) {
            throw new IllegalArgumentException("Behavior tree is unavailable: " + normalized);
        }
        return normalized;
    }

    private void onGraphAssetsChanged(GraphAssetLifecycleIndex.Change change) {
        engine.graphAssetsChanged(change.server(), change.assetIds());
    }
}
