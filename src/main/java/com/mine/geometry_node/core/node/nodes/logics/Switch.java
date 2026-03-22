package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.nodes.*;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Switch extends BaseNode {

    public static final String TYPE_ID = "flow_switch";

    @Override
    public NodeDef getDefaultDefinition() {

        return buildDef(List.of(1));
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        List<Integer> branchIndices = instanceData.execution.keySet().stream()
                .filter(key -> key.startsWith("flow_out_"))
                .map(key -> {
                    try {
                        return Integer.parseInt(key.substring("flow_out_".length()));
                    } catch (NumberFormatException e) {
                        return -1;
                    }
                })
                .filter(i -> i > 0)
                .sorted()
                .collect(Collectors.toList());

        if (branchIndices.isEmpty()) branchIndices.add(1);

        return buildDef(branchIndices);
    }

    private NodeDef buildDef(List<Integer> branchIndices) {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL, Component.translatable("geometry_node.node.flow_switch"));

        // 输入执行流
        builder.addRow(new PortRow(
                StandardPorts.FLOW_IN.toExec(),
                null,
                UIHint.DEFAULT, null, null
        ));

        // 动态行
        for (Integer index : branchIndices) {
            builder.addRow(new PortRow(
                    StandardPorts.CASE.toInputWithIndex(index, false),
                    StandardPorts.FLOW_OUT.toExecWithIndex(index),
                    UIHint.CHECKBOX,
                    null,
                    Map.of("is_dynamic", true)
            ));
        }

        return builder.build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<String> activePorts = new ArrayList<>();
        int i = 1;

        while (true) {
            String casePort = StandardPorts.CASE.getIdWithIndex(i);
            String flowPort = StandardPorts.FLOW_OUT.getIdWithIndex(i);

            if (!context.hasPort(casePort)) {
                break;
            }

            Boolean isTrigger = getInput(context, casePort, Boolean.class);
            if (Boolean.TRUE.equals(isTrigger)) {
                activePorts.add(flowPort);
            }

            i++;
        }

        return activePorts.isEmpty() ? ExecutionResult.finish() : ExecutionResult.call(activePorts);
    }
}