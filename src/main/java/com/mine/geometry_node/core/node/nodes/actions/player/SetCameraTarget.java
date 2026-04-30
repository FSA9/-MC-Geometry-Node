package com.mine.geometry_node.core.node.nodes.actions.player;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;

public class SetCameraTarget extends BaseNode {

    public static final String TYPE_ID = "set_camera_target";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_camera_target"))
                // 1. 执行流
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))

                // 2. 目标玩家 (谁的视角将被改变)
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))

                // 3. 摄像机实体 (要把视角绑定到哪个实体上。如果不连线或传空，则重置回玩家自己)
                .addRow(new PortRow(StandardPorts.ENTITY.toInputWithIndex(1), null, UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        // 获取受影响的玩家列表
        List<Entity> players = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);

        // 获取摄像机目标实体 (只取第一个)
        Entity cameraTarget = getInput(context, StandardPorts.ENTITY.getIdWithIndex(1), Entity.class);

        if (!players.isEmpty()) {
            for (Entity entity : players) {
                if (entity instanceof ServerPlayer player) {
                    // 如果传入了目标实体，则绑定视角
                    if (cameraTarget != null) {
                        player.setCamera(cameraTarget);
                    }
                    // 如果传入为空（没连线），则将视角重置回玩家自己
                    else {
                        player.setCamera(player);
                    }
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}