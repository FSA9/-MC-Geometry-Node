package com.mine.geometry_node.core.engine.blueprint.runtime;

import com.mine.geometry_node.core.engine.blueprint.attachment.GraphContainer;
import com.mine.geometry_node.core.engine.blueprint.variables.VariableRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;


public class GraphProcessSerializer {
    public static CompoundTag save(GraphProcess process, CompoundTag tag, HolderLookup.Provider provider) {
        RuntimeGraphIndex index = process.getIndex();
        tag.putString("GraphId", process.getGraphId());

        // 1. 保存变量栈
        ListTag stackTag = new ListTag();
        Iterator<GraphProcess.VariableScope> it = process.getVariableScopesDescendingForSerialization();
        while (it.hasNext()) {
            CompoundTag scopeTag = new CompoundTag();
            GraphProcess.VariableScope scope = it.next();
            saveVariablesToTag(scopeTag, scope.statics, scope.dynamics, index, provider);
            stackTag.add(scopeTag);
        }
        tag.put("VariableStack", stackTag);

        // 2. 保存休眠线程
        if (process.hasSleepingThreadsForSerialization()) {
            ListTag threadsTag = new ListTag();
            for (GraphProcess.ExecutionThread thread : process.getSleepingThreadsForSerialization()) {
                CompoundTag tTag = new CompoundTag();
                // 注意这里需要 process.getLevel()，我们需要在 GraphProcess 中暴露一下，或者直接判断
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
                for (RuntimeGraphIndex.IntFlowTarget frame : thread.getExecutionStackForSerialization()) {
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
                    Tag s = VariableRegistry.toTag(entry.getValue(), provider);
                    if (s != null) tempTag.put(entry.getKey(), s);
                }
                if (!tempTag.isEmpty()) tTag.put("TempData", tempTag);

                threadsTag.add(tTag);
            }
            tag.put("SleepingThreads", threadsTag);
        }
        if (process.hasBranchJoinsForSerialization()) {
            ListTag joinsTag = new ListTag();
            for (GraphProcess.BranchJoin join : process.getBranchJoinsForSerialization()) {
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
                    Tag s = VariableRegistry.toTag(entry.getValue(), provider);
                    if (s != null) tempTag.put(entry.getKey(), s);
                }
                if (!tempTag.isEmpty()) joinTag.put("TempData", tempTag);

                joinsTag.add(joinTag);
            }
            tag.put("BranchJoins", joinsTag);
        }
        return tag;
    }

    public static GraphProcess load(CompoundTag tag, RuntimeGraphIndex index, HolderLookup.Provider provider) {
        String graphId = tag.getString("GraphId");
        GraphProcess process = new GraphProcess(graphId, index);
        process.clearVariableScopesForSerialization(); // 清理构造函数默认放入的空栈

        int exactSize = index.getRegisterCount() + 8;

        // 1. 恢复变量栈
        if (tag.contains("VariableStack", Tag.TAG_LIST)) {
            ListTag list = tag.getList("VariableStack", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                GraphProcess.VariableScope scope = new GraphProcess.VariableScope(exactSize);
                loadVariablesFromTag(list.getCompound(i), scope.statics, scope, index, provider);
                process.addVariableScopeLastForSerialization(scope);
            }
        } else {
            process.pushVariableScopeForSerialization(new GraphProcess.VariableScope(exactSize));
        }

        // 2. 恢复线程
        if (tag.contains("SleepingThreads", Tag.TAG_LIST)) {
            ListTag list = tag.getList("SleepingThreads", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tTag = list.getCompound(i);
                int currentFlowId = -1;
                if (tTag.contains("CurrentFlowId", Tag.TAG_STRING)) {
                    currentFlowId = index.getStringToId(tTag.getString("CurrentFlowId"));
                } else if (tTag.contains("CurrentFlowId", Tag.TAG_INT)) {
                    currentFlowId = tTag.getInt("CurrentFlowId");
                }
                String currentPort = tTag.getString("CurrentEntryPort");
                if (currentPort == null || currentPort.isEmpty()) currentPort = "flow_in";

                GraphProcess.ExecutionThread thread = process.new ExecutionThread(currentFlowId, currentPort);
                if (tTag.contains("ParentJoinId", Tag.TAG_STRING)) {
                    thread.setParentJoinIdForSerialization(tTag.getString("ParentJoinId"));
                }
                UUID contextEntity = null;
                if (tTag.contains("ContextEntity", Tag.TAG_STRING)) {
                    try {
                        contextEntity = UUID.fromString(tTag.getString("ContextEntity"));
                    } catch (IllegalArgumentException ignored) {}
                }
                String contextDimension = tTag.contains("ContextDimension", Tag.TAG_STRING)
                        ? tTag.getString("ContextDimension")
                        : null;
                thread.restoreEnvironment(contextDimension, contextEntity);

                if (tTag.contains("ExecutionStack", Tag.TAG_LIST)) {
                    ListTag stackList = tTag.getList("ExecutionStack", Tag.TAG_COMPOUND);
                    for (int j = 0; j < stackList.size(); j++) {
                        CompoundTag frameTag = stackList.getCompound(j);
                        int targetId = index.getStringToId(frameTag.getString("TargetNodeId"));
                        String portName = frameTag.getString("TargetPortName");
                        if (targetId != -1) {
                            thread.getExecutionStackForSerialization().add(new RuntimeGraphIndex.IntFlowTarget(targetId, portName));
                        }
                    }
                }

                if (currentFlowId != -1 || !thread.getExecutionStackForSerialization().isEmpty()) {
                    thread.wakeUpTick = tTag.getLong("WaitRemaining");
                    thread.state = GraphProcess.ExecutionThread.State.WAITING;

                    GraphProcess.VariableScope tempScope = new GraphProcess.VariableScope(exactSize);
                    loadVariablesFromTag(tTag.getCompound("Registers"), tempScope.statics, tempScope, index, provider);
                    thread.setEventRegistersForSerialization(tempScope.statics);
                    thread.setDynamicEventDataForSerialization(tempScope.dynamics);

                    process.addSleepingThreadForSerialization(thread);

                    if (tTag.contains("TempData", Tag.TAG_COMPOUND)) {
                        CompoundTag tempTag = tTag.getCompound("TempData");
                        for (String key : tempTag.getAllKeys()) {
                            Object obj = VariableRegistry.fromTag(tempTag.get(key), provider);
                            if (obj != null) thread.tempData.put(key, obj);
                        }
                    }
                }
            }
            process.markNeedsTimeRebaseForSerialization();
        }
        process.clearBranchJoinsForSerialization();
        if (tag.contains("BranchJoins", Tag.TAG_LIST)) {
            ListTag list = tag.getList("BranchJoins", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag joinTag = list.getCompound(i);
                int ownerNodeId = index.getStringToId(joinTag.getString("OwnerNodeId"));
                String completedPortName = joinTag.getString("CompletedPortName");
                String joinId = joinTag.getString("JoinId");
                if (ownerNodeId == -1 || joinId == null || joinId.isBlank() || completedPortName == null || completedPortName.isBlank()) {
                    continue;
                }

                GraphProcess.VariableScope regScope = new GraphProcess.VariableScope(exactSize);
                loadVariablesFromTag(joinTag.getCompound("Registers"), regScope.statics, regScope, index, provider);

                Map<String, Object> tempData = new HashMap<>();
                if (joinTag.contains("TempData", Tag.TAG_COMPOUND)) {
                    CompoundTag tempTag = joinTag.getCompound("TempData");
                    for (String key : tempTag.getAllKeys()) {
                        Object obj = VariableRegistry.fromTag(tempTag.get(key), provider);
                        if (obj != null) {
                            tempData.put(key, obj);
                        }
                    }
                }

                GraphProcess.BranchJoin join = new GraphProcess.BranchJoin(
                        joinId,
                        ownerNodeId,
                        completedPortName,
                        regScope.statics,
                        regScope.dynamics,
                        tempData
                );
                join.pendingChildren = Math.max(0, joinTag.getInt("PendingChildren"));
                join.launchFinished = joinTag.getBoolean("LaunchFinished");
                process.addBranchJoinForSerialization(join);
            }
        }

        return process;
    }

    private static void saveVariablesToTag(CompoundTag tag, Object[] statics, Map<String, Object> dynamics, RuntimeGraphIndex index, HolderLookup.Provider provider) {
        for (int i = 0; i < statics.length; i++) {
            if (statics[i] != null) {
                String key = index.getKeyFromId(i);
                Tag s = VariableRegistry.toTag(statics[i], provider);
                if (s != null && key != null) tag.put(key, s);
            }
        }
        if (dynamics != null) {
            for (Map.Entry<String, Object> entry : dynamics.entrySet()) {
                Tag s = VariableRegistry.toTag(entry.getValue(), provider);
                if (s != null) tag.put(entry.getKey(), s);
            }
        }
    }

    private static void loadVariablesFromTag(CompoundTag tag, Object[] statics, GraphProcess.VariableScope scope, RuntimeGraphIndex index, HolderLookup.Provider provider) {
        for (String key : tag.getAllKeys()) {
            Object obj = VariableRegistry.fromTag(tag.get(key), provider);
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
            for (GraphProcess process : container.getProcessesMap().values()) {
                CompoundTag processTag = new CompoundTag();
                save(process, processTag, provider);
                processList.add(processTag);
            }
            tag.put("ActiveProcesses", processList);
        }
        if (!container.getAttributesMap().isEmpty()) {
            CompoundTag attrTag = new CompoundTag();
            for (Map.Entry<String, Object> entry : container.getAttributesMap().entrySet()) {
                Tag t = VariableRegistry.toTag(entry.getValue(), provider);
                if (t != null) attrTag.put(entry.getKey(), t);
            }
            tag.put("Attributes", attrTag);
        }
        return tag;
    }

    public static void loadContainer(GraphContainer container, CompoundTag tag, HolderLookup.Provider provider) {
        container.getProcessesMap().clear();
        if (tag.contains("ActiveProcesses", Tag.TAG_LIST)) {
            ListTag list = tag.getList("ActiveProcesses", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag pTag = list.getCompound(i);
                String graphId = pTag.getString("GraphId");
                RuntimeGraphIndex index = GraphEngine.getGraphIndex(graphId);
                if (index != null) {
                    container.getProcessesMap().put(graphId, load(pTag, index, provider));
                }
            }
        }

        container.getAttributesMap().clear();
        if (tag.contains("Attributes", Tag.TAG_COMPOUND)) {
            CompoundTag attrTag = tag.getCompound("Attributes");
            for (String key : attrTag.getAllKeys()) {
                Object obj = VariableRegistry.fromTag(attrTag.get(key), provider);
                if (obj != null) container.getAttributesMap().put(key, obj);
            }
        }
    }
}
