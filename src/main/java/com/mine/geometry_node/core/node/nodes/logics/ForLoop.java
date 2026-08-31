package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
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

import java.util.Map;

public class ForLoop extends BaseNode {

    public static final String TYPE_ID = "for_loop";
    private static final String COMPLETED_POLICY = "completed_policy";
    private static final String POLICY_SCHEDULED = "scheduled";
    private static final String POLICY_JOINED = "joined";
    private static final String[] COMPLETED_POLICY_OPTIONS = { POLICY_SCHEDULED, POLICY_JOINED };

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL, Component.translatable("geometry_node.node.for_loop"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.LOOP, "loop")
                        .output(StandardPorts.COMPLETED, "completed")
                        .output(StandardPorts.INDEX, "index")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.MIN_INT, "min_int")
                        .input(StandardPorts.MAX_INT, "max_int")
                        .input(StandardPorts.TICK, "tick")
                        .input(COMPLETED_POLICY, "completed_policy")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.LOOP.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.COMPLETED.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.INDEX.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.MIN_INT.toInput(0), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.MAX_INT.toInput(0), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.TICK.toInput(), null, UIHint.INPUT, null,
                        Map.of(PortMetaKeys.NUMERIC_MIN, 0)))
                .addRow(new PortRow(
                        PortDef.create(COMPLETED_POLICY, "geometry_node.port.completed_policy", PortType.STRING, POLICY_SCHEDULED).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, COMPLETED_POLICY_OPTIONS)
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Integer startInt = getInput(context, StandardPorts.MIN_INT.getId(), Integer.class);
        Integer endInt = getInput(context, StandardPorts.MAX_INT.getId(), Integer.class);
        Integer tickInterval = getInput(context, StandardPorts.TICK.getId(), Integer.class);

        int start = startInt != null ? startInt : 0;
        int end = endInt != null ? endInt : 0;

        int myNodeId = context.getCurrentNodeId();
        String indexKey = "ForLoop_" + myNodeId + "_index";
        String cursorKey = "ForLoop_" + myNodeId + "_cursor";

        // ✨ 关键修复点 1：通过进入端口，判断是谁触发了当前节点
        boolean isInternalTick = "internal_loop_tick".equals(context.getEntryPort());

        int currentIndex;
        if (isInternalTick) {
            // 是内部延迟到期唤醒的，接着读取上次保存的游标
            Object savedCursorObj = context.getTempData(cursorKey);
            currentIndex = (savedCursorObj instanceof Integer i) ? i : start;
        } else {
            // 是外部新触发的（比如重新放置方块），强行清零，从头开始
            currentIndex = start;
        }

        String completedPolicy = getInput(context, COMPLETED_POLICY, String.class);
        boolean waitForBranches = POLICY_JOINED.equals(completedPolicy);

        if ((start <= end && currentIndex > end) || (start > end && currentIndex < end)) {
            if (waitForBranches) {
                return finishJoinedLoop(context, myNodeId, indexKey, cursorKey);
            }
            context.removeTempData(indexKey);
            context.removeTempData(cursorKey);
            return next(StandardPorts.COMPLETED.getId());
        }

        int delay = (tickInterval != null) ? tickInterval : 0;
        int step = start <= end ? 1 : -1;

        if (waitForBranches) {
            return executeJoinedLoop(context, myNodeId, indexKey, cursorKey, currentIndex, end, step, delay);
        }

        if (delay > 0) { // 异步跨帧模式
            context.setTempData(indexKey, currentIndex);
            context.clearFrameCache();
            context.setTempData(cursorKey, currentIndex + step);

            context.scheduleNode(myNodeId, delay, "internal_loop_tick");
            return next(StandardPorts.LOOP.getId());
        } else { // 瞬间同步模式
            int iterations = 0;
            for (int i = currentIndex; (step > 0 ? i <= end : i >= end); i += step) {
                if (iterations++ > 10000) break;
                context.setTempData(indexKey, i);
                context.clearFrameCache();
                context.executeBranchSync(StandardPorts.LOOP.getId());
            }
            context.removeTempData(indexKey);
            context.removeTempData(cursorKey);
            return next(StandardPorts.COMPLETED.getId());
        }
    }

    private ExecutionResult executeJoinedLoop(ExecutionContext context,
                                              int myNodeId,
                                              String indexKey,
                                              String cursorKey,
                                              int currentIndex,
                                              int end,
                                              int step,
                                              int delay) {
        String joinKey = "ForLoop_" + myNodeId + "_join";
        Object joinObj = context.getTempData(joinKey);
        String joinId = joinObj instanceof String existingJoin ? existingJoin : null;
        if (joinId == null) {
            joinId = context.createBranchJoin(StandardPorts.COMPLETED.getId());
            context.setTempData(joinKey, joinId);
        }

        if (delay > 0) {
            context.spawnBranch(StandardPorts.LOOP.getId(), Map.of(indexKey, currentIndex), joinId);
            context.setTempData(cursorKey, currentIndex + step);
            context.scheduleNode(myNodeId, delay, "internal_loop_tick");
            return finish();
        }

        for (int i = currentIndex; (step > 0 ? i <= end : i >= end); i += step) {
            context.spawnBranch(StandardPorts.LOOP.getId(), Map.of(indexKey, i), joinId);
        }
        context.removeTempData(indexKey);
        context.removeTempData(cursorKey);
        context.removeTempData(joinKey);
        context.finishBranchJoin(joinId);
        return finish();
    }

    private ExecutionResult finishJoinedLoop(ExecutionContext context, int myNodeId, String indexKey, String cursorKey) {
        String joinKey = "ForLoop_" + myNodeId + "_join";
        Object joinObj = context.getTempData(joinKey);
        if (joinObj instanceof String joinId) {
            context.removeTempData(indexKey);
            context.removeTempData(cursorKey);
            context.removeTempData(joinKey);
            context.finishBranchJoin(joinId);
            return finish();
        }
        context.removeTempData(indexKey);
        context.removeTempData(cursorKey);
        return next(StandardPorts.COMPLETED.getId());
    }

    @Override
    @Nullable
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.INDEX.getId().equals(portName)) {
            return context.getTempData("ForLoop_" + context.getCurrentNodeId() + "_index");
        }
        return null;
    }
}
