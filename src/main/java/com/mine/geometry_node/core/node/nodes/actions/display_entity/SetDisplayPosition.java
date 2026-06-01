package com.mine.geometry_node.core.node.nodes.actions.display_entity;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SetDisplayPosition extends BaseNode {

    public static final String TYPE_ID = "set_display_position";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_display_position"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                // 目标绝对坐标
                .addRow(new PortRow(StandardPorts.XYZ.toInput(), null, UIHint.VECTOR, null, null))
                // 位移专属插值
                .addRow(new PortRow(StandardPorts.TELEPORT_DURATION.toInput(0), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        if (entities.isEmpty()) return next(StandardPorts.FLOW_OUT.getId());

        Vec3 targetPos = getInput(context, StandardPorts.XYZ.getId(), Vec3.class);
        Integer tpDuration = getInput(context, StandardPorts.TELEPORT_DURATION.getId(), Integer.class);

        if (targetPos == null) return next(StandardPorts.FLOW_OUT.getId());

        for (Entity entity : entities) {
            if (entity instanceof Display displayEntity) {

                if (tpDuration != null) {
                    CompoundTag nbt = new CompoundTag();
                    displayEntity.saveWithoutId(nbt);
                    nbt.putInt("teleport_duration", Math.max(0, tpDuration));
                    displayEntity.load(nbt);
                }

                // 真正的底层区块坐标转移
                displayEntity.teleportTo(targetPos.x, targetPos.y, targetPos.z);
            }
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
