package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
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
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.Map;

/** Creates or replaces one server Data Library entry by path + type + public key. */
public final class SetDataLibraryEntry extends BaseNode {
    public static final String TYPE_ID = "set_data_library_entry";
    public static final String ENTRY_TYPE = "entry_type";
    private static final PortType DEFAULT_TYPE = PortType.STRING;

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA,
                        Component.translatable("geometry_node.node.set_data_library_entry"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(StandardPorts.PATH, "path")
                        .input(ENTRY_TYPE, "type")
                        .input(StandardPorts.KEY, "key")
                        .input(StandardPorts.ANY_VALUE, "value")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(),
                        UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        PortDef.create(ENTRY_TYPE, "geometry_node.port.data_library_entry_type",
                                PortType.STRING, DEFAULT_TYPE.name()).hiddenPin(),
                        null, UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, DataLibraryTypes.optionIds())))
                .addRow(new PortRow(StandardPorts.PATH.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.KEY.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.ANY_VALUE.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        PortType type = DataLibraryTypes.resolve(getRawInput(context, ENTRY_TYPE));
        if (type == null) type = DEFAULT_TYPE;
        String path = getInput(context, StandardPorts.PATH.getId(), String.class);
        String key = getInput(context, StandardPorts.KEY.getId(), String.class);
        Object value = getRawInput(context, StandardPorts.ANY_VALUE.getId());
        try {
            RemoteDataLibraryService.INSTANCE.upsert(
                    context.getLevel().getServer(), path == null ? "" : path,
                    type, key == null ? "" : key, value);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to update the server Data Library", exception);
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
