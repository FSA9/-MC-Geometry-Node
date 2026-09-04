package com.mine.geometry_node.core.engine.blueprint.attachment;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintProcessSerializer;
import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintProcess;
import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.engine.graph.runtime.GraphCloseMode;
import com.mine.geometry_node.core.engine.graph.scheduling.DueTickScheduler;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetId;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * [蓝图运行容器核心] (组合模式)
 * 抽离了 Entity 和 Level 共用的虚拟机调度、属性存储和存档逻辑。
 */
public class BlueprintProcessContainer {

    private final Map<String, BlueprintProcess> processes = new HashMap<>();
    private final DueTickScheduler<String, BlueprintProcess> tickScheduler = new DueTickScheduler<>();

    // 脏标记回调 (用于通知 Level 保存存档)
    private final Runnable dirtyMarker;
    private final Runnable scheduleChangedCallback;
    private final Consumer<BlueprintProcess> processRemovedCallback;

    public BlueprintProcessContainer(Runnable dirtyMarker) {
        this(dirtyMarker, () -> {}, ignored -> {});
    }

    public BlueprintProcessContainer(Runnable dirtyMarker, Runnable scheduleChangedCallback) {
        this(dirtyMarker, scheduleChangedCallback, ignored -> {});
    }

    public BlueprintProcessContainer(Runnable dirtyMarker, Runnable scheduleChangedCallback,
                                     Consumer<BlueprintProcess> processRemovedCallback) {
        this.dirtyMarker = dirtyMarker != null ? dirtyMarker : () -> {};
        this.scheduleChangedCallback = scheduleChangedCallback != null ? scheduleChangedCallback : () -> {};
        this.processRemovedCallback = processRemovedCallback != null ? processRemovedCallback : ignored -> {};
    }

    /**
     * [心跳驱动] 只唤醒真正存在到期等待任务的常驻进程。
     */
    public void tick(ServerLevel level, @Nullable Entity target) {
        long currentTime = level.getGameTime();

        DueTickScheduler.Scheduled<String, BlueprintProcess> scheduled;
        while ((scheduled = tickScheduler.pollDue(currentTime)) != null) {

            BlueprintProcess process = processes.get(scheduled.key());
            if (process != scheduled.value()) {
                continue;
            }

            process.setEnvironment(level, target);
            process.tick(currentTime);

            long nextTick = process.getNextRequiredTick();
            if (nextTick <= currentTime) {
                nextTick = currentTime + 1;
            }
            scheduleProcessTick(process, nextTick);
        }
    }

    /**
     * [智能获取进程] 自带热更新比对机制。
     */
    public BlueprintProcess getProcess(String graphId) {
        graphId = GraphAssetId.require(graphId);
        BlueprintProcess process = this.processes.get(graphId);
        BlueprintPlan latestIndex = BlueprintRuntime.INSTANCE.getGraphIndex(graphId);

        if (latestIndex != null) {
            // 如果内存没进程，或者图纸版本更新了，强行重建
            if (process == null || process.getIndex() != latestIndex) {
                process = new BlueprintProcess(graphId, latestIndex);
                addProcess(process);
            }
        }
        return process;
    }

    /**
     * [挂载进程]
     */
    public void addProcess(BlueprintProcess process) {
        BlueprintProcess previous = this.processes.put(process.getGraphId(), process);
        if (previous != null && previous != process) {
            previous.setTickScheduleCallback(null);
            previous.shutdown("graph_replaced");
            notifyProcessRemoved(previous);
            if (this.tickScheduler.cancel(previous.getGraphId())) {
                notifyScheduleChanged();
            }
        }
        attachProcess(process);
        this.dirtyMarker.run(); // 触发存盘
    }

    /**
     * [卸载进程]
     */
    public void removeProcess(String graphId) {
        removeProcess(graphId, GraphCloseMode.IMMEDIATE);
    }

    public void removeProcess(String graphId, GraphCloseMode closeMode) {
        String canonicalGraphId = GraphAssetId.require(graphId);
        BlueprintProcess process = this.processes.get(canonicalGraphId);
        if (process == null) return;

        GraphCloseMode mode = closeMode != null ? closeMode : GraphCloseMode.IMMEDIATE;
        if (mode == GraphCloseMode.DRAIN) {
            this.dirtyMarker.run();
            process.requestDrain(() -> completeDrainingProcess(canonicalGraphId, process));
            return;
        }
        removeProcessNow(canonicalGraphId, process, "graph_unloaded");
    }

    public Collection<BlueprintProcess> getProcesses() {
        return this.processes.values();
    }

    public long getNextScheduledTick() {
        return tickScheduler.nextDueTick();
    }

    /**
     * [清理全部]
     */
    public void clear() {
        for (BlueprintProcess process : this.processes.values()) {
            process.setTickScheduleCallback(null);
            process.shutdown("graph_unloaded");
            notifyProcessRemoved(process);
        }
        this.processes.clear();
        if (clearTickSchedule()) {
            notifyScheduleChanged();
        }
        this.dirtyMarker.run();
    }

    public void clearProcessesForSerialization() {
        for (BlueprintProcess process : this.processes.values()) {
            process.setTickScheduleCallback(null);
            process.shutdown("graph_reloaded");
            notifyProcessRemoved(process);
        }
        this.processes.clear();
        if (clearTickSchedule()) {
            notifyScheduleChanged();
        }
    }

    public void putProcessForSerialization(BlueprintProcess process) {
        BlueprintProcess previous = this.processes.put(process.getGraphId(), process);
        if (previous != null && previous != process) {
            previous.setTickScheduleCallback(null);
            previous.shutdown("graph_reloaded");
            notifyProcessRemoved(previous);
            if (this.tickScheduler.cancel(previous.getGraphId())) {
                notifyScheduleChanged();
            }
        }
        attachProcess(process);
    }

    /**
     * [序列化] 将进程和属性保存到 NBT
     */
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return BlueprintProcessSerializer.saveContainer(this, tag, provider);
    }

    /**
     * [反序列化] 从 NBT 恢复进程和属性
     */
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        BlueprintProcessSerializer.loadContainer(this, tag, provider);
    }

    public Map<String, BlueprintProcess> getProcessesMap() {
        return this.processes;
    }

    private void attachProcess(BlueprintProcess process) {
        process.setTickScheduleCallback(() -> scheduleProcessTick(process));
        scheduleProcessTick(process);
        if (process.isDraining()) {
            process.requestDrain(() -> completeDrainingProcess(process.getGraphId(), process));
        }
    }

    private void completeDrainingProcess(String graphId, BlueprintProcess process) {
        removeProcessNow(graphId, process, "graph_drain_finished");
    }

    private void removeProcessNow(String graphId, BlueprintProcess process, String reason) {
        if (!this.processes.remove(graphId, process)) return;
        process.setTickScheduleCallback(null);
        process.shutdown(reason);
        notifyProcessRemoved(process);
        if (this.tickScheduler.cancel(graphId)) {
            notifyScheduleChanged();
        }
        this.dirtyMarker.run();
    }

    private void scheduleProcessTick(BlueprintProcess process) {
        scheduleProcessTick(process, process.getNextRequiredTick());
    }

    private void scheduleProcessTick(BlueprintProcess process, long nextTick) {
        String graphId = process.getGraphId();
        if (this.processes.get(graphId) != process) {
            return;
        }
        if (nextTick == Long.MAX_VALUE) {
            if (this.tickScheduler.cancel(graphId)) {
                notifyScheduleChanged();
            }
            return;
        }

        if (this.tickScheduler.schedule(graphId, process, nextTick)) {
            notifyScheduleChanged();
        }
    }

    private boolean clearTickSchedule() {
        return this.tickScheduler.clear();
    }

    private void notifyScheduleChanged() {
        this.scheduleChangedCallback.run();
    }

    private void notifyProcessRemoved(BlueprintProcess process) {
        try {
            processRemovedCallback.accept(process);
        } catch (RuntimeException exception) {
            GeometryNode.LOGGER.error("Blueprint process resource cleanup failed: graph={}",
                    process.getGraphId(), exception);
        }
    }
}
