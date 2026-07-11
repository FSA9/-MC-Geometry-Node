package com.mine.geometry_node.core.node.nodes.geometry;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class MeshCube extends BaseNode {
    public static final String TYPE_ID = "mesh_cube";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.mesh_cube"))
                .addRow(new PortRow(null, StandardPorts.GEOMETRY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.CENTER.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.VERTICES_X.toInput(2), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.VERTICES_Y.toInput(2), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.VERTICES_Z.toInput(2), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.GEOMETRY.getId().equals(portName)) {
            return null;
        }

        Vec3 center = getInput(context, StandardPorts.CENTER.getId(), Vec3.class);
        Vec3 size = getInput(context, StandardPorts.SIZE_3.getId(), Vec3.class);
        Integer verticesX = getInput(context, StandardPorts.VERTICES_X.getId(), Integer.class);
        Integer verticesY = getInput(context, StandardPorts.VERTICES_Y.getId(), Integer.class);
        Integer verticesZ = getInput(context, StandardPorts.VERTICES_Z.getId(), Integer.class);

        GeometryValue.Primitive primitive = GeometryValue.Primitive.cube(
                center != null ? center : Vec3.ZERO,
                size != null ? size : new Vec3(1, 1, 1),
                verticesX != null ? verticesX : 2,
                verticesY != null ? verticesY : 2,
                verticesZ != null ? verticesZ : 2
        );
        return GeometryValue.of(primitive);
    }
}
