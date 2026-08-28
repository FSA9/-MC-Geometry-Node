package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateTarget;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public final class ClearScopedState extends BaseNode {
    public static final String TYPE_ID = "clear_scoped_state";

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDefinition(ScopedStateNodeSupport.DEFAULT_SCOPE);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        return buildDefinition(ScopedStateNodeSupport.selectedScope(instanceData));
    }

    private NodeDef buildDefinition(ScopedStateScope scope) {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.DATA,
                        Component.translatable("geometry_node.node.clear_scoped_state"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(StandardPorts.NAME, "name")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(),
                        StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(ScopedStateNodeSupport.scopeRow(PortDef.create(
                        ScopedStateNodeSupport.SCOPE_PORT,
                        "geometry_node.port.state_scope", PortType.STRING)));
        if (ScopedStateNodeSupport.usesEntity(scope)) {
            builder.addRow(new PortRow(StandardPorts.ENTITY.toInput(),
                    StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null));
        } else if (scope == ScopedStateScope.WORLD) {
            builder.addRow(ScopedStateNodeSupport.dimensionRow(StandardPorts.DIMENSION.toOutput()));
        }
        return builder
                .addRow(new PortRow(StandardPorts.NAME.toInput(),
                        StandardPorts.NAME.toOutput(), UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        String key = ScopedStateNodeSupport.requireKey(
                getInput(context, StandardPorts.NAME.getId(), String.class));
        ScopedStateScope scope = ScopedStateNodeSupport.selectedScope(context);
        Entity entity = ScopedStateNodeSupport.usesEntity(scope)
                ? getInput(context, StandardPorts.ENTITY.getId(), Entity.class) : null;
        ScopedStateTarget target = ScopedStateNodeSupport.requireTarget(context, scope, entity);

        context.setTempData(tempKey(context, ScopedStateNodeSupport.SCOPE_PORT), scope);
        if (entity != null) {
            context.setTempData(tempKey(context, StandardPorts.ENTITY.getId()), entity);
        }
        if (scope == ScopedStateScope.WORLD) {
            context.setTempData(tempKey(context, StandardPorts.DIMENSION.getId()),
                    context.getStaticInput(StandardPorts.DIMENSION.getId()));
        }
        context.setTempData(tempKey(context, StandardPorts.NAME.getId()), key);

        context.clearScopedState(target, key);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (ScopedStateNodeSupport.SCOPE_PORT.equals(portName)
                || StandardPorts.ENTITY.getId().equals(portName)
                || StandardPorts.DIMENSION.getId().equals(portName)
                || StandardPorts.NAME.getId().equals(portName)) {
            return context.getTempData(tempKey(context, portName));
        }
        return null;
    }

    private String tempKey(ExecutionContext context, String portName) {
        return TYPE_ID + ":" + context.getCurrentNodeId() + ":" + portName;
    }
}
