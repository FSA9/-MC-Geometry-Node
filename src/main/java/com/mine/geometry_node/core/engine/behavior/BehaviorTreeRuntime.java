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
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.compile.CompiledGraph;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompilationService;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntime;
import com.mine.geometry_node.core.engine.graph.storage.DynamicGraphManager;
import com.mine.geometry_node.core.engine.graph.storage.GraphResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
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
        DynamicGraphManager.addReloadListener(GraphKind.BEHAVIOR_TREE, this::onDynamicGraphReload);
        BehaviorEventHandler.init();
    }

    public BehaviorTreeInstance start(ServerLevel level, Mob owner, String graphId) {
        return service.start(level, owner, graphId);
    }

    public void bind(Mob owner, String graphId) {
        requireServerOwner(owner);
        String normalized = requireAvailable(graphId);
        BehaviorTreeInstance current = service.getForOwner(owner);
        if (current != null && !current.graphId().equals(normalized)) {
            throw new IllegalStateException("Owner is running " + current.graphId()
                    + "; use switch to replace the active tree");
        }
        owner.getData(GeometryNode.GRAPH_DATA_ATTACHMENT).bindBehaviorTree(normalized);
    }

    public BehaviorTreeInstance startBound(Mob owner) {
        ServerLevel level = requireServerOwner(owner);
        if (service.getForOwner(owner) != null) {
            throw new IllegalStateException("Owner already has a running behavior tree");
        }
        String graphId = boundGraph(owner);
        if (graphId == null) throw new IllegalStateException("Owner has no bound behavior tree");
        return service.start(level, owner, graphId);
    }

    public BehaviorTreeInstance switchTo(Mob owner, String graphId) {
        ServerLevel level = requireServerOwner(owner);
        String normalized = requireAvailable(graphId);
        BehaviorTreeInstance current = service.getForOwner(owner);
        if (current != null) {
            service.stop(level.getServer(), current.instanceId(), BehaviorTerminationReason.TREE_STOPPED);
        }
        owner.getData(GeometryNode.GRAPH_DATA_ATTACHMENT).bindBehaviorTree(normalized);
        return service.start(level, owner, normalized);
    }

    public boolean unbind(Mob owner) {
        ServerLevel level = requireServerOwner(owner);
        BehaviorTreeInstance current = service.getForOwner(owner);
        if (current != null) {
            service.stop(level.getServer(), current.instanceId(), BehaviorTerminationReason.UNBOUND);
        }
        boolean changed = !boundGraphs(owner).isEmpty();
        owner.getData(GeometryNode.GRAPH_DATA_ATTACHMENT).clearBehaviorTrees();
        return changed;
    }

    public Set<String> boundGraphs(Entity owner) {
        if (owner == null) return Set.of();
        return owner.getData(GeometryNode.GRAPH_DATA_ATTACHMENT).getBoundBehaviorTrees();
    }

    @Nullable
    public String boundGraph(Entity owner) {
        return boundGraphs(owner).stream().findFirst().orElse(null);
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
        CompiledGraph dynamic = DynamicGraphManager.getArtifact(normalized, GraphKind.BEHAVIOR_TREE);
        CompiledGraph packaged = GraphResourceManager.getInstance()
                .getArtifact(normalized, GraphKind.BEHAVIOR_TREE);
        if (dynamic == null && packaged == null) {
            throw new IllegalArgumentException("Behavior tree is unavailable: " + normalized);
        }
        return normalized;
    }

    private void onDynamicGraphReload(MinecraftServer server, String graphId,
                                      @Nullable CompiledGraph oldArtifact,
                                      @Nullable CompiledGraph newArtifact) {
        service.graphReloaded(server, graphId, newArtifact);
    }
}
