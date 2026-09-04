package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ForEachLoop extends BaseNode {

    public static final String TYPE_ID = "for_each_loop";
    private static final String COMPLETED_POLICY = "completed_policy";
    private static final String POLICY_SCHEDULED = "scheduled";
    private static final String POLICY_JOINED = "joined";
    private static final String INTERNAL_LOOP_TICK = "internal_loop_tick";
    private static final String INTERNAL_BRANCH_COMPLETE = "internal_branch_complete";
    private static final String[] COMPLETED_POLICY_OPTIONS = { POLICY_SCHEDULED, POLICY_JOINED };

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL, Component.translatable("geometry_node.node.for_each_loop"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.LOOP, "loop")
                        .output(StandardPorts.COMPLETED, "completed")
                        .output(StandardPorts.INDEX, "index")
                        .output(StandardPorts.ANY_VALUE, "any_value")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.LIST, "list")
                        .input(StandardPorts.LIMIT, "limit")
                        .input(StandardPorts.TICK, "tick")
                        .input(COMPLETED_POLICY, "completed_policy")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.LOOP.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.COMPLETED.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.INDEX.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ANY_VALUE.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.LIST.toInput(), UIHint.DEFAULT, null, null)
                .addPassthroughInput(StandardPorts.LIMIT.toInput(), UIHint.INPUT, null, null)
                .addPassthroughInput(StandardPorts.TICK.toInput(), UIHint.INPUT, null, Map.of(PortMetaKeys.NUMERIC_MIN, 0))
                .addPassthroughInput(PortDef.create(COMPLETED_POLICY, "geometry_node.port.completed_policy", PortType.STRING, POLICY_SCHEDULED).hiddenPin(), UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, COMPLETED_POLICY_OPTIONS))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<?> list = getInputList(context, StandardPorts.LIST.getId(), Object.class);
        Integer limitInt = getInput(context, StandardPorts.LIMIT.getId(), Integer.class);
        Integer tickInterval = getInput(context, StandardPorts.TICK.getId(), Integer.class);

        int listSize = list != null ? list.size() : 0;
        int targetIterations = (limitInt != null && limitInt > 0) ? Math.min(listSize, limitInt) : listSize;

        int myNodeId = context.getCurrentNodeId();
        String indexKey = ExecutionContext.nodeResultKey(myNodeId, StandardPorts.INDEX.getId());
        String elementKey = ExecutionContext.nodeResultKey(myNodeId, StandardPorts.ANY_VALUE.getId());
        String cursorKey = "ForEach_" + myNodeId + "_cursor";

        boolean isInternalTick = INTERNAL_LOOP_TICK.equals(context.getEntryPort())
                || INTERNAL_BRANCH_COMPLETE.equals(context.getEntryPort());

        int currentIndex;
        if (isInternalTick) {
            // 如果是内部延迟唤醒，继续读取游标进度
            Object savedCursorObj = context.getTempData(cursorKey);
            currentIndex = (savedCursorObj instanceof Integer i) ? i : 0;
        } else {
            // 如果是外部重新触发，强行从头开始！
            currentIndex = 0;
        }

        String completedPolicy = getInput(context, COMPLETED_POLICY, String.class);
        boolean waitForBranches = POLICY_JOINED.equals(completedPolicy);

        // 结束条件判断
        if (currentIndex >= targetIterations) {
            if (waitForBranches) {
                return finishJoinedLoop(context, myNodeId, indexKey, elementKey, cursorKey);
            }
            context.removeTempData(indexKey);
            context.removeTempData(elementKey);
            context.removeTempData(cursorKey);
            return next(StandardPorts.COMPLETED.getId());
        }

        int delay = (tickInterval != null) ? tickInterval : 0;

        if (waitForBranches) {
            return executeJoinedLoop(context, list, myNodeId, indexKey, elementKey, cursorKey, currentIndex, targetIterations, delay);
        }

        if (delay > 0) { // --- 异步跨帧模式 ---
            Object currentElement = list.get(currentIndex);

            context.setNodeResult(StandardPorts.INDEX.getId(), currentIndex);
            context.setNodeResult(StandardPorts.ANY_VALUE.getId(), currentElement);
            context.setTempData(cursorKey, currentIndex + 1);

            // 唤醒自己
            context.scheduleNode(myNodeId, delay, INTERNAL_LOOP_TICK);
            return next(StandardPorts.LOOP.getId());
        }

        Object currentElement = list.get(currentIndex);
        context.setNodeResult(StandardPorts.INDEX.getId(), currentIndex);
        context.setNodeResult(StandardPorts.ANY_VALUE.getId(), currentElement);
        context.setTempData(cursorKey, currentIndex + 1);
        if (context.executeBranchThenResume(StandardPorts.LOOP.getId(), INTERNAL_BRANCH_COMPLETE)) {
            return finish();
        }
        context.removeTempData(indexKey);
        context.removeTempData(elementKey);
        context.removeTempData(cursorKey);
        return next(StandardPorts.COMPLETED.getId());
    }

    private ExecutionResult executeJoinedLoop(ExecutionContext context,
                                              List<?> list,
                                              int myNodeId,
                                              String indexKey,
                                              String elementKey,
                                              String cursorKey,
                                              int currentIndex,
                                              int targetIterations,
                                              int delay) {
        String joinKey = "ForEach_" + myNodeId + "_join";
        Object joinObj = context.getTempData(joinKey);
        String joinId = joinObj instanceof String existingJoin ? existingJoin : null;
        if (joinId == null) {
            joinId = context.createBranchJoin(StandardPorts.COMPLETED.getId());
            context.setTempData(joinKey, joinId);
        }

        if (delay > 0) {
            Object currentElement = list.get(currentIndex);
            context.spawnBranch(StandardPorts.LOOP.getId(), branchTempData(indexKey, currentIndex, elementKey, currentElement), joinId);
            context.setTempData(cursorKey, currentIndex + 1);
            context.scheduleNode(myNodeId, delay, "internal_loop_tick");
            return finish();
        }

        for (int i = currentIndex; i < targetIterations; i++) {
            context.spawnBranch(StandardPorts.LOOP.getId(), branchTempData(indexKey, i, elementKey, list.get(i)), joinId);
        }
        context.removeTempData(indexKey);
        context.removeTempData(elementKey);
        context.removeTempData(cursorKey);
        context.removeTempData(joinKey);
        context.finishBranchJoin(joinId);
        return finish();
    }

    private ExecutionResult finishJoinedLoop(ExecutionContext context, int myNodeId, String indexKey, String elementKey, String cursorKey) {
        String joinKey = "ForEach_" + myNodeId + "_join";
        Object joinObj = context.getTempData(joinKey);
        if (joinObj instanceof String joinId) {
            context.removeTempData(indexKey);
            context.removeTempData(elementKey);
            context.removeTempData(cursorKey);
            context.removeTempData(joinKey);
            context.finishBranchJoin(joinId);
            return finish();
        }
        context.removeTempData(indexKey);
        context.removeTempData(elementKey);
        context.removeTempData(cursorKey);
        return next(StandardPorts.COMPLETED.getId());
    }

    private Map<String, Object> branchTempData(String indexKey, int index, String elementKey, Object element) {
        Map<String, Object> tempData = new HashMap<>();
        tempData.put(indexKey, index);
        tempData.put(elementKey, GraphValueSnapshot.snapshot(element));
        return tempData;
    }

    @Override
    @Nullable
    public Object compute(GraphDataContext context, String portName) {
        if (StandardPorts.INDEX.getId().equals(portName)) {
            return context.getNodeResult(portName);
        }
        if (StandardPorts.ANY_VALUE.getId().equals(portName)) {
            return context.getNodeResult(portName);
        }
        return null;
    }
}
