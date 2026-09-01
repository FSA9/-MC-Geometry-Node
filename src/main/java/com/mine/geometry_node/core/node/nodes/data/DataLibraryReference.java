package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryEntryKey;
import com.mine.geometry_node.core.engine.system.data.library.RemoteDataLibraryService;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.document.NodeData;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * Reads one entry from the server Data Library by stable type and UUID.
 * The entry itself is deliberately not embedded in the graph document.
 */
public final class DataLibraryReference extends com.mine.geometry_node.core.node.nodes.BaseNode {
    public static final String TYPE_ID = "data_library_reference";
    public static final String ENTRY_TYPE = "entry_type";
    public static final String ENTRY_ID = StandardPorts.ENTRY_ID.getId();
    public static final String VALUE = "value";

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDefinition(null);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        return buildDefinition(instanceData);
    }

    private NodeDef buildDefinition(@Nullable NodeData instanceData) {
        PortType type = resolveType(instanceData == null || instanceData.inputs == null
                ? null : instanceData.inputs.get(ENTRY_TYPE));
        return NodeDef.builder(TYPE_ID, NodeType.DATA,
                        Component.translatable("geometry_node.node.data_library_reference"))
                .addRow(new PortRow(
                        PortDef.create(ENTRY_TYPE, "geometry_node.port.data_library_entry_type",
                                PortType.STRING, ""),
                        null, UIHint.INPUT, null, null))
                .addRow(new PortRow(
                        PortDef.create(ENTRY_ID, "geometry_node.port.entry_id",
                                PortType.STRING, ""),
                        new PortDef(VALUE,
                                Component.translatable("geometry_node.port.data_library_value"),
                                type, type.getDefaultValue(), false),
                        UIHint.INPUT, null, null))
                .build();
    }

    @Override
    @Nullable
    public Object compute(ExecutionContext context, String portName) {
        if (!VALUE.equals(portName)) return null;

        PortType type = resolveType(getInput(context, ENTRY_TYPE, String.class));
        UUID id = parseUuid(getInput(context, ENTRY_ID, String.class));
        if (id == null || type == PortType.ANY || !com.mine.geometry_node.core.engine.system.data.library.DataLibraryTypes.supports(type)) {
            return null;
        }
        return RemoteDataLibraryService.INSTANCE.resolve(
                context.getLevel().getServer(), new DataLibraryEntryKey(type, id));
    }

    private static PortType resolveType(Object raw) {
        if (!(raw instanceof String value) || value.isBlank()) return PortType.ANY;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return PortType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            for (PortType type : PortType.values()) {
                if (type.name().equalsIgnoreCase(value.trim())) return type;
            }
            return PortType.ANY;
        }
    }

    @Nullable
    private static UUID parseUuid(Object raw) {
        if (!(raw instanceof String value) || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
