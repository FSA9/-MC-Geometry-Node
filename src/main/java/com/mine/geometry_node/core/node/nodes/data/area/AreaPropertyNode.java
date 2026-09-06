package com.mine.geometry_node.core.node.nodes.data.area;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaRef;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResource;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResourceStore;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

abstract class AreaPropertyNode extends BaseNode {
    private final String typeId;
    private final PortDef output;

    protected AreaPropertyNode(String typeId, PortDef output) {
        this.typeId = typeId;
        this.output = output;
    }

    @Override
    public final NodeDef getDefaultDefinition() {
        return NodeDef.builder(typeId, NodeType.DATA, Component.translatable("geometry_node.node." + typeId))
                .addRow(new PortRow(null, output, UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.AREA.toInput(), UIHint.DEFAULT, null, null)
                .build();
    }

    @Override
    public final @Nullable Object compute(GraphDataContext context, String portName) {
        if (!output.id().equals(portName)) return null;
        AreaRef reference = getInput(context, StandardPorts.AREA.getId(), AreaRef.class);
        ServerLevel hostLevel = context.getLevel();
        if (reference == null || hostLevel == null) return null;
        AreaResource resource = AreaResourceStore.INSTANCE.get(hostLevel.getServer(), reference);
        return resource != null ? read(context, resource) : null;
    }

    protected abstract @Nullable Object read(GraphDataContext context, AreaResource resource);

    protected static @Nullable AreaResource.Resolved resolve(GraphDataContext context, AreaResource resource) {
        ServerLevel hostLevel = context.getLevel();
        if (hostLevel == null) return null;
        ServerLevel areaLevel = hostLevel.getServer().getLevel(resource.address().dimension());
        return areaLevel != null ? resource.resolve(areaLevel) : null;
    }
}
