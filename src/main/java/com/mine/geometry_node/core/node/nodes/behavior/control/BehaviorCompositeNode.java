package com.mine.geometry_node.core.node.nodes.behavior.control;

import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorControlExecutors;

/** Editor definition shared by the additional ordered behavior composites. */
public final class BehaviorCompositeNode extends BaseNode implements BehaviorExecutableNode {
    public enum Kind {
        REACTIVE_SEQUENCE("geometry_node:behavior_reactive_sequence"),
        PRIORITY_SELECTOR("geometry_node:behavior_priority_selector");

        private final String typeId;

        Kind(String typeId) {
            this.typeId = typeId;
        }

        public String typeId() {
            return typeId;
        }
    }

    private final Kind kind;

    public BehaviorCompositeNode(Kind kind) {
        this.kind = kind;
    }

    @Override
    public NodeDef getDefaultDefinition() {
        return definition(null);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        return definition(instanceData);
    }

    private NodeDef definition(NodeData instanceData) {
        return BehaviorCompositeDefinition.create(kind.typeId,
                "geometry_node.node." + path(kind.typeId), instanceData);
    }

    private static String path(String typeId) {
        return typeId.substring(typeId.indexOf(':') + 1);
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorControlExecutors.forKind(kind);
    }
}
