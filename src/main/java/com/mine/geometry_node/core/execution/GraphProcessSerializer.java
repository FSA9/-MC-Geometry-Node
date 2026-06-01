package com.mine.geometry_node.core.execution;

import com.mine.geometry_node.core.execution.attachment.GraphContainer;
import com.mine.geometry_node.core.execution.variables.VariableRegistry;
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
        Iterator<GraphProcess.VariableScope> it = process.variableStack.descendingIterator();
        while (it.hasNext()) {
            CompoundTag scopeTag = new CompoundTag();
            GraphProcess.VariableScope scope = it.next();
            saveVariablesToTag(scopeTag, scope.statics, scope.dynamics, index, provider);
            stackTag.add(scopeTag);
        }
        tag.put("VariableStack", stackTag);

        // 2. 保存休眠线程
        if (!process.sleepingThreads.isEmpty()) {
            ListTag threadsTag = new ListTag();
            for (GraphProcess.ExecutionThread thread : process.sleepingThreads) {
                CompoundTag tTag = new CompoundTag();
                // 注意这里需要 process.getLevel()，我们需要在 GraphProcess 中暴露一下，或者直接判断
                long currentTime = (process.getLevel() != null) ? process.getLevel().getGameTime() : 0;
                long remaining = (process.getLevel() != null) ? Math.max(0, thread.wakeUpTick - currentTime) : thread.wakeUpTick;
                tTag.putLong("WaitRemaining", remaining);

                tTag.putInt("CurrentFlowId", thread.currentFlowId);
                if (thread.currentEntryPort != null) {
                    tTag.putString("CurrentEntryPort", thread.currentEntryPort);
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
                for (RuntimeGraphIndex.IntFlowTarget frame : thread.executionStack) {
                    CompoundTag frameTag = new CompoundTag();
                    frameTag.putString("TargetNodeId", index.getIdToString(frame.targetNodeId()));
                    frameTag.putString("TargetPortName", frame.targetPortName());
                    execStackTag.add(frameTag);
                }
                tTag.put("ExecutionStack", execStackTag);

                // 存线程寄存器 (静态 + 动态)
                CompoundTag regTag = new CompoundTag();
                saveVariablesToTag(regTag, thread.eventRegisters, thread.dynamicEventData, index, provider);
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
        return tag;
    }

    public static GraphProcess load(CompoundTag tag, RuntimeGraphIndex index, HolderLookup.Provider provider) {
        String graphId = tag.getString("GraphId");
        GraphProcess process = new GraphProcess(graphId, index);
        process.variableStack.clear(); // 清理构造函数默认放入的空栈

        int exactSize = index.getRegisterCount() + 8;

        // 1. 恢复变量栈
        if (tag.contains("VariableStack", Tag.TAG_LIST)) {
            ListTag list = tag.getList("VariableStack", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                GraphProcess.VariableScope scope = new GraphProcess.VariableScope(exactSize);
                loadVariablesFromTag(list.getCompound(i), scope.statics, scope, index, provider);
                process.variableStack.addLast(scope);
            }
        } else {
            process.variableStack.push(new GraphProcess.VariableScope(exactSize));
        }

        // 2. 恢复线程
        if (tag.contains("SleepingThreads", Tag.TAG_LIST)) {
            ListTag list = tag.getList("SleepingThreads", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tTag = list.getCompound(i);
                int currentFlowId = tTag.contains("CurrentFlowId") ? tTag.getInt("CurrentFlowId") : -1;
                String currentPort = tTag.getString("CurrentEntryPort");
                if (currentPort == null || currentPort.isEmpty()) currentPort = "flow_in";

                GraphProcess.ExecutionThread thread = process.new ExecutionThread(currentFlowId, currentPort);
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
                            thread.executionStack.add(new RuntimeGraphIndex.IntFlowTarget(targetId, portName));
                        }
                    }
                }

                if (currentFlowId != -1 || !thread.executionStack.isEmpty()) {
                    thread.wakeUpTick = tTag.getLong("WaitRemaining");
                    thread.state = GraphProcess.ExecutionThread.State.WAITING;

                    GraphProcess.VariableScope tempScope = new GraphProcess.VariableScope(exactSize);
                    loadVariablesFromTag(tTag.getCompound("Registers"), tempScope.statics, tempScope, index, provider);
                    thread.eventRegisters = tempScope.statics;
                    thread.dynamicEventData = tempScope.dynamics;

                    process.sleepingThreads.add(thread);

                    if (tTag.contains("TempData", Tag.TAG_COMPOUND)) {
                        CompoundTag tempTag = tTag.getCompound("TempData");
                        for (String key : tempTag.getAllKeys()) {
                            Object obj = VariableRegistry.fromTag(tempTag.get(key), provider);
                            if (obj != null) thread.tempData.put(key, obj);
                        }
                    }
                }
            }
            process.needsTimeRebase = true;
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
