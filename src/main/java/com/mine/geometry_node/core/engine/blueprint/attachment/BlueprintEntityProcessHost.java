package com.mine.geometry_node.core.engine.blueprint.attachment;

import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintProcess;
import com.mine.geometry_node.core.engine.graph.runtime.GraphCloseMode;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceLifecycleManager;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceScope;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Collection;

/** Owns blueprint process state attached to one entity. */
public final class BlueprintEntityProcessHost {
    private final GraphContainer container = new GraphContainer(
            () -> {}, this::onScheduleChanged, this::onProcessRemoved);
    private WeakReference<Entity> owner = new WeakReference<>(null);

    public void attachOwner(Entity entity) {
        owner = new WeakReference<>(entity);
        for (BlueprintProcess process : container.getProcesses()) {
            process.setGraphOwner(entity);
        }
    }

    public void tick(Entity entity) {
        attachOwner(entity);
        if (entity.level() instanceof ServerLevel level) {
            container.tick(level, entity);
        }
    }

    public void addProcess(BlueprintProcess process) {
        process.setGraphOwner(owner.get());
        container.addProcess(process);
    }

    public void removeProcess(String graphId) {
        container.removeProcess(graphId);
    }

    public void removeProcess(String graphId, GraphCloseMode closeMode) {
        container.removeProcess(graphId, closeMode);
    }

    public Collection<BlueprintProcess> processes() {
        return container.getProcesses();
    }

    public long nextScheduledTick() {
        return container.getNextScheduledTick();
    }

    @Nullable
    public BlueprintProcess getProcess(String graphId) {
        BlueprintProcess process = container.getProcess(graphId);
        if (process != null) process.setGraphOwner(owner.get());
        return process;
    }

    public void clear() {
        container.clear();
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return container.save(tag, provider);
    }

    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        container.load(tag, provider);
    }

    private void onScheduleChanged() {
        Entity entity = owner.get();
        if (entity != null) BlueprintRuntime.INSTANCE.markActive(entity);
    }

    private void onProcessRemoved(BlueprintProcess process) {
        Entity entity = owner.get();
        if (entity != null && entity.level() instanceof ServerLevel level) {
            GraphResourceLifecycleManager.INSTANCE.releaseBinding(level.getServer(),
                    new GraphResourceScope.EntityScope(level.dimension(), entity.getUUID()),
                    GraphBindingKey.blueprint(process.getGraphId()));
        }
    }
}
