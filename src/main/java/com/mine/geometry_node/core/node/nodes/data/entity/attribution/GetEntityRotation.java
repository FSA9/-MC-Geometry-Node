package com.mine.geometry_node.core.node.nodes.data.entity.attribution;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.system.display.DisplayTransformController;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;


public class GetEntityRotation extends BaseNode {

    public static final String TYPE_ID = "get_entity_rotation";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_entity_rotation"))
                .addRow(new PortRow(null, StandardPorts.ROTATION.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT, null, null)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.ROTATION.getId().equals(portName)) return null;

        Entity entity = getInputFromList(context, StandardPorts.ENTITY.getId(), 0, Entity.class);
        if (entity == null) return null;

        Entity target = entity;
        Vec3 rotation = DisplayTransformController.worldRotation(target);
        return bindDynamicVector(rotation, target, "rotation");
    }
}
