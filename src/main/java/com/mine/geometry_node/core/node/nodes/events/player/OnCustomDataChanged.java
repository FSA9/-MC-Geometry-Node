package com.mine.geometry_node.core.node.nodes.events.player;

import com.mine.geometry_node.core.engine.blueprint.event.precheck.EventPrecheckFactory;
import com.mine.geometry_node.core.engine.blueprint.event.precheck.EventPrecheckProvider;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Listens to custom data writes dispatched to the graph's bound server player. */
public final class OnCustomDataChanged extends BaseEventNode implements EventPrecheckProvider {
    public static final String TYPE_ID = "on_custom_data_changed";
    public static final String VALUE_TYPE = "value_type";
    public static final String NEW_VALUE = "new_value";
    public static final String OLD_VALUE = "old_value";

    @Override
    public NodeDef getDefaultDefinition() {
        return definition(PortType.INTEGER);
    }

    @Override
    public NodeDef getDefinition(NodeData data) {
        return definition(parseValueType(data == null || data.inputs == null ? null : data.inputs.get(VALUE_TYPE)));
    }

    private static NodeDef definition(PortType type) {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node." + TYPE_ID))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(VALUE_TYPE, "value_type")
                        .input(StandardPorts.NAME, "name")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(NEW_VALUE, "new_value")
                        .output(OLD_VALUE, "old_value")
                        .build())
                .addStaticInput(PortDef.create(VALUE_TYPE, "geometry_node.port.value_type",
                                PortType.STRING, "INTEGER"), UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, new String[]{"INTEGER", "FLOAT", "BOOLEAN"},
                                PortMetaKeys.OPTION_LABELS, new String[]{
                                        PortType.INTEGER.getTranslationKey(),
                                        PortType.FLOAT.getTranslationKey(),
                                        PortType.BOOLEAN.getTranslationKey()}))
                .addStaticInput(StandardPorts.NAME.toInput(""), UIHint.INPUT)
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(output(NEW_VALUE, type))
                .addRow(output(OLD_VALUE, type))
                .build();
    }

    private static PortRow output(String id, PortType type) {
        // An absent event value must remain null, including for numeric/boolean outputs.
        return new PortRow(null, PortDef.create(id, "geometry_node.port." + id, type, null),
                UIHint.DEFAULT, null, null);
    }

    /** Parses the authored selector ID; an omitted selector uses the node default. */
    public static PortType parseValueType(@Nullable Object value) {
        if (value == null || "INTEGER".equals(value)) return PortType.INTEGER;
        if ("FLOAT".equals(value)) return PortType.FLOAT;
        if ("BOOLEAN".equals(value)) return PortType.BOOLEAN;
        throw new IllegalArgumentException("Unsupported custom data event value type: " + value);
    }

    /** Strict payload types: no numeric widening, string parsing or boolean coercion. */
    public static boolean matchesValueType(PortType type, @Nullable Object value) {
        return switch (type) {
            case INTEGER -> value instanceof Integer;
            case FLOAT -> value instanceof Float || value instanceof Double;
            case BOOLEAN -> value instanceof Boolean;
            default -> false;
        };
    }

    /** Pure payload filter for dispatchers; the key is compared verbatim, including empty keys. */
    public static boolean matchesEvent(String name, PortType type, @Nullable Map<String, Object> eventData) {
        return eventData != null && name.equals(eventData.get(StandardPorts.NAME.getId()))
                && matchesValueType(type, eventData.get(NEW_VALUE));
    }

    @Override
    public EventPrecheckFactory eventPrecheckFactory() {
        return context -> {
            PortType type = parseValueType(context.staticInput(VALUE_TYPE));
            Object configuredName = context.staticInput(StandardPorts.NAME);
            String name = configuredName == null ? "" : configuredName instanceof String key ? key : null;
            return (level, target, eventData) -> target instanceof ServerPlayer
                    && name != null && matchesEvent(name, type, eventData);
        };
    }

    @Override
    @Nullable
    public Object compute(GraphDataContext context, String portName) {
        Object value = super.compute(context, portName);
        if (!OLD_VALUE.equals(portName) || value == null) return value;
        // First writes and old values of another type have no old value for this listener.
        return matchesValueType(parseValueType(context.getStaticInput(VALUE_TYPE)), value) ? value : null;
    }
}
