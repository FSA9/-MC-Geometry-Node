package com.mine.geometry_node.core.engine.blueprint.runtime;

import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.blueprint.attachment.GraphContainer;
import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.engine.graph.value.GraphValueCodecRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;


public class BlueprintProcessSerializer {
    public static CompoundTag save(BlueprintProcess process, CompoundTag tag, HolderLookup.Provider provider) {
        BlueprintPlan index = process.getIndex();
        tag.putString("GraphId", process.getGraphId());
        if (process.isDraining()) {
            tag.putBoolean("Draining", true);
        }

        // 1. 保存变量栈
        ListTag stackTag = new ListTag();
        Iterator<BlueprintProcess.VariableScope> it = process.getVariableScopesDescendingForSerialization();
        while (it.hasNext()) {
            CompoundTag scopeTag = new CompoundTag();
            BlueprintProcess.VariableScope scope = it.next();
            saveVariablesToTag(scopeTag, scope.statics, scope.dynamics, index, provider);
            stackTag.add(scopeTag);
        }
        tag.put("VariableStack", stackTag);

        // 2. 保存休眠线程
        if (process.hasSleepingThreadsForSerialization()) {
            ListTag threadsTag = new ListTag();
            for (BlueprintProcess.ExecutionThread thread : process.getSleepingThreadsForSerialization()) {
                CompoundTag tTag = new CompoundTag();
                // 注意这里需要 process.getLevel()，我们需要在 BlueprintProcess 中暴露一下，或者直接判断
                long currentTime = (process.getLevel() != null) ? process.getLevel().getGameTime() : 0;
                long remaining = (process.getLevel() != null) ? Math.max(0, thread.wakeUpTick - currentTime) : thread.wakeUpTick;
                tTag.putLong("WaitRemaining", remaining);

                String currentFlowNodeId = index.getIdToString(thread.getCurrentFlowIdForSerialization());
                if (currentFlowNodeId != null) {
                    tTag.putString("CurrentFlowId", currentFlowNodeId);
                }
                if (thread.getCurrentEntryPortForSerialization() != null) {
                    tTag.putString("CurrentEntryPort", thread.getCurrentEntryPortForSerialization());
                }
                if (thread.getParentJoinIdForSerialization() != null) {
                    tTag.putString("ParentJoinId", thread.getParentJoinIdForSerialization());
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
                    frameTag.putString("TargetNodeId", index.getIdToString(frame.targetNodeId()));
                    frameTag.putString("TargetPortName", frame.targetPortName());
                    execStackTag.add(frameTag);
                }
                tTag.put("ExecutionStack", execStackTag);

                // 存线程寄存器 (静态 + 动态)
                CompoundTag regTag = new CompoundTag();
                saveVariablesToTag(regTag, thread.getEventRegistersForSerialization(), thread.getDynamicEventDataForSerialization(), index, provider);
                tTag.put("Registers", regTag);

                // 存临时黑板
                CompoundTag tempTag = new CompoundTag();
                for (Map.Entry<String, Object> entry : thread.tempData.entrySet()) {
                    Tag s = GraphValueCodecRegistry.toTag(entry.getValue(), provider);
                    if (s != null) tempTag.put(entry.getKey(), s);
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
                joinTag.putString("OwnerNodeId", index.getIdToString(join.ownerNodeId));
                joinTag.putString("CompletedPortName", join.completedPortName);
                joinTag.putInt("PendingChildren", join.pendingChildren);
                joinTag.putBoolean("LaunchFinished", join.launchFinished);

                CompoundTag regTag = new CompoundTag();
                saveVariablesToTag(regTag, join.eventRegisters, join.dynamicEventData, index, provider);
                joinTag.put("Registers", regTag);

                CompoundTag tempTag = new CompoundTag();
                for (Map.Entry<String, Object> entry : join.tempData.entrySet()) {
                    Tag s = GraphValueCodecRegistry.toTag(entry.getValue(), provider);
                    if (s != null) tempTag.put(entry.getKey(), s);
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
        process.clearVariableScopesForSerialization(); // 清理构造函数默认放入的空栈

        int exactSize = index.getRegisterCount() + 8;

        // 1. 恢复变量栈
        if (tag.contains("VariableStack")) {
            ListTag list = tag.getListOrEmpty("VariableStack");
            for (int i = 0; i < list.size(); i++) {
                BlueprintProcess.VariableScope scope = new BlueprintProcess.VariableScope(exactSize);
                loadVariablesFromTag(list.getCompoundOrEmpty(i), scope.statics, scope, index, provider);
                process.addVariableScopeLastForSerialization(scope);
            }
        } else {
            process.pushVariableScopeForSerialization(new BlueprintProcess.VariableScope(exactSize));
        }

        // 2. 恢复线程
        if (tag.contains("SleepingThreads")) {
            ListTag list = tag.getListOrEmpty("SleepingThreads");
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tTag = list.getCompoundOrEmpty(i);
                int currentFlowId = -1;
                Tag currentFlowTag = tTag.get("CurrentFlowId");
                if (currentFlowTag != null) {
                    if (currentFlowTag.asString().isPresent()) {
                        currentFlowId = index.getStringToId(currentFlowTag.asString().orElse(""));
                    } else if (currentFlowTag.asInt().isPresent()) {
                        currentFlowId = currentFlowTag.asInt().orElse(-1);
                    }
                }
                String currentPort = tTag.getStringOr("CurrentEntryPort", "flow_in");
                if (currentPort.isEmpty()) currentPort = "flow_in";

                BlueprintProcess.ExecutionThread thread = process.new ExecutionThread(currentFlowId, currentPort);
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
                        int targetId = index.getStringToId(frameTag.getStringOr("TargetNodeId", ""));
                        String portName = frameTag.getStringOr("TargetPortName", "");
                        if (targetId != -1) {
                            thread.getExecutionStackForSerialization().add(new BlueprintPlan.IntFlowTarget(targetId, portName));
                        }
                    }
                }

                if (currentFlowId != -1 || !thread.getExecutionStackForSerialization().isEmpty()) {
                    thread.wakeUpTick = tTag.getLongOr("WaitRemaining", 0L);
                    thread.state = BlueprintProcess.ExecutionThread.State.WAITING;

                    BlueprintProcess.VariableScope tempScope = new BlueprintProcess.VariableScope(exactSize);
                    loadVariablesFromTag(tTag.getCompoundOrEmpty("Registers"), tempScope.statics, tempScope, index, provider);
                    thread.setEventRegistersForSerialization(tempScope.statics);
                    thread.setDynamicEventDataForSerialization(tempScope.dynamics);

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
                int ownerNodeId = index.getStringToId(joinTag.getStringOr("OwnerNodeId", ""));
                String completedPortName = joinTag.getStringOr("CompletedPortName", "");
                String joinId = joinTag.getStringOr("JoinId", "");
                if (ownerNodeId == -1 || joinId.isBlank() || completedPortName.isBlank()) {
                    continue;
                }

                BlueprintProcess.VariableScope regScope = new BlueprintProcess.VariableScope(exactSize);
                loadVariablesFromTag(joinTag.getCompoundOrEmpty("Registers"), regScope.statics, regScope, index, provider);

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
                        ownerNodeId,
                        completedPortName,
                        regScope.statics,
                        regScope.dynamics,
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

    private static void saveVariablesToTag(CompoundTag tag, Object[] statics, Map<String, Object> dynamics, BlueprintPlan index, HolderLookup.Provider provider) {
        for (int i = 0; i < statics.length; i++) {
            if (statics[i] != null) {
                String key = index.getKeyFromId(i);
                Tag s = GraphValueCodecRegistry.toTag(statics[i], provider);
                if (s != null && key != null) tag.put(key, s);
            }
        }
        if (dynamics != null) {
            for (Map.Entry<String, Object> entry : dynamics.entrySet()) {
                Tag s = GraphValueCodecRegistry.toTag(entry.getValue(), provider);
                if (s != null) tag.put(entry.getKey(), s);
            }
        }
    }

    private static void loadVariablesFromTag(CompoundTag tag, Object[] statics, BlueprintProcess.VariableScope scope, BlueprintPlan index, HolderLookup.Provider provider) {
        for (String key : tag.keySet()) {
            Object obj = GraphValueCodecRegistry.fromTag(tag.get(key), provider);
            if (obj != null) {
                int id = index.getKeyId(key);
                if (id != -1 && id < statics.length) {
                    statics[id] = obj;
                } else {
                    if (scope.dynamics == null) scope.dynamics = new HashMap<>();
                    scope.dynamics.put(key, obj);
                }
            }
        }
    }

    public static CompoundTag saveContainer(GraphContainer container, CompoundTag tag, HolderLookup.Provider provider) {
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

    public static void loadContainer(GraphContainer container, CompoundTag tag, HolderLookup.Provider provider) {
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
