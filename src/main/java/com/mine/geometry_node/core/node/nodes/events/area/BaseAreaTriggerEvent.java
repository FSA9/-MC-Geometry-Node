package com.mine.geometry_node.core.node.nodes.events.area;

import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public abstract class BaseAreaTriggerEvent extends BaseEventNode {
    public static final String TRIGGER_ID_PORT = "trigger_id";
    public static final String INSIDE_COUNT_PORT = "inside_count";
    public static final String ENABLED_PORT = "enabled";

    private final String typeId;

    protected BaseAreaTriggerEvent(String typeId) {
        this.typeId = typeId;
    }

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(typeId, NodeType.EVENT, Component.translatable("geometry_node.node." + typeId))
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.TRIGGER_ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.XYZ.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.TYPE.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.CENTER.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.SIZE_3.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ROTATION.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, PortDef.create(TRIGGER_ID_PORT, "geometry_node.port.trigger_id", PortType.STRING), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, PortDef.create(INSIDE_COUNT_PORT, "geometry_node.port.inside_count", PortType.INTEGER), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.CENTER.toInput(Vec3.ZERO).hiddenPin(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)).hiddenPin(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.ROTATION.toInput(Vec3.ZERO).hiddenPin(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.INTERVAL.toInput(1).hiddenPin(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.OFFSET.toInput(0).hiddenPin(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(PortDef.create(ENABLED_PORT, "geometry_node.port.enabled", PortType.BOOLEAN, true).hiddenPin(), null, UIHint.CHECKBOX, null, null))
                .addRow(new PortRow(StandardPorts.DEBUG.toInput(false).hiddenPin(), null, UIHint.CHECKBOX, null, null))
                .build();
    }
}
