package com.mine.geometry_node.core.node.util;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import net.minecraft.network.chat.Component;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.engine.graph.value.TypeConverter;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.value.dynamic.DynamicData;

/** Shared resource inputs without changing persisted graph port identities. */
public final class ResourceNodeSupport {
    private ResourceNodeSupport() {}

    public static PortDef input(String wireId) {
        return PortDef.create(wireId, "geometry_node.port.resource_id", PortType.STRING, "");
    }

    public static PortRow creationRow(String inputId, String outputId) {
        // Creation resolves names/empty inputs, so the output must use the execution result.
        return new PortRow(input(inputId), new PortDef(outputId, Component.empty(),
                PortType.STRING, null, false), UIHint.INPUT, null, null);
    }

    public static String read(GraphDataContext context, String... legacyIds) {
        String value = readPort(context, StandardPorts.RESOURCE_ID.getId());
        if (value != null && !value.isBlank()) return value;
        for (String legacyId : legacyIds) {
            value = readPort(context, legacyId);
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private static String readPort(GraphDataContext context, String port) {
        Object value = context.resolveInput(port).value();
        if (value instanceof DynamicData dynamic) value = dynamic.value();
        return TypeConverter.convert(value, String.class, context);
    }

    public static boolean hasLegacyPort(NodeData data, String wireId) {
        if (data == null) return false;
        Object value = data.inputs != null ? data.inputs.get(wireId) : null;
        return (value != null && !value.toString().isBlank())
                || (data.connectedInputs != null && data.connectedInputs.contains(wireId))
                || (data.outputs != null && data.outputs.containsKey(wireId)
                    && data.outputs.get(wireId) != null && !data.outputs.get(wireId).isEmpty())
                || (data.portConfig != null
                    && ((data.portConfig.inputs != null && data.portConfig.inputs.containsKey(wireId))
                        || (data.portConfig.outputs != null && data.portConfig.outputs.containsKey(wireId))));
    }

    public static NodeDef.Builder addLegacyInput(NodeDef.Builder builder, NodeData data, String wireId) {
        if (hasLegacyPort(data, wireId)) builder.addPassthroughInput(input(wireId), UIHint.INPUT);
        return builder;
    }
}
