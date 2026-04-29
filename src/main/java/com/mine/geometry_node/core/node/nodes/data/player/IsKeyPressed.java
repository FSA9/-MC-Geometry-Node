package com.mine.geometry_node.core.node.nodes.data.player;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.state.PlayerInputStateManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.Map;

public class IsKeyPressed extends BaseNode {

    public static final String TYPE_ID = "is_key_pressed";

    // 状态查询通常不需要 DOUBLE_CLICK 等瞬间动作，只需查按键
    public static final String[] VALID_KEYS = {
            "skill_1", "skill_2", "skill_3", "skill_4", "skill_5",
            "skill_6", "skill_7", "skill_8", "skill_9", "skill_10",
            "ctrl", "shift", "alt"
    };

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.is_key_pressed"))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                // 限制为下拉框
                .addRow(new PortRow(
                        StandardPorts.NAME.toInput("ctrl"), null,
                        UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, VALID_KEYS)
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.BOOL.getId().equals(portName)) return false;

        Entity entity = getInput(context, StandardPorts.ENTITY.getId(), Entity.class);
        String keyId = getInput(context, StandardPorts.NAME.getId(), String.class);

        // 如果连线不完整，或者实体不是有效实体，返回 false
        if (entity == null || keyId == null) {
            return false;
        }

        // 瞬间 O(1) 性能查询服务端的内存字典
        return PlayerInputStateManager.isKeyPressed(entity.getUUID(), keyId);
    }
}