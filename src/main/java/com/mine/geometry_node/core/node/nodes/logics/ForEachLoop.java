package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ForEachLoop extends BaseNode {

    public static final String TYPE_ID = "for_each_loop";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL, Component.translatable("geometry_node.node.for_each_loop"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.LOOP.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.COMPLETED.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.INDEX.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ANY_VALUE.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.LIST.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.LIMIT.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.TICK.toInput(), null, UIHint.INPUT, null, null))
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
        String indexKey = "ForEach_" + myNodeId + "_index";
        String elementKey = "ForEach_" + myNodeId + "_element";
        String cursorKey = "ForEach_" + myNodeId + "_cursor";

        boolean isInternalTick = "internal_loop_tick".equals(context.getEntryPort());

        int currentIndex;
        if (isInternalTick) {
            // 如果是内部延迟唤醒，继续读取游标进度
            Object savedCursorObj = context.getTempData(cursorKey);
            currentIndex = (savedCursorObj instanceof Integer i) ? i : 0;
        } else {
            // 如果是外部重新触发，强行从头开始！
            currentIndex = 0;
        }

        // 结束条件判断
        if (currentIndex >= targetIterations) {
            context.removeTempData(indexKey);
            context.removeTempData(elementKey);
            context.removeTempData(cursorKey);
            return next(StandardPorts.COMPLETED.getId());
        }

        int delay = (tickInterval != null) ? tickInterval : 0;

        if (delay > 0) { // --- 异步跨帧模式 ---
            Object currentElement = list.get(currentIndex);

            context.setTempData(indexKey, currentIndex);
            context.setTempData(elementKey, currentElement);
            context.clearFrameCache();
            context.setTempData(cursorKey, currentIndex + 1);

            // 唤醒自己
            context.scheduleNode(myNodeId, delay, "internal_loop_tick");
            return next(StandardPorts.LOOP.getId());

        } else { // --- 瞬间同步模式 ---
            for (int i = currentIndex; i < targetIterations; i++) {
                Object currentElement = list.get(i);
                context.setTempData(indexKey, i);
                context.setTempData(elementKey, currentElement);
                context.clearFrameCache();

                context.executeBranchSync(StandardPorts.LOOP.getId());
            }

            context.removeTempData(indexKey);
            context.removeTempData(elementKey);
            context.removeTempData(cursorKey);
            return next(StandardPorts.COMPLETED.getId());
        }
    }

    @Override
    @Nullable
    public Object compute(ExecutionContext context, String portName) {
        int myNodeId = context.getCurrentNodeId();
        if (StandardPorts.INDEX.getId().equals(portName)) {
            return context.getTempData("ForEach_" + myNodeId + "_index");
        }
        if (StandardPorts.ANY_VALUE.getId().equals(portName)) {
            return context.getTempData("ForEach_" + myNodeId + "_element");
        }
        return null;
    }
}