package com.mine.geometry_node.core.node.nodes.behavior.blackboard;

import com.mine.geometry_node.core.node.definition.port.StandardPorts;

import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.UIHint;

import java.util.Map;

public final class BlackboardNodePorts {
    private static final String[] OPTIONS = ScopedStateScope.optionIds(ScopedStateScope.ALL);

    private BlackboardNodePorts() {
    }

    public static PortRow scopeRow() {
        return new PortRow(StandardPorts.BLACKBOARD_SCOPE.toInput("instance").hiddenPin(),
                null, UIHint.SELECT, null,
                Map.of(PortMetaKeys.OPTIONS, OPTIONS));
    }

    public static NodeComment comment(String nodeTypeId) {
        String path = nodeTypeId.substring(nodeTypeId.indexOf(':') + 1);
        return NodeComment.builder(path)
                .text("summary")
                .text("geometry_node.node.behavior_blackboard.comment.dynamic")
                .text("geometry_node.node.behavior_blackboard.comment.instance")
                .text("geometry_node.node.behavior_blackboard.comment.owner")
                .text("geometry_node.node.behavior_blackboard.comment.shared")
                .text("geometry_node.node.behavior_blackboard.comment.group")
                .text("geometry_node.node.behavior_blackboard.comment.world")
                .build();
    }

    public static ScopedStateScope scope(Object value) {
        return ScopedStateScope.parse(value);
    }
}
