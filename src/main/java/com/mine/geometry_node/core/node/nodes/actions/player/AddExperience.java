package com.mine.geometry_node.core.node.nodes.actions.player;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;

public class AddExperience extends BaseNode {

    public static final String TYPE_ID = "add_experience";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.add_experience"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                // 是否为等级 (True = 等级, False = 经验点数)
                .addRow(new PortRow(StandardPorts.BOOL.toInput(false), null, UIHint.CHECKBOX, null, null))
                // 经验数量
                .addRow(new PortRow(StandardPorts.INT.toInput(1), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Integer amount = getInput(context, StandardPorts.INT.getId(), Integer.class);
        Boolean isLevels = getInput(context, StandardPorts.BOOL.getId(), Boolean.class);

        if (amount != null && amount != 0 && !entities.isEmpty()) {
            boolean addLevels = Boolean.TRUE.equals(isLevels);
            for (Entity entity : entities) {
                // 仅对服务端玩家生效
                if (entity instanceof ServerPlayer player) {
                    if (addLevels) {
                        player.giveExperienceLevels(amount);
                    } else {
                        player.giveExperiencePoints(amount);
                    }
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}