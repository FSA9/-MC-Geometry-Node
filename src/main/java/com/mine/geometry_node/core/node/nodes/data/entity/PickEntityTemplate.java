package com.mine.geometry_node.core.node.nodes.data.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import net.minecraft.network.chat.Component;

public final class PickEntityTemplate extends BaseNode {
    public static final String TYPE_ID = "pick_entity_template";
    public static final String TEMPLATE_DATA = "template_data";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.pick_entity_template"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.ENTITY_TEMPLATE, "entity_template")
                        .build())
                .uiWidth(105)
                .addRow(new PortRow(null, StandardPorts.ENTITY_TEMPLATE.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        new PortDef(
                                TEMPLATE_DATA,
                                Component.translatable("geometry_node.port.entity_template_data"),
                                PortType.ENTITY_TEMPLATE,
                                EntityTemplateValue.EMPTY.toMap(),
                                true
                        ),
                        null,
                        UIHint.ENTITY_TEMPLATE,
                        null,
                        null
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.ENTITY_TEMPLATE.getId().equals(portName)) return null;
        return EntityTemplateValue.from(getRawInput(context, TEMPLATE_DATA));
    }
}
