package com.mine.geometry_node.core.node.nodes.actions.world;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CreateExplosion extends BaseNode {

    public static final String TYPE_ID = "create_explosion";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.create_explosion"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.XYZ.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.RADIUS.toInput(4.0f), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.IS_BLOCK_BREAK.toInputWithIndex(0, true), null, UIHint.CHECKBOX, null, null)) // 是否破坏方块
                .addRow(new PortRow(StandardPorts.IS_FIRE_GEN.toInputWithIndex(1, false), null, UIHint.CHECKBOX, null, null)) // 是否产生火焰
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Vec3 pos = getInput(context, StandardPorts.XYZ.getId(), Vec3.class);
        Float radius = getInput(context, StandardPorts.RADIUS.getId(), Float.class);
        Boolean destroyBlocks = getInput(context, StandardPorts.IS_BLOCK_BREAK.getIdWithIndex(0), Boolean.class);
        Boolean causeFire = getInput(context, StandardPorts.IS_FIRE_GEN.getIdWithIndex(1), Boolean.class);

        if (pos != null && radius != null && context.getLevel() instanceof ServerLevel level) {
            Level.ExplosionInteraction interaction = (destroyBlocks != null && destroyBlocks)
                    ? Level.ExplosionInteraction.BLOCK
                    : Level.ExplosionInteraction.NONE;

            boolean fire = causeFire != null && causeFire;

            level.explode(null, pos.x, pos.y, pos.z, radius, fire, interaction);
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}