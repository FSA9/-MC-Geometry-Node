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
import net.minecraft.world.level.GameType;

import java.util.List;

public class SetGameMode extends BaseNode {

    public static final String TYPE_ID = "set_game_mode";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_game_mode"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT)
                // 0: 生存, 1: 创造, 2: 冒险, 3: 旁观
                .addPassthroughInput(StandardPorts.INT.toInput(0), UIHint.INPUT)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Integer modeId = getInput(context, StandardPorts.INT.getId(), Integer.class);

        if (modeId != null && !entities.isEmpty()) {
            // 将 Int 转换为原版的游戏模式枚举 (超出范围默认返回 SURVIVAL)
            GameType gameType = GameType.byId(modeId);

            for (Entity entity : entities) {
                if (entity instanceof ServerPlayer player) {
                    player.setGameMode(gameType);
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}