package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ForEach extends BaseNode {

    public static final String TYPE_ID = "for_each";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL, Component.translatable("geometry_node.node.for_each"))
                // 1. 输入执行流 -> 输出循环体执行流 (LOOP)
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.LOOP.toExec(), UIHint.DEFAULT, null, null))
                // 2. 无输入 -> 输出循环结束执行流 (COMPLETED)
                .addRow(new PortRow(null, StandardPorts.COMPLETED.toExec(), UIHint.DEFAULT, null, null))
                // 3. 无输入 -> 输出当前 Index
                .addRow(new PortRow(null, StandardPorts.INDEX.toOutput(), UIHint.DEFAULT, null, null))
                // 4. 无输入 -> 输出当前 Element (ANY)
                .addRow(new PortRow(null, StandardPorts.ANY_VALUE.toOutput(), UIHint.DEFAULT, null, null))
                // 5. 列表输入 -> 无输出
                .addRow(new PortRow(StandardPorts.LIST.toInput(), null, UIHint.DEFAULT, null, null))
                // 6. 循环最大次数输入 -> 无输出
                .addRow(new PortRow(StandardPorts.INT.toInput(), null, UIHint.INPUT, null, null))
                // 7. 循环间隔输入 -> 无输出 (预留给未来的异步协程)
                .addRow(new PortRow(StandardPorts.TICK.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<?> list = getInputList(context, StandardPorts.LIST.getId(), Object.class);
        Integer maxInt = getInput(context, StandardPorts.INT.getId(), Integer.class);
        Integer tickInterval = getInput(context, StandardPorts.TICK.getId(), Integer.class);

        int listSize = list.size();
        int targetIterations = (maxInt != null && maxInt > 0) ?
                (listSize == 0 ? maxInt : Math.min(listSize, maxInt)) : listSize;

        int myNodeId = context.getCurrentNodeId();
        // 对外暴露数据 Key
        String indexKey = "ForEach_" + myNodeId + "_index";
        String elementKey = "ForEach_" + myNodeId + "_element";
        // 对内专用状态机游标 Key
        String cursorKey = "ForEach_" + myNodeId + "_cursor";

        // 1. 读取内部状态机游标 (从 cursorKey 读，不影响 indexKey)
        Object savedCursorObj = context.getTempData(cursorKey);
        int currentIndex = (savedCursorObj instanceof Integer i) ? i : 0;

        // 2. 终止条件判定：循环结束，打扫战场
        if (currentIndex >= targetIterations) {
            context.removeTempData(indexKey);
            context.removeTempData(elementKey);
            context.removeTempData(cursorKey); // 记得清理游标
            return next(StandardPorts.COMPLETED.getId());
        }

        int delay = (tickInterval != null) ? tickInterval : 0;

        // 异步跨帧模式
        if (delay > 0) {
            Object currentElement = (currentIndex < listSize) ? list.get(currentIndex) : null;

            context.setTempData(indexKey, currentIndex);
            context.setTempData(elementKey, currentElement);
            context.clearFrameCache();

            context.setTempData(cursorKey, currentIndex + 1);

            context.scheduleNode(myNodeId, delay);

            return next(StandardPorts.LOOP.getId());
        }

        // 瞬间同步模式
        else {
            for (int i = currentIndex; i < targetIterations; i++) {
                Object currentElement = (i < listSize) ? list.get(i) : null;
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