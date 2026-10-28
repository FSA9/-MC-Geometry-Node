package com.mine.geometry_node.core.node.nodes.data.world;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.value.dynamic.DynamicData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/** A reusable positioning reference; consumer offsets remain independent live inputs. */
public final class CreateSpatialAnchor extends BaseNode {
    public static final String TYPE_ID = "create_spatial_anchor";
    public static final String ORIGIN = "anchor_origin";
    public static final String FOLLOW_POSITION = "follow_position";
    public static final String FOLLOW_ORIENTATION = "follow_orientation";
    public static final String ORIENTATION_SOURCE = "orientation_source";
    public static final String WORLD = "world";
    public static final String ENTITY = "entity";

    @Override public NodeDef getDefaultDefinition() { return definition(false); }

    @Override public NodeDef getDefinition(NodeData data) {
        return definition(data != null && ENTITY.equals(data.inputs.get(StandardPorts.ANCHOR_TYPE.getId())));
    }

    private NodeDef definition(boolean entity) {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.DATA,
                        Component.translatable("geometry_node.node.create_spatial_anchor"))
                .comment(NodeComment.builder(TYPE_ID).text("summary").build())
                .addRow(new PortRow(null, StandardPorts.SPATIAL_ANCHOR.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ANCHOR_TYPE.toInput(entity ? ENTITY : WORLD).hiddenPin(),
                        UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, new String[]{WORLD, ENTITY},
                                PortMetaKeys.OPTION_LABELS, new String[]{"geometry_node.anchor.world", "geometry_node.anchor.entity"}));
        if (entity) {
            builder.addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT)
                    .addPassthroughInput(new PortDef(ORIGIN, Component.translatable("geometry_node.anchor.origin"),
                                    PortType.STRING, "base", true), UIHint.SELECT, null,
                            Map.of(PortMetaKeys.OPTIONS, new String[]{"base", "eye"},
                                    PortMetaKeys.OPTION_LABELS, new String[]{"geometry_node.anchor.base", "geometry_node.anchor.eye"}))
                    .addPassthroughInput(new PortDef(FOLLOW_POSITION, Component.translatable("geometry_node.anchor.follow_position"),
                            PortType.BOOLEAN, true, true), UIHint.CHECKBOX)
                    .addPassthroughInput(new PortDef(FOLLOW_ORIENTATION, Component.translatable("geometry_node.anchor.follow_orientation"),
                            PortType.BOOLEAN, false, true), UIHint.CHECKBOX)
                    .addPassthroughInput(new PortDef(ORIENTATION_SOURCE, Component.translatable("geometry_node.anchor.orientation_source"),
                            PortType.STRING, "body", true), UIHint.SELECT, null,
                            Map.of(PortMetaKeys.OPTIONS, new String[]{"body", "view"},
                                    PortMetaKeys.OPTION_LABELS, new String[]{"geometry_node.anchor.body", "geometry_node.anchor.view"}));
        } else {
            builder.addPassthroughInput(StandardPorts.DIMENSION.toInput("minecraft:overworld"), UIHint.SELECT, null,
                            Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, RegistryDataManager.DIMENSION_REGISTRY_ID,
                                    PortMetaKeys.CONNECTABLE_SELECT, true))
                    .addPassthroughInput(StandardPorts.XYZ.toInput(Vec3.ZERO).liveExpression(), UIHint.VECTOR);
        }
        return builder.build();
    }

    @Override public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.SPATIAL_ANCHOR.getId().equals(portName)) return null;
        if (ENTITY.equals(getInput(context, StandardPorts.ANCHOR_TYPE.getId(), String.class))) {
            Entity entity = getInputFromList(context, StandardPorts.ENTITY.getId(), 0, Entity.class);
            if (entity == null || entity.isRemoved()) return null;
            SpatialAnchor.Origin origin = "eye".equals(getInput(context, ORIGIN, String.class))
                    ? SpatialAnchor.Origin.EYE : SpatialAnchor.Origin.BASE;
            Boolean followPosition = getInput(context, FOLLOW_POSITION, Boolean.class);
            boolean followOrientation = Boolean.TRUE.equals(getInput(context, FOLLOW_ORIENTATION, Boolean.class));
            SpatialAnchor.OrientationSource source = "view".equals(getInput(context, ORIENTATION_SOURCE, String.class))
                    ? SpatialAnchor.OrientationSource.VIEW : SpatialAnchor.OrientationSource.BODY;
            return SpatialAnchor.entity(entity, origin, !Boolean.FALSE.equals(followPosition), followOrientation, source);
        }
        String dimensionId = getInput(context, StandardPorts.DIMENSION.getId(), String.class);
        if (dimensionId == null || dimensionId.isBlank()) return null;
        Identifier identifier = Identifier.tryParse(dimensionId.trim());
        if (identifier == null) return null;
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, identifier);
        Vec3 position = getInput(context, StandardPorts.XYZ.getId(), Vec3.class);
        SpatialAnchor anchor = SpatialAnchor.world(dimension, position == null ? Vec3.ZERO : position);
        ExpressionData expression = getInputExpression(context, StandardPorts.XYZ.getId());
        return expression != null && expression.isVector() ? new DynamicData(anchor, expression) : anchor;
    }
}
