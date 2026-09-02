package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.google.gson.JsonObject;
import com.mine.geometry_node.client.ai.command.CommandResult;
import com.mine.geometry_node.client.ai.command.GraphCommandTarget;
import com.mine.geometry_node.client.ai.command.GraphQueryTarget;
import com.mine.geometry_node.client.ai.command.NodeCatalogIndex;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdAddNode;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdConnect;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdRemoveNodes;
import com.mine.geometry_node.client.ui.document.GraphSession;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Adapts the editor document to the runtime-neutral graph command target. */
public record TerminalCommandTarget(GraphSession session) implements GraphCommandTarget, GraphQueryTarget {
    @Override public boolean hasGraph() { return session != null; }

    @Override
    public Collection<String> registeredNodeTypeIds() {
        return NodeCatalogIndex.entries().stream().map(NodeCatalogIndex.Entry::typeId).toList();
    }

    @Override
    public Collection<String> nodeIds() {
        return session == null ? List.of() : List.copyOf(session.editorContext.getCurrentGraph().nodes.keySet());
    }

    @Override
    public Collection<String> portIds(String nodeId, PortDirection direction) {
        if (session == null) return List.of();
        NodeData node = session.editorContext.getCurrentGraph().getNode(nodeId);
        if (node == null) return List.of();
        NodeDef definition = NodeRegistry.INSTANCE.resolveDefinition(node);
        if (definition == null) return List.of();
        List<String> ids = new ArrayList<>();
        for (PortRow row : definition.rows()) {
            PortDef port = direction == PortDirection.INPUT ? row.leftPort() : row.rightPort();
            if (port != null) ids.add(port.id());
        }
        return List.copyOf(ids);
    }

    @Override
    public CommandResult addNode(String typeId, double x, double y, String requestedNodeId) {
        if (session == null) return graphRequired();
        String canonicalTypeId;
        try {
            canonicalTypeId = NodeCatalogIndex.canonicalTypeId(typeId);
        } catch (IllegalArgumentException failure) {
            return CommandResult.failure("NODE_TYPE_INVALID", failure.getMessage());
        }
        if (!NodeRegistry.INSTANCE.has(canonicalTypeId)) {
            return CommandResult.failure("NODE_TYPE_NOT_FOUND",
                    "节点生成失败: 注册表中不存在类型为 '" + typeId + "' 的节点");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x) > Float.MAX_VALUE || Math.abs(y) > Float.MAX_VALUE) {
            return CommandResult.failure("ARGUMENT_INVALID", "参数格式错误: 坐标 x 和 y 超出允许范围");
        }
        String nodeId = requestedNodeId == null || requestedNodeId.isBlank()
                ? UUID.randomUUID().toString() : requestedNodeId;
        if (session.editorContext.getCurrentGraph().getNode(nodeId) != null) {
            return CommandResult.failure("NODE_ID_CONFLICT",
                    "节点生成失败: 当前画布中已经存在 ID 为 '" + nodeId + "' 的节点");
        }
        NodeData data = new NodeData(nodeId, canonicalTypeId, (float) x, (float) y);
        boolean executed = session.editorContext.getCommandManager().execute(
                new CmdAddNode(session.editorContext.getGraphController(), data));
        if (!executed) return CommandResult.failure("COMMAND_REJECTED", "节点生成失败: 当前图状态拒绝执行");
        JsonObject result = new JsonObject();
        result.addProperty("type_id", typeId);
        result.addProperty("node_id", nodeId);
        return CommandResult.success("NODE_ADDED", "节点添加成功 | Type: " + typeId + " | ID: " + nodeId, result);
    }

    @Override
    public CommandResult deleteNode(String nodeId) {
        if (session == null) return graphRequired();
        if (session.editorContext.getCurrentGraph().getNode(nodeId) == null) {
            return CommandResult.failure("NODE_NOT_FOUND", "删除失败: 画布中找不到 ID 为 '" + nodeId + "' 的节点");
        }
        boolean executed = session.editorContext.getCommandManager().execute(new CmdRemoveNodes(
                session.editorContext.getGraphController(), session.editorContext.getCurrentGraph(),
                Collections.singletonList(nodeId)));
        if (!executed) return CommandResult.failure("COMMAND_REJECTED", "删除失败: 当前图状态拒绝执行");
        JsonObject result = new JsonObject();
        result.addProperty("node_id", nodeId);
        return CommandResult.success("NODE_DELETED", "节点已删除 | ID: " + nodeId, result);
    }

    @Override
    public CommandResult connect(String outputNodeId, String outputPortId, String inputNodeId, String inputPortId) {
        if (session == null) return graphRequired();
        if (session.editorContext.getCurrentGraph().getNode(outputNodeId) == null) {
            return CommandResult.failure("NODE_NOT_FOUND", "连线失败: 找不到输出端节点 ID '" + outputNodeId + "'");
        }
        if (session.editorContext.getCurrentGraph().getNode(inputNodeId) == null) {
            return CommandResult.failure("NODE_NOT_FOUND", "连线失败: 找不到输入端节点 ID '" + inputNodeId + "'");
        }
        boolean executed = session.editorContext.getCommandManager().execute(new CmdConnect(
                session.editorContext.getGraphController(), session.editorContext.getCurrentGraph(),
                outputNodeId, outputPortId, inputNodeId, inputPortId));
        if (!executed) {
            return CommandResult.failure("PORT_CONNECTION_INVALID", String.format(Locale.ROOT,
                    "连线失败: 端口不存在或类型不兼容 | %s[%s] -> %s[%s]",
                    outputNodeId, outputPortId, inputNodeId, inputPortId));
        }
        JsonObject result = new JsonObject();
        result.addProperty("output_node_id", outputNodeId);
        result.addProperty("output_port_id", outputPortId);
        result.addProperty("input_node_id", inputNodeId);
        result.addProperty("input_port_id", inputPortId);
        return CommandResult.success("PORTS_CONNECTED", String.format(Locale.ROOT,
                "连线成功 | %s[%s] -> %s[%s]", outputNodeId, outputPortId, inputNodeId, inputPortId), result);
    }

    @Override
    public CommandResult searchNodeTypes(String query, String path, boolean recursive, int offset, int limit) {
        return queries().searchNodeTypes(query, path, recursive, offset, limit);
    }

    @Override
    public CommandResult browseNodeCatalog(String path, boolean recursive, int offset, int limit) {
        return queries().browseNodeCatalog(path, recursive, offset, limit);
    }

    @Override
    public CommandResult getNodeTypeDetails(String typeId) {
        return queries().getNodeTypeDetails(typeId);
    }

    @Override
    public CommandResult getNodeTypePortOptions(String typeId, String portId, String query, int offset, int limit) {
        return queries().getNodeTypePortOptions(typeId, portId, query, offset, limit);
    }

    @Override
    public CommandResult queryGraphNodes(java.util.List<String> nodeIds, java.util.List<String> typeIds,
                                         String directory, String query, String commentFilter,
                                         String connectionState, java.util.List<String> select,
                                         String connectionDirection, java.util.List<String> connectionKinds,
                                         int offset, int limit) {
        return queries().queryGraphNodes(nodeIds, typeIds, directory, query, commentFilter, connectionState,
                select, connectionDirection, connectionKinds, offset, limit);
    }

    @Override
    public CommandResult getGraphStats(String typeId, String category, String groupBy, int offset, int limit) {
        return queries().getGraphStats(typeId, category, groupBy, offset, limit);
    }

    @Override
    public CommandResult validateGraph(int offset, int limit) {
        return queries().validateGraph(offset, limit);
    }

    @Override
    public CommandResult getPortOptions(String nodeId, String portId, String query, int offset, int limit) {
        return queries().getPortOptions(nodeId, portId, query, offset, limit);
    }

    private TerminalGraphQueryService queries() {
        return new TerminalGraphQueryService(session.editorContext.getCurrentGraph());
    }

    private static CommandResult graphRequired() {
        return CommandResult.failure("GRAPH_SESSION_REQUIRED", "执行失败: 当前没有打开且活跃的蓝图会话");
    }
}
