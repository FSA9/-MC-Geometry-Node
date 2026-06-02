package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SetEntityRotation extends BaseNode {

    public static final String TYPE_ID = "set_entity_rotation";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_entity_rotation"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ROTATION.toInput(), null, UIHint.VECTOR, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Vec3 rotation = getInput(context, StandardPorts.ROTATION.getId(), Vec3.class);

        if (rotation != null && !entities.isEmpty()) {
            for (Entity entity : entities) {
                if (entity instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    serverPlayer.connection.teleport(
                            serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                            (float) rotation.y, (float) rotation.x
                    );
                } else {
                    // 如果是普通实体：直接赋值即可
                    entity.setXRot((float) rotation.x);
                    entity.setYRot((float) rotation.y);
                    entity.setYBodyRot((float) rotation.y);
                    entity.setYHeadRot((float) rotation.y);
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}