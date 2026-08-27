package com.mine.geometry_node.core.node.nodes.behavior.control;

import com.mine.geometry_node.core.engine.behavior.document.BehaviorNodeTypes;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;

/** Editor definition shared by the additional ordered behavior composites. */
public final class BehaviorCompositeNode extends BaseNode {
    public enum Kind {
        REACTIVE_SEQUENCE(BehaviorNodeTypes.REACTIVE_SEQUENCE),
        PRIORITY_SELECTOR(BehaviorNodeTypes.PRIORITY_SELECTOR);

        private final String typeId;

        Kind(String typeId) {
            this.typeId = typeId;
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
}
