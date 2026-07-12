package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class CombineFlow extends BaseNode {

    public static final String TYPE_ID = "combine_flow";

    private static final int DEFAULT_BRANCH_COUNT = 2;
    private static final int MAX_BRANCH_COUNT = 10;

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDef(DEFAULT_BRANCH_COUNT);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        int branchCount = DEFAULT_BRANCH_COUNT;

        if (instanceData != null && instanceData.inputs.containsKey(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id())) {
            Object countObj = instanceData.inputs.get(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
            if (countObj instanceof Number n) {
                branchCount = n.intValue();
            } else if (countObj instanceof String s) {
                try {
                    branchCount = Integer.parseInt(s);
                } catch (NumberFormatException ignored) {}
            }
        }
        // 限制范围
        branchCount = Math.max(2, Math.min(branchCount, MAX_BRANCH_COUNT));
        return buildDef(branchCount);
    }

    private NodeDef buildDef(int branchCount) {
        String comment = """
                将多个执行流入口合并到一个执行流出口。
                任意 flow_in 输入触发时，都会继续执行同一个 flow_out。
                输入分支数量可动态增加，最多 10 个。""";

        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL, Component.translatable("geometry_node.node.combine_flow"))
                .comment(comment);
        builder.addMeta(SchemaKeys.MAX_DYNAMIC_INPUT, MAX_BRANCH_COUNT);
        builder.addRow(new PortRow(
                null,
                StandardPorts.FLOW_OUT.toExec(),
                UIHint.DEFAULT, null, null
        ));
        for (int i = 1; i <= branchCount; i++) {
            builder.addRow(new PortRow(
                    StandardPorts.FLOW_IN.toExecWithIndex(i),
                    null,
                    UIHint.DEFAULT,
                    null,
                    Map.of(
                            PortMetaKeys.IS_DYNAMIC, true,
                            PortMetaKeys.DYNAMIC_INDEX, i
                    )
            ));
        }

        return builder.build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
