package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryTypes;
import com.mine.geometry_node.core.engine.system.data.library.RemoteDataLibraryService;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
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

import java.util.UUID;

/**
 * Reads one entry from the server Data Library by its stable UUID.
 * The entry itself is deliberately not embedded in the graph document.
 */
public final class DataLibraryReference extends com.mine.geometry_node.core.node.nodes.BaseNode {
    public static final String TYPE_ID = "data_library_reference";
    public static final String ENTRY_TYPE = "entry_type";
    public static final String INFO_WIDGET_ID = "data_library_reference_info";
    public static final String VALUE = "value";
    private static final PortType DEFAULT_TYPE = PortType.STRING;

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
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(ENTRY_TYPE, "type")
                        .input(StandardPorts.ENTRY_ID, "id")
                        .output(VALUE, "value")
                        .build())
                .addRow(new PortRow(null, new PortDef(VALUE,
                                Component.translatable("geometry_node.port.data_library_value"),
                                type, type.getDefaultValue(), false), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTRY_ID.toInput(), UIHint.INPUT, null, null)
                .addRow(new PortRow(null, null, UIHint.CUSTOM, INFO_WIDGET_ID, null))
                .build();
    }

    @Override
    @Nullable
    public Object compute(GraphDataContext context, String portName) {
        if (!VALUE.equals(portName)) return null;

        UUID id = parseUuid(getInput(context, StandardPorts.ENTRY_ID.getId(), String.class));
        if (id == null) return null;
        PortType expectedType = resolveType(getInput(context, ENTRY_TYPE, String.class));
        return RemoteDataLibraryService.INSTANCE.resolve(
                context.getLevel().getServer(), id, expectedType);
    }

    private static PortType resolveType(Object raw) {
        PortType type = DataLibraryTypes.resolve(raw);
        return type != null ? type : DEFAULT_TYPE;
    }

    @Nullable
    private static UUID parseUuid(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
