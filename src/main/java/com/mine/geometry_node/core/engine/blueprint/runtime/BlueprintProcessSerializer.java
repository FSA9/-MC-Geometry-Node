package com.mine.geometry_node.core.engine.blueprint.runtime;

import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.blueprint.attachment.BlueprintProcessContainer;
import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.engine.graph.value.GraphValueCodecRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;


public class BlueprintProcessSerializer {
    public static CompoundTag save(BlueprintProcess process, CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag snapshot = saveSnapshot(process, provider);
        replaceContents(tag, snapshot);
        return tag;
    }

    private static void replaceContents(CompoundTag target, CompoundTag source) {
        for (String key : new HashSet<>(target.keySet())) {
            target.remove(key);
        }
        for (String key : source.keySet()) {
            target.put(key, source.get(key).copy());
        }
    }

    private static CompoundTag saveSnapshot(BlueprintProcess process, HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        BlueprintPlan index = process.getIndex();
        tag.putString("GraphId", process.getGraphId());
        if (process.isDraining()) {
            tag.putBoolean("Draining", true);
        }

        // 保存休眠线程
        if (process.hasSleepingThreadsForSerialization()) {
            ListTag threadsTag = new ListTag();
            for (BlueprintProcess.ExecutionThread thread : process.getSleepingThreadsForSerialization()) {
                CompoundTag tTag = new CompoundTag();
                // 注意这里需要 process.getLevel()，我们需要在 BlueprintProcess 中暴露一下，或者直接判断
                long currentTime = (process.getLevel() != null) ? process.getLevel().getGameTime() : 0;
                long remaining = (process.getLevel() != null) ? Math.max(0, thread.wakeUpTick - currentTime) : thread.wakeUpTick;
                tTag.putLong("WaitRemaining", remaining);

                String currentFlowNodeId = index.getNodeId(thread.getCurrentFlowIdForSerialization());
                if (currentFlowNodeId != null) {
                    tTag.putString("CurrentFlowId", currentFlowNodeId);
                }
                if (thread.getCurrentEntryPortForSerialization() != null) {
                    tTag.putString("CurrentEntryPort", thread.getCurrentEntryPortForSerialization());
                }
                if (thread.getParentJoinIdForSerialization() != null) {
                    tTag.putString("ParentJoinId", thread.getParentJoinIdForSerialization());
                }
                String eventSourceNodeId = index.getNodeId(thread.getEventSourceNodeIdForSerialization());
                if (eventSourceNodeId != null) {
                    tTag.putString("EventSourceNodeId", eventSourceNodeId);
                }
                String contextDimension = thread.getThreadDimensionId();
                if (contextDimension != null) {
                    tTag.putString("ContextDimension", contextDimension);
                }
                UUID contextEntity = thread.getThreadEntityUuid();
                if (contextEntity != null) {
                    tTag.putString("ContextEntity", contextEntity.toString());
                }

                ListTag execStackTag = new ListTag();
                for (BlueprintPlan.IntFlowTarget frame : thread.getExecutionStackForSerialization()) {
                    CompoundTag frameTag = new CompoundTag();
                    frameTag.putString("TargetNodeId", index.getNodeId(frame.targetNodeId()));
                    frameTag.putString("TargetPortName", frame.targetPortName());
                    execStackTag.add(frameTag);
                }
                tTag.put("ExecutionStack", execStackTag);

                // 存线程事件寄存器（索引化 + 动态键）
                CompoundTag regTag = new CompoundTag();
                saveRegistersToTag(regTag, thread.getEventRegistersForSerialization(), thread.getDynamicEventDataForSerialization(), index, provider);
                tTag.put("Registers", regTag);

                // 存临时黑板
                CompoundTag tempTag = new CompoundTag();
                for (Map.Entry<String, Object> entry : thread.tempData.entrySet()) {
                    if (entry.getValue() != null) {
                        tempTag.put(entry.getKey(), GraphValueCodecRegistry.toTagStrict(entry.getValue(), provider));
                    }
                }
                if (!tempTag.isEmpty()) tTag.put("TempData", tempTag);

                threadsTag.add(tTag);
            }
            tag.put("SleepingThreads", threadsTag);
        }
        if (process.hasBranchJoinsForSerialization()) {
            ListTag joinsTag = new ListTag();
            for (BlueprintProcess.BranchJoin join : process.getBranchJoinsForSerialization()) {
                CompoundTag joinTag = new CompoundTag();
                joinTag.putString("JoinId", join.id);
                String completionNodeId = index.getNodeId(join.completionNodeId);
                if (completionNodeId != null) {
                    joinTag.putString("CompletionNodeId", completionNodeId);
                    joinTag.putString("CompletionEntryPort", join.completionEntryPort);
                }
                joinTag.putInt("PendingChildren", join.pendingChildren);
                joinTag.putBoolean("LaunchFinished", join.launchFinished);
                String eventSourceNodeId = index.getNodeId(join.eventSourceNodeId);
                if (eventSourceNodeId != null) {
                    joinTag.putString("EventSourceNodeId", eventSourceNodeId);
                }
                if (join.threadDimensionId != null) {
                    joinTag.putString("ContextDimension", join.threadDimensionId);
                }
                if (join.threadEntityUuid != null) {
                    joinTag.putString("ContextEntity", join.threadEntityUuid.toString());
                }

                CompoundTag regTag = new CompoundTag();
                saveRegistersToTag(regTag, join.eventRegisters, join.dynamicEventData, index, provider);
                joinTag.put("Registers", regTag);

                CompoundTag tempTag = new CompoundTag();
                for (Map.Entry<String, Object> entry : join.tempData.entrySet()) {
                    if (entry.getValue() != null) {
                        tempTag.put(entry.getKey(), GraphValueCodecRegistry.toTagStrict(entry.getValue(), provider));
                    }
                }
                if (!tempTag.isEmpty()) joinTag.put("TempData", tempTag);

                joinsTag.add(joinTag);
            }
            tag.put("BranchJoins", joinsTag);
        }
        return tag;
    }

    public static BlueprintProcess load(CompoundTag tag, BlueprintPlan index, HolderLookup.Provider provider) {
        String graphId = tag.getStringOr("GraphId", "");
        BlueprintProcess process = new BlueprintProcess(graphId, index);

        int exactSize = index.getRegisterCount() + 8;

        // 恢复线程
        if (tag.contains("SleepingThreads")) {
            ListTag list = tag.getListOrEmpty("SleepingThreads");
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tTag = list.getCompoundOrEmpty(i);
                int currentFlowId = -1;
                Tag currentFlowTag = tTag.get("CurrentFlowId");
                if (currentFlowTag != null) {
                    if (currentFlowTag.asString().isPresent()) {
                        currentFlowId = index.getNodeKey(currentFlowTag.asString().orElse(""));
                    } else if (currentFlowTag.asInt().isPresent()) {
                        currentFlowId = currentFlowTag.asInt().orElse(-1);
                    }
                }
                String currentPort = tTag.getStringOr("CurrentEntryPort", "flow_in");
                if (currentPort.isEmpty()) currentPort = "flow_in";

                BlueprintProcess.ExecutionThread thread = process.new ExecutionThread(currentFlowId, currentPort);
                thread.setEventSourceNodeIdForSerialization(
                        index.getNodeKey(tTag.getStringOr("EventSourceNodeId", ""))
                );
                if (tTag.contains("ParentJoinId")) {
                    thread.setParentJoinIdForSerialization(tTag.getStringOr("ParentJoinId", ""));
                }
                UUID contextEntity = null;
                if (tTag.contains("ContextEntity")) {
                    try {
                        contextEntity = UUID.fromString(tTag.getStringOr("ContextEntity", ""));
                    } catch (IllegalArgumentException ignored) {}
                }
                String contextDimension = tTag.contains("ContextDimension")
                        ? tTag.getStringOr("ContextDimension", "")
                        : null;
                thread.restoreEnvironment(contextDimension, contextEntity);

                if (tTag.contains("ExecutionStack")) {
                    ListTag stackList = tTag.getListOrEmpty("ExecutionStack");
                    for (int j = 0; j < stackList.size(); j++) {
                        CompoundTag frameTag = stackList.getCompoundOrEmpty(j);
                        int targetId = index.getNodeKey(frameTag.getStringOr("TargetNodeId", ""));
                        String portName = frameTag.getStringOr("TargetPortName", "");
                        if (targetId != -1) {
                            thread.getExecutionStackForSerialization().add(new BlueprintPlan.IntFlowTarget(targetId, portName));
                        }
                    }
                }

                if (currentFlowId != -1 || !thread.getExecutionStackForSerialization().isEmpty()) {
                    thread.wakeUpTick = tTag.getLongOr("WaitRemaining", 0L);
                    thread.state = BlueprintProcess.ExecutionThread.State.WAITING;

                    DecodedRegisters registers = loadRegisters(
                            tTag.getCompoundOrEmpty("Registers"), exactSize, index, provider);
                    thread.setEventRegistersForSerialization(registers.statics());
                    thread.setDynamicEventDataForSerialization(registers.dynamics());

                    process.addSleepingThreadForSerialization(thread);

                    if (tTag.contains("TempData")) {
                        CompoundTag tempTag = tTag.getCompoundOrEmpty("TempData");
                        for (String key : tempTag.keySet()) {
                            Object obj = GraphValueCodecRegistry.fromTag(tempTag.get(key), provider);
                            if (obj != null) thread.tempData.put(key, obj);
                        }
                    }
                }
            }
            process.markNeedsTimeRebaseForSerialization();
        }
        process.clearBranchJoinsForSerialization();
        if (tag.contains("BranchJoins")) {
            ListTag list = tag.getListOrEmpty("BranchJoins");
            for (int i = 0; i < list.size(); i++) {
                CompoundTag joinTag = list.getCompoundOrEmpty(i);
                int completionNodeId = index.getNodeKey(joinTag.getStringOr("CompletionNodeId", ""));
                String completionEntryPort = joinTag.getStringOr("CompletionEntryPort", "");
                String joinId = joinTag.getStringOr("JoinId", "");
                if (joinId.isBlank()) {
                    continue;
                }

                DecodedRegisters registers = loadRegisters(
                        joinTag.getCompoundOrEmpty("Registers"), exactSize, index, provider);

                Map<String, Object> tempData = new HashMap<>();
                if (joinTag.contains("TempData")) {
                    CompoundTag tempTag = joinTag.getCompoundOrEmpty("TempData");
                    for (String key : tempTag.keySet()) {
                        Object obj = GraphValueCodecRegistry.fromTag(tempTag.get(key), provider);
                        if (obj != null) {
                            tempData.put(key, obj);
                        }
                    }
                }

                BlueprintProcess.BranchJoin join = new BlueprintProcess.BranchJoin(
                        joinId,
                        completionNodeId,
                        completionEntryPort,
                        index.getNodeKey(joinTag.getStringOr("EventSourceNodeId", "")),
                        joinTag.contains("ContextDimension")
                                ? joinTag.getStringOr("ContextDimension", "") : null,
                        parseUuid(joinTag.getStringOr("ContextEntity", "")),
                        registers.statics(),
                        registers.dynamics(),
                        tempData
                );
                join.pendingChildren = Math.max(0, joinTag.getIntOr("PendingChildren", 0));
                join.launchFinished = joinTag.getBooleanOr("LaunchFinished", false);
                process.addBranchJoinForSerialization(join);
            }
        }
        process.restoreDrainingForSerialization(tag.getBooleanOr("Draining", false));

        return process;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void saveRegistersToTag(CompoundTag tag, Object[] statics, Map<String, Object> dynamics,
                                           BlueprintPlan index, HolderLookup.Provider provider) {
        for (int i = 0; i < statics.length; i++) {
            if (statics[i] != null) {
                String key = index.getPortName(i);
                if (key != null) {
                    tag.put(key, GraphValueCodecRegistry.toTagStrict(statics[i], provider));
                }
            }
        }
        if (dynamics != null) {
            for (Map.Entry<String, Object> entry : dynamics.entrySet()) {
                if (entry.getValue() != null) {
                    tag.put(entry.getKey(), GraphValueCodecRegistry.toTagStrict(entry.getValue(), provider));
                }
            }
        }
    }

    private static DecodedRegisters loadRegisters(CompoundTag tag, int size, BlueprintPlan index,
                                                  HolderLookup.Provider provider) {
        Object[] statics = new Object[size];
        Map<String, Object> dynamics = null;
        for (String key : tag.keySet()) {
            Object obj = GraphValueCodecRegistry.fromTag(tag.get(key), provider);
            if (obj != null) {
                int id = index.getPortKey(key);
                if (id != -1 && id < statics.length) {
                    statics[id] = obj;
                } else {
                    if (dynamics == null) dynamics = new HashMap<>();
                    dynamics.put(key, obj);
                }
            }
        }
        return new DecodedRegisters(statics, dynamics);
    }

    private record DecodedRegisters(Object[] statics, Map<String, Object> dynamics) {}

    public static CompoundTag saveContainer(BlueprintProcessContainer container, CompoundTag tag, HolderLookup.Provider provider) {
        tag.remove("ActiveProcesses");
        if (!container.getProcessesMap().isEmpty()) {
            ListTag processList = new ListTag();
            for (BlueprintProcess process : container.getProcessesMap().values()) {
                CompoundTag processTag = new CompoundTag();
                save(process, processTag, provider);
                processList.add(processTag);
            }
            tag.put("ActiveProcesses", processList);
        }
        return tag;
    }

    public static void loadContainer(BlueprintProcessContainer container, CompoundTag tag, HolderLookup.Provider provider) {
        container.clearProcessesForSerialization();
        if (tag.contains("ActiveProcesses")) {
            ListTag list = tag.getListOrEmpty("ActiveProcesses");
            for (int i = 0; i < list.size(); i++) {
                CompoundTag pTag = list.getCompoundOrEmpty(i);
                String graphId = pTag.getStringOr("GraphId", "");
                BlueprintPlan index = BlueprintRuntime.INSTANCE.getGraphIndex(graphId);
                if (index != null) {
                    container.putProcessForSerialization(load(pTag, index, provider));
                }
            }
        }

    }
}
