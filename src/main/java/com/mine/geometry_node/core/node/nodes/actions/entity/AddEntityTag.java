package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.StandardPorts;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public class AddEntityTag extends BaseNode {

    public static final String TYPE_ID = "add_entity_tag";

    @Override
    public NodeDef getDefaultDefinition() {
        return getDefinition(null);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
//        int portCount = 1;
//        if (instanceData != null && instanceData.properties.containsKey(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id())) {
//            Object countObj = instanceData.properties.get(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
//            if (countObj instanceof Number n) {
//                portCount = Math.max(1, n.intValue());
//            } else if (countObj instanceof String s) {
//                try { portCount = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
//            }
//        }
//        portCount = Math.max(1, Math.min(portCount, 30));
//
//        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.add_entity_tag"))
//                .addMeta(SchemaKeys.MAX_DYNAMIC_INPUT, 30);
//
//        // 1. 执行流
//        builder.addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null));
//        // 2. 目标实体
//        builder.addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null));
//
//        // 3. 动态标签输入行
//        for (int i = 1; i <= portCount; i++) {
//            PortDef tagPort = new PortDef("tag_" + i, Component.literal("Tag " + i), PortType.STRING, "");
//            builder.addRow(new PortRow(
//                    tagPort,
//                    null,
//                    UIHint.INPUT, // 允许玩家直接在节点上打字输入标签名
//                    null,
//                    Map.of(
//                            PortMetaKeys.IS_DYNAMIC, true,
//                            PortMetaKeys.DYNAMIC_INDEX, i
//                    )
//            ));
//        }

//        return builder.build();
        return null;
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);

        // 提取所有动态输入的标签
        List<String> tagsToAdd = new ArrayList<>();
        int portCount = 1;
        Object countObj = context.getStaticInput(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
        if (countObj instanceof Number n) portCount = Math.max(1, n.intValue());

        for (int i = 1; i <= portCount; i++) {
            String tag = getInput(context, "tag_" + i, String.class);
            if (tag != null && !tag.trim().isEmpty()) {
                tagsToAdd.add(tag.trim());
            }
        }

        // 核心原版方法调用：批量添加
        if (!entities.isEmpty() && !tagsToAdd.isEmpty()) {
            for (Entity entity : entities) {
                for (String tag : tagsToAdd) {
                    entity.addTag(tag);
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}