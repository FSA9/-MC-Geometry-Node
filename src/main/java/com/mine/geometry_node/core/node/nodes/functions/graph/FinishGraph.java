package com.mine.geometry_node.core.node.nodes.functions.graph;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintProcess;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;

public class FinishGraph extends BaseNode {

    public static final String TYPE_ID = "finish_graph";
    public static final String PROPERTY_SELECTED = PortMetaKeys.DYNAMIC_REGISTRY_ID.id();

    @Override
    public NodeDef getDefaultDefinition() {
//        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.finish_graph"))
//                // 执行流
//                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
//                // 第一行：作用域（使用维度下拉框逻辑，Key 必须全部为 MetaKey）
//                .addRow(new PortRow(
//                        null,
//                        null,
//                        UIHint.SELECT,
//                        null,
//                        Map.of(
//                                PortMetaKeys.BIND_PROPERTY, PROPERTY_SELECTED,
//                                PortMetaKeys.DYNAMIC_REGISTRY_ID, "minecraft:dimension"
//                        )
//                ))
//                // 第二行：实体
//                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
//                // 第三行：名称 (图 ID，填写时不带 .json)
//                .addRow(new PortRow(StandardPorts.NAME.toInput(""), null, UIHint.INPUT, null, null))
//                .build();
        return null;
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {


        return next(StandardPorts.FLOW_OUT.getId());
    }

    private void terminateMatchingProcesses(Iterable<BlueprintProcess> processes, String targetGraphId) {

    }
}