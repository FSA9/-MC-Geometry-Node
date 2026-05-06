package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class ForLoop extends BaseNode {

    public static final String TYPE_ID = "for_loop";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL, Component.translatable("geometry_node.node.for_loop"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.LOOP.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.COMPLETED.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.INDEX.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.MIN_INT.toInput(0), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.MAX_INT.toInput(0), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.TICK.toInput(), null, UIHint.INPUT, null, null))
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

        Object savedCursorObj = context.getTempData(cursorKey);
        int currentIndex = (savedCursorObj instanceof Integer i) ? i : start;

        if ((start <= end && currentIndex > end) || (start > end && currentIndex < end)) {
            context.removeTempData(indexKey);
            context.removeTempData(cursorKey);
            return next(StandardPorts.COMPLETED.getId());
        }

        int delay = (tickInterval != null) ? tickInterval : 0;
        int step = start <= end ? 1 : -1; // 根据起始和结束大小自动决定步长

        if (delay > 0) { // 异步跨帧模式
            context.setTempData(indexKey, currentIndex);
            context.clearFrameCache();
            context.setTempData(cursorKey, currentIndex + step);
            context.scheduleNode(myNodeId, delay);
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

    @Override
    @Nullable
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.INDEX.getId().equals(portName)) {
            return context.getTempData("ForLoop_" + context.getCurrentNodeId() + "_index");
        }
        return null;
    }
}