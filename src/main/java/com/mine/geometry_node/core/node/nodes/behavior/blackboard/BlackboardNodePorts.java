package com.mine.geometry_node.core.node.nodes.behavior.blackboard;

import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorNodeTypes;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.UIHint;

import java.util.Locale;
import java.util.Map;

public final class BlackboardNodePorts {
    private static final String[] OPTIONS = {"instance", "owner", "shared", "group", "world"};

    private BlackboardNodePorts() {
    }

    public static PortRow scopeRow() {
        return new PortRow(PortDef.create(BehaviorNodeTypes.BLACKBOARD_SCOPE_PORT,
                "geometry_node.port." + BehaviorNodeTypes.BLACKBOARD_SCOPE_PORT,
                PortType.STRING, "instance").hiddenPin(), null, UIHint.SELECT, null,
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
        if (!(value instanceof String text)) return null;
        try {
            return ScopedStateScope.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
