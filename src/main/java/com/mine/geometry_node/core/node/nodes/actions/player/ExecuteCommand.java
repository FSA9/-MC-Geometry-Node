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

public class ExecuteCommand extends BaseNode {

    public static final String TYPE_ID = "execute_command";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.execute_command"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                // 指令内容 (不需要加斜杠 "/")
                .addRow(new PortRow(StandardPorts.EXPRESSION.toInput("say Hello"), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        String command = getInput(context, StandardPorts.EXPRESSION.getId(), String.class);

        if (command != null && !command.isBlank() && !entities.isEmpty()) {
            // 去除可能带入的开头斜杠
            if (command.startsWith("/")) {
                command = command.substring(1);
            }

            for (Entity entity : entities) {
                if (entity instanceof ServerPlayer player) {
                    // 以玩家身份在服务端执行指令
                    player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
