package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

/** Runtime-only root/group path captured when a PowerShell run starts. */
public record BoundGraphScope(List<String> groupPath) {
    public BoundGraphScope { groupPath = List.copyOf(groupPath == null ? List.of() : groupPath); }

    public static BoundGraphScope capture(EditorContext context) {
        ArrayList<String> reversed = new ArrayList<>();
        for (NodeData node = context.getCurrentGroupNode(); node != null; node = node.parentGroupNode) reversed.add(node.id);
        Collections.reverse(reversed);
        return new BoundGraphScope(reversed);
    }

    public String id() {
        if (groupPath.isEmpty()) return "root";
        return "group:" + groupPath.stream().map(BoundGraphScope::encode).collect(java.util.stream.Collectors.joining("."));
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public NodeGraph resolve(NodeGraph root) {
        if (root == null) return null;
        if (groupPath.isEmpty()) return root;
        NodeData group = null;
        java.util.Map<String, NodeData> nodes = root.nodes;
        for (String id : groupPath) {
            group = nodes == null ? null : nodes.get(id);
            if (group == null || !group.isGroupNode() || group.subNodes == null) return null;
            nodes = group.subNodes;
        }
        NodeGraph scope = new NodeGraph();
        scope.graphKind = root.graphKind;
        scope.tags = root.tags;
        scope.comment = root.comment;
        scope.quest = root.quest;
        scope.version = root.version;
        scope.nodes = nodes;
        scope.frames = new LinkedHashMap<>();
        return scope;
    }
}
