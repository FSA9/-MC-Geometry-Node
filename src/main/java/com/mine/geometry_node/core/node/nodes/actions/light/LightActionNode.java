package com.mine.geometry_node.core.node.nodes.actions.light;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.light.GraphLightService;
import com.mine.geometry_node.core.engine.graph.light.LightDefinition;
import com.mine.geometry_node.core.engine.graph.resource.*;
import com.mine.geometry_node.core.node.definition.node.*;
import com.mine.geometry_node.core.node.definition.port.*;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.util.ResourceNodeSupport;
import com.mine.geometry_node.core.node.util.AnchorNodeSupport;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import com.mine.geometry_node.core.node.value.color.ColorValue;
import com.mine.geometry_node.core.node.value.light.LightConfig;
import com.mine.geometry_node.core.utils.RateLimitedLog;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import com.mine.geometry_node.core.node.document.NodeData;

/** Server-owned light creation, replacement and removal through resource IDs. */
public final class LightActionNode extends BaseNode {
    public enum Operation { CREATE, UPDATE, REMOVE }
    private final Operation operation;
    public LightActionNode(Operation operation) { this.operation = operation; }
    private String typeId() { return operation.name().toLowerCase(java.util.Locale.ROOT) + "_light"; }
    @Override public NodeDef getDefaultDefinition() {
        return definition(null);
    }
    @Override public NodeDef getDefinition(NodeData data) {
        return definition(data);
    }
    private NodeDef definition(NodeData data) {
        var b = NodeDef.builder(typeId(), NodeType.ACTION, Component.translatable("geometry_node.node." + typeId()))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null));
        if (operation == Operation.UPDATE)
            b.addMeta(com.mine.geometry_node.core.node.meta.SchemaKeys.HIDDEN_FROM_MENU, true);
        if (operation != Operation.CREATE) b.addPassthroughInput(StandardPorts.RESOURCE_ID.toInput(""), UIHint.INPUT);
        else b.addRow(ResourceNodeSupport.creationRow(StandardPorts.KEY.getId(), StandardPorts.RESOURCE_ID.getId()));
        if (operation == Operation.REMOVE) return LightNodeSchema.finish(b.build(), null, false);
        LightNodeSchema.addType(b);
        b.addPassthroughInput(StandardPorts.COLOR.toInput(ColorValue.WHITE), UIHint.INPUT);
        b.addRow(AnchorNodeSupport.row(StandardPorts.SPATIAL_ANCHOR));
        LightNodeSchema.addTransformInputs(b);
        LightConfigurationNode.add(b,"source_visible",PortType.BOOLEAN,false,UIHint.CHECKBOX);
        LightConfigurationNode.add(b,"intensity",PortType.FLOAT,1f,UIHint.INPUT);
        LightNodeSchema.addShape(b);
        b.addRow(PortRow.passthrough(LightConfigurationNode.port("config",PortType.LIGHT_CONFIG,LightConfig.DEFAULT),UIHint.DEFAULT,null,null));
        return LightNodeSchema.finish(b.build(), data, false);
    }
    private <T> T value(GraphDataContext c,String id,Class<T> type,T fallback) {
        T v=getInput(c,id,type); return v==null?fallback:v;
    }
    @Override public ExecutionResult execute(ExecutionContext c) {
        if (operation == Operation.CREATE) c.setNodeResult(StandardPorts.RESOURCE_ID.getId(), "");
        if (operation != Operation.REMOVE && AnchorNodeSupport.read(c, StandardPorts.SPATIAL_ANCHOR) == null)
            return next(StandardPorts.FLOW_OUT.getId());
        if (!(c.getLevel() instanceof ServerLevel level)) return next(StandardPorts.FLOW_OUT.getId());
        try {
            GraphResourceId id;
            if (operation == Operation.CREATE) {
                String stable=c.getCurrentNodeStableId();
                String input = ResourceNodeSupport.read(c, StandardPorts.KEY.getId());
                id=GraphResourceReferences.resolveCreate(c,stable==null||stable.isBlank()?Integer.toString(c.getCurrentNodeId()):stable,
                        GraphResourceTypeRegistry.LIGHT,input);
            } else id=GraphResourceReferences.resolveExisting(c, GraphResourceTypeRegistry.LIGHT,
                    value(c,StandardPorts.RESOURCE_ID.getId(),String.class,""));
            if (!id.type().equals(GraphResourceTypeRegistry.LIGHT)) throw new IllegalArgumentException("Resource is not a light");
            ServerLevel target=level.getServer().getLevel(id.scope().dimension());
            if (target==null) throw new IllegalArgumentException("Light dimension is not loaded");
            boolean success;
            if (operation==Operation.REMOVE) success=GraphLightService.INSTANCE.remove(target,id);
            else {
                var light=readDefinition(c);
                success=operation==Operation.CREATE?GraphLightService.INSTANCE.create(target,id,light):GraphLightService.INSTANCE.update(target,id,light);
            }
            if (!success) throw new IllegalArgumentException("Light resource operation was rejected");
            if (operation==Operation.CREATE) c.setNodeResult(StandardPorts.RESOURCE_ID.getId(),GraphResourceIdCodec.encode(id));
        } catch (IllegalArgumentException failure) {
            GraphLightService.INSTANCE.recordFailure(typeId(), failure.getMessage());
            if (RateLimitedLog.acquire(c,typeId()+":"+failure.getMessage()))
                GeometryNode.LOGGER.warn("Cannot execute {}: {}",typeId(),failure.getMessage());
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
    LightDefinition readDefinition(GraphDataContext c) {
        ColorValue color=value(c,StandardPorts.COLOR.getId(),ColorValue.class,ColorValue.WHITE);
        LightConfig config=value(c,"config",LightConfig.class,LightConfig.DEFAULT);
        SpatialAnchor anchor = AnchorNodeSupport.capture(c, StandardPorts.SPATIAL_ANCHOR);
        if (anchor == null) throw new IllegalArgumentException("Light spatial anchor is unavailable");
        config = config.withTransform(new LightConfig.Transform(anchor,
                AnchorNodeSupport.vector(c,AnchorNodeSupport.LOCAL_OFFSET),AnchorNodeSupport.vector(c,AnchorNodeSupport.LOCAL_ROTATION)));
        config = config.withShadow(new LightConfig.Shadow(config.shadow().enabled(),
                value(c,"source_size",Float.class,config.shadow().sourceSize())));
        config = config.withArea(new LightConfig.Area(value(c,"area_width",Float.class,config.area().width()),
                value(c,"area_height",Float.class,config.area().height())));
        return new LightDefinition(value(c,"type",String.class,"spot"),new Vec3(color.r(),color.g(),color.b()),
                value(c,"intensity",Float.class,1f),value(c,"range",Float.class,16f),value(c,"cone_angle",Float.class,45f),
                value(c,"edge_softness",Float.class,0.25f),AnchorNodeSupport.vector(c,AnchorNodeSupport.WORLD_OFFSET),
                AnchorNodeSupport.vector(c,AnchorNodeSupport.WORLD_ROTATION),config
                        .withSourceVisible(value(c,"source_visible",Boolean.class,config.sourceVisible())));
    }
    @Override public Object compute(GraphDataContext c,String port) {
        return StandardPorts.RESOURCE_ID.getId().equals(port)?c.getNodeResult(port):null;
    }
}
