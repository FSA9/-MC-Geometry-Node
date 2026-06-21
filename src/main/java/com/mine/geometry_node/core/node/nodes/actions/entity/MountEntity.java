package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.List;

public class MountEntity extends BaseNode {

    public static final String TYPE_ID = "mount_entity";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.mount_entity"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null)) // 乘客
                .addRow(new PortRow(StandardPorts.SOURCE_ENTITY.toInput(), null, UIHint.DEFAULT, null, null)) // 载具
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> passengers = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Entity vehicle = getInput(context, StandardPorts.SOURCE_ENTITY.getId(), Entity.class);

        if (vehicle != null && !passengers.isEmpty()) {
            for (Entity passenger : passengers) {
                // 防止自己骑自己导致游戏死循环崩溃
                if (passenger != vehicle) {
                    // true 表示强制骑乘（忽略某些原版的骑乘限制）
                    passenger.startRiding(vehicle, true, true);
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
