package com.mine.geometry_node.core.node.nodes.actions.light;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.light.GraphLightService;
import com.mine.geometry_node.core.engine.graph.light.LightDefinition;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceReferences;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.*;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.util.AnchorNodeSupport;
import com.mine.geometry_node.core.node.value.color.ColorValue;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.utils.RateLimitedLog;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Resource mutation occurs only when execution reaches this action. */
public final class SetLightPropertyNode extends BaseNode {
    public enum Property {
        COLOR, INTENSITY, POSITION, ROTATION, PARAMETERS, SPOT, SOURCE_VISIBLE,
        TRANSFORM, SHADOW, VOLUME, PROJECTION, QUALITY, SURFACE_QUALITY, VOLUME_QUALITY, LIFECYCLE
    }

    private final Property property;

    public SetLightPropertyNode(Property property) { this.property = property; }

    private String typeId() { return "set_light_" + property.name().toLowerCase(java.util.Locale.ROOT); }

    @Override public NodeDef getDefaultDefinition() {
        return definition(null);
    }
    @Override public NodeDef getDefinition(NodeData data) {
        return definition(data);
    }
    private NodeDef definition(NodeData data) {
        String id = typeId();
        var b = NodeDef.builder(id, NodeType.ACTION, Component.translatable("geometry_node.node." + id))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.RESOURCE_ID.toInput(""), UIHint.INPUT);
        if (property == Property.SPOT || property == Property.SHADOW || property == Property.QUALITY)
            b.addMeta(com.mine.geometry_node.core.node.meta.SchemaKeys.HIDDEN_FROM_MENU, true);
        switch (property) {
            case PARAMETERS -> {
                LightNodeSchema.addType(b);
                LightConfigurationNode.add(b, "source_visible", PortType.BOOLEAN, false, UIHint.CHECKBOX);
                LightConfigurationNode.add(b, "intensity", PortType.FLOAT, 1f, UIHint.INPUT);
                LightNodeSchema.addShape(b);
                LightConfigurationNode.add(b, "shadow_enabled", PortType.BOOLEAN, true, UIHint.CHECKBOX);
            }
            case COLOR -> b.addPassthroughInput(StandardPorts.COLOR.toInput(ColorValue.WHITE), UIHint.INPUT);
            case SOURCE_VISIBLE -> LightConfigurationNode.add(b, "source_visible", PortType.BOOLEAN, false, UIHint.CHECKBOX);
            case INTENSITY -> LightConfigurationNode.add(b, "intensity", PortType.FLOAT, 1f, UIHint.INPUT);
            case POSITION -> b.addPassthroughInput(LightNodeSchema.transformPort(AnchorNodeSupport.WORLD_OFFSET), UIHint.VECTOR);
            case ROTATION -> b.addPassthroughInput(LightNodeSchema.transformPort(AnchorNodeSupport.WORLD_ROTATION), UIHint.VECTOR);
            case TRANSFORM -> {
                b.addRow(AnchorNodeSupport.row(StandardPorts.SPATIAL_ANCHOR));
                LightNodeSchema.addTransformInputs(b);
            }
            case SPOT -> {
                LightConfigurationNode.add(b, "range", PortType.FLOAT, 16f, UIHint.INPUT);
                LightConfigurationNode.add(b, "cone_angle", PortType.FLOAT, 45f, UIHint.INPUT);
                LightConfigurationNode.add(b, "edge_softness", PortType.FLOAT, 0.25f, UIHint.INPUT);
            }
            default -> LightConfigurationNode.addGroupInputs(b, LightConfigurationNode.Group.valueOf(property.name()));
        }
        return LightNodeSchema.finish(b.build(), data, property == Property.VOLUME);
    }

    @Override public ExecutionResult execute(ExecutionContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return next(StandardPorts.FLOW_OUT.getId());
        try {
            var id = GraphResourceReferences.resolveExisting(context, GraphResourceTypeRegistry.LIGHT,
                    value(context, StandardPorts.RESOURCE_ID.getId(), String.class, ""));
            if (!GraphResourceTypeRegistry.LIGHT.equals(id.type())) throw new IllegalArgumentException("Resource is not a light");
            ServerLevel target = level.getServer().getLevel(id.scope().dimension());
            if (target == null) throw new IllegalArgumentException("Light dimension is not loaded");
            if (!GraphLightService.INSTANCE.mutate(target, id, current -> apply(context, current), property == Property.LIFECYCLE))
                throw new IllegalArgumentException("Light resource does not exist");
        } catch (IllegalArgumentException failure) {
            GraphLightService.INSTANCE.recordFailure(typeId(), failure.getMessage());
            if (RateLimitedLog.acquire(context, typeId() + ":" + failure.getMessage()))
                GeometryNode.LOGGER.warn("Cannot execute {}: {}", typeId(), failure.getMessage());
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }

    LightDefinition apply(GraphDataContext context, LightDefinition current) {
        Vec3 color = current.color(), position = current.position(), rotation = current.rotation();
        float intensity = current.intensity(), range = current.range(), cone = current.coneAngle(), edge = current.edgeSoftness();
        switch (property) {
            case PARAMETERS -> {
                var config = current.config().withShadow(new com.mine.geometry_node.core.node.value.light.LightConfig.Shadow(
                        value(context, "shadow_enabled", Boolean.class, true), value(context, "source_size", Float.class, 0.02f)))
                        .withSourceVisible(value(context, "source_visible", Boolean.class, current.config().sourceVisible()))
                        .withArea(new com.mine.geometry_node.core.node.value.light.LightConfig.Area(
                                value(context, "area_width", Float.class, current.config().area().width()),
                                value(context, "area_height", Float.class, current.config().area().height())));
                return new LightDefinition(value(context, "type", String.class, "spot"), color,
                        value(context, "intensity", Float.class, intensity),
                        value(context, "range", Float.class, 16f), value(context, "cone_angle", Float.class, 45f),
                        value(context, "edge_softness", Float.class, 0.25f), position, rotation, config);
            }
            case SOURCE_VISIBLE -> {
                return current.withConfig(current.config().withSourceVisible(value(context, "source_visible", Boolean.class, false)));
            }
            case COLOR -> {
                var value = value(context, StandardPorts.COLOR.getId(), ColorValue.class, ColorValue.WHITE);
                color = new Vec3(value.r(), value.g(), value.b());
            }
            case INTENSITY -> intensity = value(context, "intensity", Float.class, 1f);
            case POSITION -> position = AnchorNodeSupport.vector(context,AnchorNodeSupport.WORLD_OFFSET);
            case ROTATION -> rotation = AnchorNodeSupport.vector(context,AnchorNodeSupport.WORLD_ROTATION);
            case TRANSFORM -> {
                var config=new LightConfigurationNode(LightConfigurationNode.Group.TRANSFORM).apply(context,current.config());
                var anchor=AnchorNodeSupport.capture(context,StandardPorts.SPATIAL_ANCHOR);
                if(anchor==null)throw new IllegalArgumentException("Light spatial anchor is unavailable");
                config=config.withTransform(new com.mine.geometry_node.core.node.value.light.LightConfig.Transform(anchor,config.transform().position(),config.transform().rotation()));
                return new LightDefinition(current.type(),color,intensity,range,cone,edge,
                    AnchorNodeSupport.vector(context,AnchorNodeSupport.WORLD_OFFSET),
                    AnchorNodeSupport.vector(context,AnchorNodeSupport.WORLD_ROTATION),config);
            }
            case SPOT -> {
                range = value(context, "range", Float.class, 16f);
                cone = value(context, "cone_angle", Float.class, 45f);
                edge = value(context, "edge_softness", Float.class, 0.25f);
            }
            default -> {
                var group = LightConfigurationNode.Group.valueOf(property.name());
                return current.withConfig(new LightConfigurationNode(group).apply(context, current.config()));
            }
        }
        return new LightDefinition(current.type(), color, intensity, range, cone, edge, position, rotation, current.config());
    }

    private <T> T value(GraphDataContext context, String id, Class<T> type, T fallback) {
        T value = getInput(context, id, type);
        return value == null ? fallback : value;
    }

    @Override public Object compute(GraphDataContext context, String port) {
        return StandardPorts.RESOURCE_ID.getId().equals(port)
                ? getInput(context, StandardPorts.RESOURCE_ID.getId(), Object.class) : null;
    }
}
