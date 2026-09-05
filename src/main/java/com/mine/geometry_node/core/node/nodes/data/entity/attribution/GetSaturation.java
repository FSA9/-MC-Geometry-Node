package com.mine.geometry_node.core.node.nodes.data.entity.attribution;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;


public class GetSaturation extends BaseNode {

    public static final String TYPE_ID = "get_saturation";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_saturation"))
                .addRow(new PortRow(null, StandardPorts.FLOAT_VALUE.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT, null, null)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.FLOAT_VALUE.getId().equals(portName)) return null;

        Entity entity = getInputFromList(context, StandardPorts.ENTITY.getId(), 0, Entity.class);
        if (entity == null) return null;

        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            return player.getFoodData().getSaturationLevel();
        }

        return null;
    }
}
