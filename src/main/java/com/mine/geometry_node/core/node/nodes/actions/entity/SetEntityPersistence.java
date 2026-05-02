package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.List;

public class SetEntityPersistence extends BaseNode {
    public static final String TYPE_ID = "set_entity_persistence";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_entity_persistence"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.BOOL.toInput(true), null, UIHint.CHECKBOX, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Boolean value = getInput(context, StandardPorts.BOOL.getId(), Boolean.class);

        if (value != null && !entities.isEmpty()) {
            for (Entity entity : entities) {
                if (entity instanceof Mob mob) {
                    if (value) {
                        mob.setPersistenceRequired(); // 原生快捷方法
                    } else {
                        CompoundTag tag = new CompoundTag();
                        if (mob.saveAsPassenger(tag)) {
                            tag.putBoolean("PersistenceRequired", false);
                            mob.load(tag);
                        }
                    }
                }
            }
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}