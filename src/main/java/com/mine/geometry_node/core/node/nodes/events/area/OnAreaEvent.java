package com.mine.geometry_node.core.node.nodes.events.area;

import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaTargetType;
import net.minecraft.network.chat.Component;

import java.util.Map;

/** Listens to an existing Area directly or through a Force Field reference. */
public final class OnAreaEvent extends BaseEventNode {
    public static final String TYPE_ID = "on_area_event";
    public static final String PHASE_PORT = "area_phase";
    public static final String SOURCE_PORT = "area_source";
    public static final String TARGET_PORT = "area_target";
    public static final String INSIDE_COUNT_PORT = "inside_count";
    public static final String INTERVAL_TICK_PORT = StandardPorts.TICK.getId();
    public static final String OFFSET_TICK_PORT = StandardPorts.TICK.getIdWithIndex(1);

    public static final String PHASE_ENTER = "enter";
    public static final String PHASE_STAY = "stay";
    public static final String PHASE_EXIT = "exit";
    public static final String[] PHASE_OPTIONS = {PHASE_ENTER, PHASE_STAY, PHASE_EXIT};
    public static final String SOURCE_AREA = "area";
    public static final String SOURCE_FORCE_FIELD = "force_field";
    public static final String[] SOURCE_OPTIONS = {SOURCE_AREA, SOURCE_FORCE_FIELD};

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDefinition(SOURCE_AREA);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        Object value = instanceData != null ? instanceData.inputs.get(SOURCE_PORT) : null;
        return buildDefinition(SOURCE_FORCE_FIELD.equals(value) ? SOURCE_FORCE_FIELD : SOURCE_AREA);
    }

    private NodeDef buildDefinition(String source) {
        NodeComment.Builder comment = NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.ENTITY, "entity")
                        .output(StandardPorts.TRIGGER_ENTITY, "trigger_entity")
                        .output(StandardPorts.HIT_POS, "hit_pos")
                        .output(StandardPorts.VECTOR, "vector")
                        .output(StandardPorts.TYPE, "phase")
                        .output(StandardPorts.SHAPE, "shape")
                        .output(StandardPorts.CENTER, "center")
                        .output(StandardPorts.SIZE_3, "size")
                        .output(StandardPorts.RADIUS, "radius")
                        .output(StandardPorts.HEIGHT, "height")
                        .output(StandardPorts.ROTATION, "rotation")
                        .output(StandardPorts.AREA_ID, "area_id")
                        .output(StandardPorts.FORCE_FIELD_ID, "force_field_id")
                        .output(SOURCE_PORT, "source")
                        .output(StandardPorts.DIMENSION, "dimension")
                        .output(INSIDE_COUNT_PORT, "inside_count")
                        .input(SOURCE_PORT, "source")
                        .input(StandardPorts.DIMENSION, "dimension")
                        .input(PHASE_PORT, "phase")
                        .input(TARGET_PORT, "target")
                        .input(INTERVAL_TICK_PORT, "interval")
                        .input(OFFSET_TICK_PORT, "offset");
        if (SOURCE_FORCE_FIELD.equals(source)) comment.input(StandardPorts.FORCE_FIELD_ID, "force_field_id");
        else comment.input(StandardPorts.AREA_ID, "area_id");

        PortDef idPort = SOURCE_FORCE_FIELD.equals(source)
                ? StandardPorts.FORCE_FIELD_ID.toInput("")
                : StandardPorts.AREA_ID.toInput("");
        return NodeDef.builder(TYPE_ID, NodeType.EVENT,
                        Component.translatable("geometry_node.node." + TYPE_ID))
                .comment(comment.build())
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.TRIGGER_ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.HIT_POS.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.VECTOR.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.TYPE.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.SHAPE.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.CENTER.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.SIZE_3.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.RADIUS.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.HEIGHT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ROTATION.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.AREA_ID.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.FORCE_FIELD_ID.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(PortDef.create(SOURCE_PORT, "geometry_node.port.area_source",
                                PortType.STRING, SOURCE_AREA),
                        PortDef.create(SOURCE_PORT, "geometry_node.port.area_source", PortType.STRING),
                        UIHint.SELECT, null, Map.of(
                                PortMetaKeys.OPTIONS, SOURCE_OPTIONS,
                                PortMetaKeys.OPTION_LABELS, new String[]{
                                        "geometry_node.area.source.area",
                                        "geometry_node.area.source.force_field"
                                })))
                .addRow(new PortRow(idPort, null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.DIMENSION.toInput(RegistryDataManager.DEFAULT_DIMENSION),
                        StandardPorts.DIMENSION.toOutput(), UIHint.SELECT, null,
                        Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, RegistryDataManager.DIMENSION_REGISTRY_ID)))
                .addRow(new PortRow(null, PortDef.create(INSIDE_COUNT_PORT,
                        "geometry_node.port.inside_count", PortType.INTEGER), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        PortDef.create(PHASE_PORT, "geometry_node.port.area_phase", PortType.STRING,
                                PHASE_ENTER).hiddenPin(),
                        null, UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, PHASE_OPTIONS)))
                .addRow(new PortRow(
                        PortDef.create(TARGET_PORT, "geometry_node.port.area_target", PortType.STRING,
                                AreaTargetType.ALL.id()).hiddenPin(),
                        null, UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, AreaTargetType.OPTIONS)))
                .addRow(new PortRow(StandardPorts.TICK.toInput(1)
                        .withDisplayName("geometry_node.port.tick.interval").hiddenPin(),
                        null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.TICK.toInputWithIndex(1, 0)
                        .withDisplayName("geometry_node.port.tick.offset").hiddenPin(),
                        null, UIHint.INPUT, null, null))
                .build();
    }
}
