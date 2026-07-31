package com.mine.geometry_node.core.engine.blueprint.attachment;

import com.mine.geometry_node.core.engine.blueprint.runtime.GraphProcessSerializer;
import com.mine.geometry_node.core.engine.blueprint.runtime.GraphEngine;
import com.mine.geometry_node.core.engine.blueprint.runtime.GraphProcess;
import com.mine.geometry_node.core.engine.blueprint.runtime.RuntimeGraphIndex;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * [蓝图运行容器核心] (组合模式)
 * 抽离了 Entity 和 Level 共用的虚拟机调度、属性存储和存档逻辑。
 */
public class GraphContainer {

    private final Map<String, GraphProcess> processes = new HashMap<>();
    private final Map<String, Object> attributes = new HashMap<>();
    private final PriorityQueue<ScheduledProcess> tickQueue = new PriorityQueue<>(Comparator.comparingLong(ScheduledProcess::nextTick));
    private final Map<String, ScheduledProcess> activeTickSchedules = new HashMap<>();

    // 脏标记回调 (用于通知 Level 保存存档)
    private final Runnable dirtyMarker;
    private final Runnable scheduleChangedCallback;

    public GraphContainer(Runnable dirtyMarker) {
        this(dirtyMarker, () -> {});
    }

    public GraphContainer(Runnable dirtyMarker, Runnable scheduleChangedCallback) {
        this.dirtyMarker = dirtyMarker != null ? dirtyMarker : () -> {};
        this.scheduleChangedCallback = scheduleChangedCallback != null ? scheduleChangedCallback : () -> {};
    }

    private record ScheduledProcess(String graphId, GraphProcess process, long nextTick) {}

    /**
     * [心跳驱动] 只唤醒真正存在到期等待任务的常驻进程。
     */
    public void tick(ServerLevel level, @Nullable Entity target) {
        if (tickQueue.isEmpty()) return;
        long currentTime = level.getGameTime();

        while (!tickQueue.isEmpty()) {
            ScheduledProcess scheduled = tickQueue.peek();
            if (activeTickSchedules.get(scheduled.graphId()) != scheduled) {
                tickQueue.poll();
                continue;
            }
            if (scheduled.nextTick() > currentTime) {
                return;
            }

            tickQueue.poll();
            activeTickSchedules.remove(scheduled.graphId(), scheduled);

            GraphProcess process = processes.get(scheduled.graphId());
            if (process != scheduled.process()) {
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
    public GraphProcess getProcess(String graphId) {
        GraphProcess process = this.processes.get(graphId);
        RuntimeGraphIndex latestIndex = GraphEngine.getGraphIndex(graphId);

        if (latestIndex != null) {
            // 如果内存没进程，或者图纸版本更新了，强行重建
            if (process == null || process.getIndex() != latestIndex) {
                process = new GraphProcess(graphId, latestIndex);
                addProcess(process);
            }
        }
        return process;
    }

    /**
     * [挂载进程]
     */
    public void addProcess(GraphProcess process) {
        GraphProcess previous = this.processes.put(process.getGraphId(), process);
        if (previous != null && previous != process) {
            previous.setTickScheduleCallback(null);
            previous.shutdown("graph_replaced");
            if (this.activeTickSchedules.remove(previous.getGraphId()) != null) {
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
        GraphProcess removed = this.processes.remove(graphId);
        if (removed != null) {
            removed.setTickScheduleCallback(null);
            removed.shutdown("graph_unloaded");
            if (this.activeTickSchedules.remove(graphId) != null) {
                notifyScheduleChanged();
            }
            this.dirtyMarker.run();
        }
    }

    public Collection<GraphProcess> getProcesses() {
        return this.processes.values();
    }

    public long getNextScheduledTick() {
        discardStaleTickSchedules();
        ScheduledProcess scheduled = this.tickQueue.peek();
        return scheduled != null ? scheduled.nextTick() : Long.MAX_VALUE;
    }

    /**
     * [清理全部]
     */
    public void clear() {
        for (GraphProcess process : this.processes.values()) {
            process.setTickScheduleCallback(null);
            process.shutdown("graph_unloaded");
        }
        this.processes.clear();
        this.attributes.clear();
        if (clearTickSchedule()) {
            notifyScheduleChanged();
        }
        this.dirtyMarker.run();
    }

    public void clearProcessesForSerialization() {
        for (GraphProcess process : this.processes.values()) {
            process.setTickScheduleCallback(null);
            process.shutdown("graph_reloaded");
        }
        this.processes.clear();
        if (clearTickSchedule()) {
            notifyScheduleChanged();
        }
    }

    public void putProcessForSerialization(GraphProcess process) {
        GraphProcess previous = this.processes.put(process.getGraphId(), process);
        if (previous != null && previous != process) {
            previous.setTickScheduleCallback(null);
            previous.shutdown("graph_reloaded");
            if (this.activeTickSchedules.remove(previous.getGraphId()) != null) {
                notifyScheduleChanged();
            }
        }
        attachProcess(process);
    }

    /**
     * [属性写入]
     */
    public void setAttribute(String key, Object value) {
        if (value == null) this.attributes.remove(key);
        else this.attributes.put(key, value);
        this.dirtyMarker.run();
    }

    /**
     * [属性读取]
     */
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    /**
     * [序列化] 将进程和属性保存到 NBT
     */
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return GraphProcessSerializer.saveContainer(this, tag, provider);
    }

    /**
     * [反序列化] 从 NBT 恢复进程和属性
     */
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        GraphProcessSerializer.loadContainer(this, tag, provider);
    }

    public Map<String, GraphProcess> getProcessesMap() {
        return this.processes;
    }

    public Map<String, Object> getAttributesMap() {
        return this.attributes;
    }

    private void attachProcess(GraphProcess process) {
        process.setTickScheduleCallback(() -> scheduleProcessTick(process));
        scheduleProcessTick(process);
    }

    private void scheduleProcessTick(GraphProcess process) {
        scheduleProcessTick(process, process.getNextRequiredTick());
    }

    private void scheduleProcessTick(GraphProcess process, long nextTick) {
        String graphId = process.getGraphId();
        if (this.processes.get(graphId) != process) {
            return;
        }
        if (nextTick == Long.MAX_VALUE) {
            if (this.activeTickSchedules.remove(graphId) != null) {
                notifyScheduleChanged();
            }
            return;
        }

        ScheduledProcess current = this.activeTickSchedules.get(graphId);
        if (current != null && current.process() == process && current.nextTick() == nextTick) {
            return;
        }

        ScheduledProcess scheduled = new ScheduledProcess(graphId, process, nextTick);
        this.activeTickSchedules.put(graphId, scheduled);
        this.tickQueue.offer(scheduled);
        notifyScheduleChanged();
    }

    private boolean clearTickSchedule() {
        boolean hadSchedule = !this.activeTickSchedules.isEmpty();
        this.tickQueue.clear();
        this.activeTickSchedules.clear();
        return hadSchedule;
    }

    private void discardStaleTickSchedules() {
        while (!this.tickQueue.isEmpty()) {
            ScheduledProcess scheduled = this.tickQueue.peek();
            if (this.activeTickSchedules.get(scheduled.graphId()) == scheduled) {
                return;
            }
            this.tickQueue.poll();
        }
    }

    private void notifyScheduleChanged() {
        this.scheduleChangedCallback.run();
    }
}
