package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Switch extends BaseNode {

    public static final String TYPE_ID = "switch";

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

        if (instanceData != null && instanceData.inputs.containsKey(StaticKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id())) {
            Object countObj = instanceData.inputs.get(StaticKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id());
            if (countObj instanceof Number) {
                branchCount = ((Number) countObj).intValue();
            } else if (countObj instanceof String) {
                try {
                    branchCount = Integer.parseInt((String) countObj);
                } catch (NumberFormatException ignored) {}
            }
        }
        branchCount = Math.max(1, Math.min(branchCount, MAX_BRANCH_COUNT));
        return buildDef(branchCount);
    }

    private NodeDef buildDef(int branchCount) {
        NodeComment.Builder comment = NodeComment.builder(TYPE_ID)
                .text("summary")
                .input(StandardPorts.FLOW_IN, "flow_in");
        for (int i = 1; i <= branchCount; i++) {
            comment.output(StandardPorts.FLOW_OUT.getIdWithIndex(i), "flow_out")
                    .input(StandardPorts.CASE.getIdWithIndex(i), "case");
        }

        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL, Component.translatable("geometry_node.node.switch"))
                .comment(comment.build());

        builder.addMeta(SchemaKeys.MAX_DYNAMIC_OUTPUT, MAX_BRANCH_COUNT);
        builder.addRow(new PortRow(
                StandardPorts.FLOW_IN.toExec(),
                null,
                UIHint.DEFAULT, null, null
        ));

        for (int i = 1; i <= branchCount; i++) {
            builder.addRow(new PortRow(
                    null,
                    StandardPorts.FLOW_OUT.toExecWithIndex(i),
                    UIHint.DEFAULT, null, null
            ));

            builder.addRow(new PortRow(
                    StandardPorts.CASE.toInputWithIndex(i, false),
                    null,
                    UIHint.CHECKBOX,
                    null,
                    Map.of(PortMetaKeys.IS_DYNAMIC, true,
                            PortMetaKeys.DYNAMIC_INDEX, i)
            ));
        }

        return builder.build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<String> activePorts = new ArrayList<>();

        int branchCount = DEFAULT_BRANCH_COUNT;
        Object countProp = context.getStaticInput(StaticKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id());
        if (countProp instanceof Number) {
            branchCount = ((Number) countProp).intValue();
        } else if (countProp instanceof String) {
            try { branchCount = Integer.parseInt((String) countProp); } catch (Exception ignored) {}
        }

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
