package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.List;

public class LeashEntity extends BaseNode {

    public static final String TYPE_ID = "leash_entity";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.leash_entity"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT) // 被牵住的实体 (必须是 Mob)
                .addPassthroughInput(StandardPorts.SOURCE_ENTITY.toInput(), UIHint.DEFAULT) // 牵绳子的实体
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> mobsToLeash = getInputs(context, StandardPorts.ENTITY.getId(), Entity.class);
        Entity leashHolder = getInputFromList(
                context, StandardPorts.SOURCE_ENTITY.getId(), 0, Entity.class);

        if (leashHolder != null && !mobsToLeash.isEmpty()) {
            for (Entity entity : mobsToLeash) {
                if (entity == null) continue;
                // 原版机制：只有 Mob 才能被拴绳牵着
                if (entity instanceof Mob mob) {
                    // true 表示强制发送数据包同步给客户端
                    mob.setLeashedTo(leashHolder, true);
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
