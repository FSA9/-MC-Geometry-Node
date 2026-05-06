package com.mine.geometry_node.core.node.nodes.actions.display_entity;

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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SpawnInteractionEntity extends BaseNode {

    public static final String TYPE_ID = "spawn_interaction_entity";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.spawn_interaction_entity"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))

                .addRow(new PortRow(StandardPorts.XYZ.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.WIDTH.toInput(1.0f), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.HEIGHT.toInput(1.0f), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.RESPONSIVE.toInput(false), null, UIHint.CHECKBOX, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Level level = context.getLevel();
        if (level == null || level.isClientSide) return next(StandardPorts.FLOW_OUT.getId());

        Vec3 pos = getInput(context, StandardPorts.XYZ.getId(), Vec3.class);
        if (pos == null) pos = Vec3.ZERO;

        Float width = getInput(context, StandardPorts.WIDTH.getId(), Float.class);
        Float height = getInput(context, StandardPorts.HEIGHT.getId(), Float.class);
        Boolean responsive = getInput(context, StandardPorts.RESPONSIVE.getId(), Boolean.class);

        Interaction interaction = EntityType.INTERACTION.create(level);
        if (interaction != null) {
            interaction.setPos(pos.x, pos.y, pos.z);

            // 利用 NBT 完美兼容所有版本映射，注入核心参数
            CompoundTag nbt = new CompoundTag();
            interaction.saveWithoutId(nbt);
            nbt.putFloat("width", width != null ? width : 1.0f);
            nbt.putFloat("height", height != null ? height : 1.0f);
            nbt.putBoolean("response", responsive != null && responsive);
            interaction.load(nbt);

            level.addFreshEntity(interaction);
            context.setTempData("spawned_interaction_" + context.getCurrentNodeId(), interaction);
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.ENTITY.getId().equals(portName)) {
            return context.getTempData("spawned_interaction_" + context.getCurrentNodeId());
        }
        return null;
    }
}