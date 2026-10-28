package com.mine.geometry_node.core.node.nodes.actions.light;

import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.util.AnchorNodeSupport;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.MetaKey;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import java.util.Map;
import java.util.Set;

/** Shared editor presentation for creation, setters and configuration nodes. */
final class LightNodeSchema {
    private static final Set<String> VOLUME_FIELDS = Set.of("strength", "density", "noise_strength",
            "noise_scale", "velocity", "seed");
    private static final Set<String> NOISE_FIELDS = Set.of("noise_strength", "noise_scale", "velocity", "seed");

    private LightNodeSchema() {}

    /** Light resources change through explicit actions; these ports capture creation-time values. */
    static PortDef transformPort(PortDef port) {
        return new PortDef(port.id(),port.displayName(),port.type(),port.defaultValue(),port.hidePin(),false);
    }
    static void addTransformInputs(NodeDef.Builder builder) {
        builder.addPassthroughInput(transformPort(AnchorNodeSupport.WORLD_OFFSET),UIHint.VECTOR);
        builder.addPassthroughInput(transformPort(AnchorNodeSupport.WORLD_ROTATION),UIHint.VECTOR);
        builder.addRow(PortRow.passthrough(transformPort(AnchorNodeSupport.LOCAL_OFFSET),UIHint.VECTOR,null,null));
        builder.addRow(PortRow.passthrough(transformPort(AnchorNodeSupport.LOCAL_ROTATION),UIHint.VECTOR,null,null));
    }

    static void addType(NodeDef.Builder b) {
        b.addPassthroughInput(LightConfigurationNode.port("type", PortType.STRING, "spot").hiddenPin(), UIHint.SELECT, null,
                Map.of(PortMetaKeys.OPTIONS, new String[]{"point", "spot", "area"}, PortMetaKeys.OPTION_LABELS,
                        new String[]{"geometry_node.light.option.point", "geometry_node.light.option.spot", "geometry_node.light.option.area"}));
    }

    static void addShape(NodeDef.Builder b) {
        LightConfigurationNode.add(b, "range", PortType.FLOAT, 16f, UIHint.INPUT);
        LightConfigurationNode.add(b, "source_size", PortType.FLOAT, 0.02f, UIHint.INPUT);
        LightConfigurationNode.add(b, "area_width", PortType.FLOAT, 1f, UIHint.INPUT);
        LightConfigurationNode.add(b, "area_height", PortType.FLOAT, 1f, UIHint.INPUT);
        LightConfigurationNode.add(b, "cone_angle", PortType.FLOAT, 45f, UIHint.INPUT);
        LightConfigurationNode.add(b, "edge_softness", PortType.FLOAT, 0.25f, UIHint.INPUT);
    }

    static Map<MetaKey<?>, Object> numericBounds(String port) {
        return switch (port) {
            case "intensity" -> bounds(0, 1024);
            case "range" -> bounds(0.01f, 32);
            case "cone_angle" -> bounds(1, 179);
            case "edge_softness", "noise_strength" -> bounds(0, 1);
            case "source_size" -> bounds(0, 4);
            case "area_width", "area_height" -> bounds(0.01f, 8);
            case "strength" -> bounds(0, 100);
            case "density" -> bounds(0, 10);
            case "noise_scale", "scale" -> bounds(0.001f, 1024);
            case "shadow_resolution", "volume_resolution" -> bounds(0.1f, 1);
            case "shadow_samples", "volume_emitter_samples" -> bounds(4, 16);
            case "volume_samples" -> bounds(4, 64);
            case "duration_ticks" -> bounds(0, Integer.MAX_VALUE);
            default -> null;
        };
    }

    private static Map<MetaKey<?>, Object> bounds(Number min, Number max) {
        return Map.of(PortMetaKeys.NUMERIC_MIN, min, PortMetaKeys.NUMERIC_MAX, max);
    }

    static NodeDef finish(NodeDef definition, NodeData data, boolean volume) {
        String mode = "off";
        if (data != null && data.isInputConnected("mode")) mode = "dynamic";
        else if (data != null && data.inputs != null && data.inputs.containsKey("mode"))
            mode = String.valueOf(data.inputs.get("mode"));
        String selectedMode = mode;
        var rows = definition.rows().stream()
                .filter(row -> !volume || visible(row, data, selectedMode))
                .filter(row -> visibleForType(row, data)).toList();
        var comment = NodeComment.builder(definition.typeId()).text("summary");
        for (PortRow row : rows) {
            if (row.leftPort() != null) comment.input(row.leftPort().id(), help(row.leftPort().id()));
            if (row.rightPort() != null && !row.dataPassthrough()
                    && !(row.leftPort() != null && StandardPorts.RESOURCE_ID.getId().equals(row.rightPort().id())))
                comment.output(row.rightPort().id(), help(row.rightPort().id()));
        }
        return new NodeDef(definition.typeId(), definition.category(), definition.displayName(),
                comment.build(), definition.meta(), rows);
    }

    private static String help(String port) { return "geometry_node.light.port_help." + port; }

    private static boolean visibleForType(PortRow row, NodeData data) {
        if (row.leftPort() == null || (data != null && data.isInputConnected("type"))) return true;
        String id = row.leftPort().id();
        if (data != null && (data.isInputConnected(id) || (data.outputs != null && data.outputs.containsKey(id)
                && !data.outputs.get(id).isEmpty()))) return true;
        String type = data != null && data.inputs != null ? String.valueOf(data.inputs.getOrDefault("type", "spot")) : "spot";
        if (id.equals("area_width") || id.equals("area_height")) return type.equals("area");
        if (type.equals("area")) return !Set.of("source_size", "cone_angle", "edge_softness").contains(id);
        return !type.equals("point") || !Set.of("rotation", "cone_angle", "edge_softness").contains(id);
    }

    private static boolean visible(PortRow row, NodeData data, String mode) {
        if (row.leftPort() == null) return true;
        String id = row.leftPort().id();
        // Existing connections remain valid even when their parameter is currently inactive.
        if (data != null && (data.isInputConnected(id)
                || (data.outputs != null && data.outputs.containsKey(id) && !data.outputs.get(id).isEmpty()))) return true;
        return !("off".equals(mode) && VOLUME_FIELDS.contains(id))
                && !("uniform".equals(mode) && NOISE_FIELDS.contains(id));
    }
}
