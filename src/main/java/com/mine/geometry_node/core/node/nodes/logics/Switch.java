package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.PropertyKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Switch extends BaseNode {

    public static final String TYPE_ID = "flow_switch";

    // 默认数量与最大上限
    private static final int DEFAULT_BRANCH_COUNT = 1;
    private static final int MAX_BRANCH_COUNT = 10;

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDef(DEFAULT_BRANCH_COUNT);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        int branchCount = DEFAULT_BRANCH_COUNT;

        // 核心修改：不再遍历 execution，而是直接读取我们保存的属性状态
        if (instanceData != null && instanceData.properties.containsKey(PropertyKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id())) {
            Object countObj = instanceData.properties.get(PropertyKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id());
            // 兼容强转防御：防止从某些 JSON 库反序列化回来后变成了 String
            if (countObj instanceof Number) {
                branchCount = ((Number) countObj).intValue();
            } else if (countObj instanceof String) {
                try {
                    branchCount = Integer.parseInt((String) countObj);
                } catch (NumberFormatException ignored) {}
            }
        }

        // 兜底保护，防止 JSON 被外部恶意篡改导致越界
        branchCount = Math.max(1, Math.min(branchCount, MAX_BRANCH_COUNT));
        return buildDef(branchCount);
    }

    private NodeDef buildDef(int branchCount) {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL, Component.translatable("geometry_node.node.flow_switch"));

        builder.addMeta(SchemaKeys.MAX_DYNAMIC_OUTPUT, MAX_BRANCH_COUNT);

        // 1. 固定输入执行流
        builder.addRow(new PortRow(
                StandardPorts.FLOW_IN.toExec(),
                null,
                UIHint.DEFAULT, null, null
        ));

        // 2. 根据记录的数量动态生成行
        for (int i = 1; i <= branchCount; i++) {
            builder.addRow(new PortRow(
                    StandardPorts.CASE.toInputWithIndex(i, false),
                    StandardPorts.FLOW_OUT.toExecWithIndex(i),
                    UIHint.CHECKBOX,
                    null,
                    Map.of(PortMetaKeys.IS_DYNAMIC, true)
            ));
        }

        return builder.build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<String> activePorts = new ArrayList<>();

        // 运行时同样读取数量，不再死循环猜测
        int branchCount = DEFAULT_BRANCH_COUNT;
        Object countProp = context.getNodeProperty(PropertyKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id());
        if (countProp instanceof Number) {
            branchCount = ((Number) countProp).intValue();
        } else if (countProp instanceof String) {
            try { branchCount = Integer.parseInt((String) countProp); } catch (Exception ignored) {}
        }

        // 严格按照生成的端口数量进行数据推断
        for (int i = 1; i <= branchCount; i++) {
            String casePort = StandardPorts.CASE.getIdWithIndex(i);
            String flowPort = StandardPorts.FLOW_OUT.getIdWithIndex(i);

            Boolean isTrigger = getInput(context, casePort, Boolean.class);
            if (Boolean.TRUE.equals(isTrigger)) {
                activePorts.add(flowPort);
            }
        }

        return activePorts.isEmpty() ? ExecutionResult.finish() : ExecutionResult.call(activePorts);
    }
}