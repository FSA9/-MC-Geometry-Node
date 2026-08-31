package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.system.chunk_loading.EntityChunkLoadingConfig;
import com.mine.geometry_node.core.engine.system.chunk_loading.EntityChunkLoadingService;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;

/**
 * Enables or disables the single persistent strong-loading configuration of each target entity.
 */
public final class SetEntityChunkLoading extends BaseNode {
    public static final String TYPE_ID = "set_entity_chunk_loading";
    public static final String MODE_PORT = "mode";
    public static final String MODE_ENABLED = "enabled";
    public static final String MODE_DISABLED = "disabled";

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDefinition(MODE_ENABLED);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        String mode = instanceData != null && instanceData.inputs.get(MODE_PORT) instanceof String value
                ? value
                : MODE_ENABLED;
        return buildDefinition(MODE_DISABLED.equals(mode) ? MODE_DISABLED : MODE_ENABLED);
    }

    private NodeDef buildDefinition(String mode) {
        NodeComment.Builder comment = NodeComment.builder(TYPE_ID)
                .text("summary")
                .input(StandardPorts.FLOW_IN, "flow_in")
                .output(StandardPorts.FLOW_OUT, "flow_out")
                .output(StandardPorts.BOOL, "bool")
                .input(StandardPorts.ENTITY, "entity")
                .input(MODE_PORT, "mode");
        if (MODE_ENABLED.equals(mode)) {
            comment.input(StandardPorts.CHUNK_RADIUS, "chunk_radius");
        }

        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node.set_entity_chunk_loading"))
                .comment(comment.build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        PortDef.create(MODE_PORT, "geometry_node.port.entity_chunk_loading_mode", PortType.STRING, MODE_ENABLED).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(
                                PortMetaKeys.OPTIONS, new String[]{MODE_ENABLED, MODE_DISABLED},
                                PortMetaKeys.OPTION_LABELS, new String[]{
                                        "geometry_node.entity_chunk_loading.mode.enabled",
                                        "geometry_node.entity_chunk_loading.mode.disabled"
                                }
                        )
                ));

        if (MODE_ENABLED.equals(mode)) {
            builder.addRow(new PortRow(StandardPorts.CHUNK_RADIUS.toInput(EntityChunkLoadingConfig.MIN_RADIUS), null,
                    UIHint.INPUT, null, Map.of(
                    PortMetaKeys.NUMERIC_MIN, EntityChunkLoadingConfig.MIN_RADIUS,
                    PortMetaKeys.NUMERIC_MAX, EntityChunkLoadingConfig.MAX_RADIUS
            )));
        }
        return builder.build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        String mode = getInput(context, MODE_PORT, String.class);
        boolean enable = !MODE_DISABLED.equals(mode);
        int radius = Math.clamp(valueOr(getInput(context, StandardPorts.CHUNK_RADIUS.getId(), Integer.class),
                EntityChunkLoadingConfig.MIN_RADIUS),
                EntityChunkLoadingConfig.MIN_RADIUS, EntityChunkLoadingConfig.MAX_RADIUS);

        boolean success = false;
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        for (Entity entity : entities) {
            success |= enable
                    ? EntityChunkLoadingService.INSTANCE.configure(entity, radius)
                    : EntityChunkLoadingService.INSTANCE.disable(entity);
        }
        context.setTempData(tempKey(context), success);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        return StandardPorts.BOOL.getId().equals(portName) ? context.getTempData(tempKey(context)) : null;
    }

    private static String tempKey(ExecutionContext context) {
        return TYPE_ID + ":" + context.getCurrentNodeId() + ":" + StandardPorts.BOOL.getId();
    }

    private static <T> T valueOr(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
