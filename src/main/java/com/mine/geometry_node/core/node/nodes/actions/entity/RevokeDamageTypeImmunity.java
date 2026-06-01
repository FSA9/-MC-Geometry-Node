package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.execution.attachment.EntityImmunityAttachment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;

public class RevokeDamageTypeImmunity extends BaseNode {

    public static final String TYPE_ID = "revoke_damage_type_immunity";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.revoke_damage_type_immunity"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        StandardPorts.STRING.toInput(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, "minecraft:damage_type")
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        String damageType = getInput(context, StandardPorts.STRING.getId(), String.class);

        if (damageType != null && !damageType.isEmpty() && !entities.isEmpty()) {
            for (Entity entity : entities) {
                EntityImmunityAttachment.revokeImmunity(entity, damageType);
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}