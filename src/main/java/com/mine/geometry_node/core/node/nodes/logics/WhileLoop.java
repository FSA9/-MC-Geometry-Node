package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class WhileLoop extends BaseNode {

    public static final String TYPE_ID = "while_loop";

    private static final String INTERNAL_LOOP_TICK = "internal_loop_tick";
    private static final int DEFAULT_TICK_INTERVAL = 1;
    private static final int MAX_SYNC_ITERATIONS = 10_000;

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL, Component.translatable("geometry_node.node.while_loop"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.LOOP, "loop")
                        .output(StandardPorts.COMPLETED, "completed")
                        .output(StandardPorts.ITERATION, "iteration")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.CONDITION, "condition")
                        .input(StandardPorts.TICK, "tick")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.LOOP.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.COMPLETED.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ITERATION.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.CONDITION.toInput(false), UIHint.CHECKBOX, null, null)
                .addPassthroughInput(StandardPorts.TICK.toInput(DEFAULT_TICK_INTERVAL), UIHint.INPUT, null, Map.of(PortMetaKeys.NUMERIC_MIN, 0))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        int nodeId = context.getCurrentNodeId();
        String iterationKey = ExecutionContext.nodeResultKey(nodeId, StandardPorts.ITERATION.getId());
        String cursorKey = tempKey(nodeId, "cursor");
        boolean internalTick = INTERNAL_LOOP_TICK.equals(context.getEntryPort());
        int currentIteration = internalTick ? readIteration(context.getTempData(cursorKey)) : 0;

        context.setNodeResult(StandardPorts.ITERATION.getId(), currentIteration);
        Integer configuredTick = getInput(context, StandardPorts.TICK.getId(), Integer.class);
        int tickInterval = Math.max(0, configuredTick != null ? configuredTick : DEFAULT_TICK_INTERVAL);

        if (tickInterval > 0) {
            return executeScheduledIteration(context, nodeId, iterationKey, cursorKey, currentIteration, tickInterval);
        }
        return executeSynchronousLoop(context, iterationKey, cursorKey, currentIteration);
    }

    private ExecutionResult executeScheduledIteration(ExecutionContext context,
                                                       int nodeId,
                                                       String iterationKey,
                                                       String cursorKey,
                                                       int currentIteration,
                                                       int tickInterval) {
        if (!conditionIsTrue(context)) {
            clearState(context, iterationKey, cursorKey);
            return next(StandardPorts.COMPLETED.getId());
        }

        context.setTempData(cursorKey, currentIteration + 1);
        context.scheduleNode(nodeId, tickInterval, INTERNAL_LOOP_TICK);
        return next(StandardPorts.LOOP.getId());
    }

    private ExecutionResult executeSynchronousLoop(ExecutionContext context,
                                                    String iterationKey,
                                                    String cursorKey,
                                                    int startIteration) {
        int currentIteration = startIteration;
        int executedIterations = 0;

        while (true) {
            context.setNodeResult(StandardPorts.ITERATION.getId(), currentIteration);
            if (!conditionIsTrue(context)) {
                clearState(context, iterationKey, cursorKey);
                return next(StandardPorts.COMPLETED.getId());
            }
            if (executedIterations >= MAX_SYNC_ITERATIONS) {
                clearState(context, iterationKey, cursorKey);
                String message = "WhileLoop exceeded the synchronous iteration limit of " + MAX_SYNC_ITERATIONS;
                System.err.println("[WhileLoop] " + message);
                return ExecutionResult.error(message);
            }
            context.setTempData(cursorKey, currentIteration + 1);
            context.executeBranchSync(StandardPorts.LOOP.getId());
            currentIteration++;
            executedIterations++;
        }
    }

    private boolean conditionIsTrue(ExecutionContext context) {
        Boolean condition = getInput(context, StandardPorts.CONDITION.getId(), Boolean.class);
        return Boolean.TRUE.equals(condition);
    }

    private void clearState(ExecutionContext context, String iterationKey, String cursorKey) {
        context.removeTempData(iterationKey);
        context.removeTempData(cursorKey);
    }

    private int readIteration(@Nullable Object value) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value instanceof String text) {
            try {
                return Math.max(0, Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private String tempKey(int nodeId, String suffix) {
        return "WhileLoop_" + nodeId + "_" + suffix;
    }

    @Override
    @Nullable
    public Object compute(GraphDataContext context, String portName) {
        if (StandardPorts.ITERATION.getId().equals(portName)) {
            return context.getNodeResult(portName);
        }
        return null;
    }
}
