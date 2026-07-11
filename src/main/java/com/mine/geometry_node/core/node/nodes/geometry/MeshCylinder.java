package com.mine.geometry_node.core.node.nodes.geometry;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class MeshCylinder extends BaseNode {
    public static final String TYPE_ID = "mesh_cylinder";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.mesh_cylinder"))
                .addRow(new PortRow(null, StandardPorts.GEOMETRY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.CENTER.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(
                        StandardPorts.FILL_TYPE.toInput(GeometryValue.CylinderFillType.NGON.id()).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, GeometryValue.CylinderFillType.OPTIONS)
                ))
                .addRow(new PortRow(StandardPorts.VERTICES.toInput(32), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.SIDE_SEGMENTS.toInput(1), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.FILL_SEGMENTS.toInput(1), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.RADIUS.toInput(1.0f), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.DEPTH.toInput(2.0f), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.GEOMETRY.getId().equals(portName)) {
            return null;
        }

        Vec3 center = getInput(context, StandardPorts.CENTER.getId(), Vec3.class);
        Integer vertices = getInput(context, StandardPorts.VERTICES.getId(), Integer.class);
        Integer sideSegments = getInput(context, StandardPorts.SIDE_SEGMENTS.getId(), Integer.class);
        Integer fillSegments = getInput(context, StandardPorts.FILL_SEGMENTS.getId(), Integer.class);
        Float radius = getInput(context, StandardPorts.RADIUS.getId(), Float.class);
        Float depth = getInput(context, StandardPorts.DEPTH.getId(), Float.class);
        String fillType = getInput(context, StandardPorts.FILL_TYPE.getId(), String.class);

        GeometryValue.Primitive primitive = GeometryValue.Primitive.cylinder(
                center != null ? center : Vec3.ZERO,
                vertices != null ? vertices : 32,
                sideSegments != null ? sideSegments : 1,
                fillSegments != null ? fillSegments : 1,
                radius != null ? radius : 1.0f,
                depth != null ? depth : 2.0f,
                GeometryValue.CylinderFillType.fromId(fillType)
        );
        return GeometryValue.of(primitive);
    }
}
