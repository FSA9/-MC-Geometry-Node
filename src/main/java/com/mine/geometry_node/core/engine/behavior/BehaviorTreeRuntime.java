package com.mine.geometry_node.core.engine.behavior;

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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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

    private void onDynamicGraphReload(MinecraftServer server, String graphId,
                                      @Nullable CompiledGraph oldArtifact,
                                      @Nullable CompiledGraph newArtifact) {
        service.graphReloaded(server, graphId, newArtifact);
    }
}
