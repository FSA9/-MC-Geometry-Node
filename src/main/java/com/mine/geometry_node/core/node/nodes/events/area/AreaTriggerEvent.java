package com.mine.geometry_node.core.node.nodes.events.area;

import com.mine.geometry_node.core.engine.blueprint.spatial.AreaAnchor;
import com.mine.geometry_node.core.engine.blueprint.spatial.AreaShape;
import com.mine.geometry_node.core.engine.blueprint.spatial.AreaTargetType;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
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

import java.util.Map;

public class AreaTriggerEvent extends BaseEventNode {
    public static final String TYPE_ID = "area_trigger_event";
    public static final String PHASE_PORT = "phase";
    public static final String ANCHOR_PORT = "anchor";
    public static final String SHAPE_PORT = "shape";
    public static final String TARGET_PORT = "target";
    public static final String HEIGHT_PORT = StandardPorts.HEIGHT.getId();
    public static final String TRIGGER_ID_PORT = "trigger_id";
    public static final String INSIDE_COUNT_PORT = "inside_count";
    public static final double DEFAULT_RADIUS = 1.0D;
    public static final double DEFAULT_HEIGHT = 2.0D;

    public static final String PHASE_ENTER = "enter";
    public static final String PHASE_STAY = "stay";
    public static final String PHASE_EXIT = "exit";
    public static final String[] PHASE_OPTIONS = {PHASE_ENTER, PHASE_STAY, PHASE_EXIT};

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDef(AreaShape.BOX);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        AreaShape shape = AreaShape.BOX;
        if (instanceData != null && instanceData.inputs.get(SHAPE_PORT) instanceof String rawShape) {
            shape = AreaShape.fromId(rawShape);
        }
        return buildDef(shape);
    }

    private NodeDef buildDef(AreaShape shape) {
        NodeComment.Builder comment = NodeComment.builder(TYPE_ID)
                .text("summary")
                .output(StandardPorts.FLOW_OUT, "flow_out")
                .output(StandardPorts.ENTITY, "entity")
                .output(StandardPorts.TRIGGER_ENTITY, "trigger_entity")
                .output(StandardPorts.HIT_POS, "hit_pos")
                .output(StandardPorts.VECTOR, "vector")
                .output(StandardPorts.TYPE, "type")
                .output(StandardPorts.CENTER, "output_center")
                .output(TRIGGER_ID_PORT, "trigger_id")
                .output(INSIDE_COUNT_PORT, "inside_count")
                .input(PHASE_PORT, "phase")
                .input(ANCHOR_PORT, "anchor")
                .input(SHAPE_PORT, "shape")
                .input(TARGET_PORT, "target")
                .input(StandardPorts.CENTER, "input_center")
                .input(StandardPorts.INTERVAL, "interval")
                .input(StandardPorts.OFFSET, "offset");

        switch (shape) {
            case SPHERE -> comment.output(StandardPorts.RADIUS, "output_radius")
                    .input(StandardPorts.RADIUS, "input_radius");
            case CYLINDER -> comment.output(StandardPorts.RADIUS, "output_radius")
                    .output(HEIGHT_PORT, "output_height")
                    .output(StandardPorts.ROTATION, "output_rotation")
                    .input(StandardPorts.RADIUS, "input_radius")
                    .input(HEIGHT_PORT, "input_height")
                    .input(StandardPorts.ROTATION, "input_rotation");
            case BOX -> comment.output(StandardPorts.SIZE_3, "output_size_3")
                    .output(StandardPorts.ROTATION, "output_rotation")
                    .input(StandardPorts.SIZE_3, "input_size_3")
                    .input(StandardPorts.ROTATION, "input_rotation");
        }

        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node." + TYPE_ID))
                .comment(comment.build())
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.TRIGGER_ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.HIT_POS.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.VECTOR.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.TYPE.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.CENTER.toOutput(), UIHint.DEFAULT, null, null));

        switch (shape) {
            case SPHERE -> builder.addRow(new PortRow(null, StandardPorts.RADIUS.toOutput(), UIHint.DEFAULT, null, null));
            case CYLINDER -> {
                builder.addRow(new PortRow(null, StandardPorts.RADIUS.toOutput(), UIHint.DEFAULT, null, null));
                builder.addRow(new PortRow(null, areaHeightPort(0.0D), UIHint.DEFAULT, null, null));
                builder.addRow(new PortRow(null, StandardPorts.ROTATION.toOutput(), UIHint.DEFAULT, null, null));
            }
            case BOX -> {
                builder.addRow(new PortRow(null, StandardPorts.SIZE_3.toOutput(), UIHint.DEFAULT, null, null));
                builder.addRow(new PortRow(null, StandardPorts.ROTATION.toOutput(), UIHint.DEFAULT, null, null));
            }
        }

        builder.addRow(new PortRow(null, PortDef.create(TRIGGER_ID_PORT, "geometry_node.port.trigger_id", PortType.STRING), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, PortDef.create(INSIDE_COUNT_PORT, "geometry_node.port.inside_count", PortType.INTEGER), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        PortDef.create(PHASE_PORT, "geometry_node.port.area_phase", PortType.STRING, PHASE_ENTER).hiddenPin(),
                        null, UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, PHASE_OPTIONS)))
                .addRow(new PortRow(
                        PortDef.create(ANCHOR_PORT, "geometry_node.port.area_anchor", PortType.STRING, AreaAnchor.WORLD.id()).hiddenPin(),
                        null, UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, AreaAnchor.OPTIONS)))
                .addRow(new PortRow(
                        PortDef.create(SHAPE_PORT, "geometry_node.port.area_shape", PortType.STRING, AreaShape.BOX.id()).hiddenPin(),
                        null, UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, AreaShape.OPTIONS)))
                .addRow(new PortRow(
                        PortDef.create(TARGET_PORT, "geometry_node.port.area_target", PortType.STRING, AreaTargetType.ALL.id()).hiddenPin(),
                        null, UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, AreaTargetType.OPTIONS)))
                .addRow(new PortRow(StandardPorts.CENTER.toInput(Vec3.ZERO).hiddenPin(), null, UIHint.VECTOR, null, null));

        switch (shape) {
            case SPHERE -> builder.addRow(new PortRow(StandardPorts.RADIUS.toInput((float) DEFAULT_RADIUS).hiddenPin(), null, UIHint.INPUT, null, null));
            case CYLINDER -> {
                builder.addRow(new PortRow(StandardPorts.RADIUS.toInput((float) DEFAULT_RADIUS).hiddenPin(), null, UIHint.INPUT, null, null));
                builder.addRow(new PortRow(areaHeightPort(DEFAULT_HEIGHT).hiddenPin(), null, UIHint.INPUT, null, null));
                builder.addRow(new PortRow(StandardPorts.ROTATION.toInput(Vec3.ZERO).hiddenPin(), null, UIHint.VECTOR, null, null));
            }
            case BOX -> {
                builder.addRow(new PortRow(StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)).hiddenPin(), null, UIHint.VECTOR, null, null));
                builder.addRow(new PortRow(StandardPorts.ROTATION.toInput(Vec3.ZERO).hiddenPin(), null, UIHint.VECTOR, null, null));
            }
        }

        return builder
                .addRow(new PortRow(StandardPorts.INTERVAL.toInput(1).hiddenPin(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.OFFSET.toInput(0).hiddenPin(), null, UIHint.INPUT, null, null))
                .build();
    }

    private static PortDef areaHeightPort(double defaultValue) {
        return PortDef.create(HEIGHT_PORT, "geometry_node.port.area_height", PortType.FLOAT, (float) defaultValue);
    }
}
