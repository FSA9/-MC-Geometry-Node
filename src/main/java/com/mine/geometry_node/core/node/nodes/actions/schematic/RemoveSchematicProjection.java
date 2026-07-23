package com.mine.geometry_node.core.node.nodes.actions.schematic;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class RemoveSchematicProjection extends BaseNode {
    public static final String TYPE_ID = "remove_schematic_projection";

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                删除指定 key 的结构投影视觉。
                只影响客户端 debug 投影，不会修改世界方块，也不会删除放置记录。
                删除包会发送给所有在线玩家，确保所有客户端缓存中的同 key 投影都被移除。""";

        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.remove_schematic_projection"))
                .comment(comment)
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.KEY.toInput(""), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        boolean success = false;
        if (context.getLevel() instanceof ServerLevel level) {
            String key = getInput(context, StandardPorts.KEY.getId(), String.class);
            if (key != null && !key.trim().isEmpty()) {
                success = _SchematicActionUtils.sendProjectionRemoval(context, level, key.trim());
            }
        }
        context.setTempData(tempKey(context, StandardPorts.BOOL.getId()), success);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.BOOL.getId().equals(portName)) {
            Object value = context.getTempData(tempKey(context, portName));
            return value instanceof Boolean bool && bool;
        }
        return null;
    }

    private static String tempKey(ExecutionContext context, String port) {
        return TYPE_ID + ":" + context.getCurrentNodeId() + ":" + port;
    }
}
