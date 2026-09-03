package com.mine.geometry_node.core.node.nodes.data.player;

import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.blueprint.event.PlayerInputKeys;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.Map;

public class IsKeyPressed extends BaseNode {

    public static final String TYPE_ID = "is_key_pressed";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.is_key_pressed"))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT, null, null)
                .addPassthroughInput(StandardPorts.NAME.toInput("ctrl"), UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, PlayerInputKeys.options()))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.BOOL.getId().equals(portName)) return false;

        Entity entity = getInput(context, StandardPorts.ENTITY.getId(), Entity.class);
        String keyId = getInput(context, StandardPorts.NAME.getId(), String.class);
        if (entity == null || keyId == null) return false;

        return BlueprintRuntime.INSTANCE.isKeyPressed(entity, keyId);
    }
}
