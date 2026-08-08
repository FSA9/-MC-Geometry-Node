package com.mine.geometry_node.core.node.nodes.geometry;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.document.NodeData;
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

public class CreateMesh extends BaseNode {
    public static final String TYPE_ID = "create_mesh";

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDef(GeometryValue.PrimitiveType.CUBE);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        return buildDef(resolveShape(instanceData));
    }

    private NodeDef buildDef(GeometryValue.PrimitiveType shape) {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.create_mesh"));
        builder.addRow(new PortRow(null, StandardPorts.GEOMETRY.toOutput(), UIHint.DEFAULT, null, null));
        builder.addRow(new PortRow(
                StandardPorts.SHAPE.toInput(shape.id()).hiddenPin(),
                null,
                UIHint.SELECT,
                null,
                Map.of(PortMetaKeys.OPTIONS, GeometryValue.PrimitiveType.OPTIONS)
        ));
        builder.addRow(new PortRow(StandardPorts.CENTER.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null));

        switch (shape) {
            case CUBE -> addCubeRows(builder);
            case CYLINDER -> addCylinderRows(builder);
            case UV_SPHERE -> addUvSphereRows(builder);
        }

        return builder.build();
    }

    private static void addCubeRows(NodeDef.Builder builder) {
        builder.addRow(new PortRow(StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)), null, UIHint.VECTOR, null, null));
        builder.addRow(new PortRow(StandardPorts.VERTICES_X.toInput(2), null, UIHint.INPUT, null, null));
        builder.addRow(new PortRow(StandardPorts.VERTICES_Y.toInput(2), null, UIHint.INPUT, null, null));
        builder.addRow(new PortRow(StandardPorts.VERTICES_Z.toInput(2), null, UIHint.INPUT, null, null));
    }

    private static void addCylinderRows(NodeDef.Builder builder) {
        builder.addRow(new PortRow(
                StandardPorts.FILL_TYPE.toInput(GeometryValue.CylinderFillType.NGON.id()).hiddenPin(),
                null,
                UIHint.SELECT,
                null,
                Map.of(PortMetaKeys.OPTIONS, GeometryValue.CylinderFillType.OPTIONS)
        ));
        builder.addRow(new PortRow(StandardPorts.VERTICES.toInput(32), null, UIHint.INPUT, null, null));
        builder.addRow(new PortRow(StandardPorts.SIDE_SEGMENTS.toInput(1), null, UIHint.INPUT, null, null));
        builder.addRow(new PortRow(StandardPorts.FILL_SEGMENTS.toInput(1), null, UIHint.INPUT, null, null));
        builder.addRow(new PortRow(StandardPorts.RADIUS.toInput(1.0f), null, UIHint.INPUT, null, null));
        builder.addRow(new PortRow(StandardPorts.DEPTH.toInput(2.0f), null, UIHint.INPUT, null, null));
    }

    private static void addUvSphereRows(NodeDef.Builder builder) {
        builder.addRow(new PortRow(StandardPorts.RADIUS.toInput(1.0f), null, UIHint.INPUT, null, null));
        builder.addRow(new PortRow(StandardPorts.VERTICES.toInput(32), null, UIHint.INPUT, null, null));
        builder.addRow(new PortRow(StandardPorts.RINGS.toInput(16), null, UIHint.INPUT, null, null));
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.GEOMETRY.getId().equals(portName)) {
            return null;
        }

        GeometryValue.PrimitiveType shape = resolveShape(getInput(context, StandardPorts.SHAPE.getId(), String.class));
        Vec3 center = getInput(context, StandardPorts.CENTER.getId(), Vec3.class);

        GeometryValue.Primitive primitive = switch (shape) {
            case CUBE -> GeometryValue.Primitive.cube(
                    center != null ? center : Vec3.ZERO,
                    valueOrDefault(getInput(context, StandardPorts.SIZE_3.getId(), Vec3.class), new Vec3(1, 1, 1)),
                    intOrDefault(getInput(context, StandardPorts.VERTICES_X.getId(), Integer.class), 2),
                    intOrDefault(getInput(context, StandardPorts.VERTICES_Y.getId(), Integer.class), 2),
                    intOrDefault(getInput(context, StandardPorts.VERTICES_Z.getId(), Integer.class), 2)
            );
            case CYLINDER -> GeometryValue.Primitive.cylinder(
                    center != null ? center : Vec3.ZERO,
                    intOrDefault(getInput(context, StandardPorts.VERTICES.getId(), Integer.class), 32),
                    intOrDefault(getInput(context, StandardPorts.SIDE_SEGMENTS.getId(), Integer.class), 1),
                    intOrDefault(getInput(context, StandardPorts.FILL_SEGMENTS.getId(), Integer.class), 1),
                    floatOrDefault(getInput(context, StandardPorts.RADIUS.getId(), Float.class), 1.0f),
                    floatOrDefault(getInput(context, StandardPorts.DEPTH.getId(), Float.class), 2.0f),
                    GeometryValue.CylinderFillType.fromId(getInput(context, StandardPorts.FILL_TYPE.getId(), String.class))
            );
            case UV_SPHERE -> GeometryValue.Primitive.uvSphere(
                    center != null ? center : Vec3.ZERO,
                    intOrDefault(getInput(context, StandardPorts.VERTICES.getId(), Integer.class), 32),
                    intOrDefault(getInput(context, StandardPorts.RINGS.getId(), Integer.class), 16),
                    floatOrDefault(getInput(context, StandardPorts.RADIUS.getId(), Float.class), 1.0f)
            );
        };

        return GeometryValue.of(primitive);
    }

    private static GeometryValue.PrimitiveType resolveShape(NodeData instanceData) {
        if (instanceData == null || instanceData.inputs == null) {
            return GeometryValue.PrimitiveType.CUBE;
        }
        return resolveShape(instanceData.inputs.get(StandardPorts.SHAPE.getId()));
    }

    private static GeometryValue.PrimitiveType resolveShape(Object rawShape) {
        return GeometryValue.PrimitiveType.fromId(rawShape != null ? String.valueOf(rawShape) : null);
    }

    private static int intOrDefault(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private static float floatOrDefault(Float value, float fallback) {
        return value != null ? value : fallback;
    }

    private static <T> T valueOrDefault(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
