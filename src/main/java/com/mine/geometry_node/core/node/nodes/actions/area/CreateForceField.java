package com.mine.geometry_node.core.node.nodes.actions.area;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaAddress;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldAddress;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldResourceStore;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionSpec;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.graph.expression.LiveValues;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIds;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.value.dynamic.DynamicData;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;

public final class CreateForceField extends BaseNode {
    public static final String TYPE_ID = "create_force_field";
    public static final float DEFAULT_STRENGTH = 0.05F;
    public static final PortDef STRENGTH_PORT = StandardPorts.STRENGTH
            .toInput(DEFAULT_STRENGTH).liveExpression();

    @Override
    public NodeDef getDefaultDefinition() {
        return definition();
    }

    private NodeDef definition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node." + TYPE_ID))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.BOOL, "bool")
                        .input(StandardPorts.FORCE_FIELD_ID, "force_field_id")
                        .input(StandardPorts.AREA_ID, "area_id")
                        .input(StandardPorts.DIMENSION, "dimension")
                        .input(StandardPorts.STRENGTH, "strength")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(),
                        UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.FORCE_FIELD_ID.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.AREA_ID.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.DIMENSION.toInput(RegistryDataManager.DEFAULT_DIMENSION),
                        null, UIHint.SELECT, null,
                        Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, RegistryDataManager.DIMENSION_REGISTRY_ID)))
                .addRow(new PortRow(STRENGTH_PORT,
                        null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        boolean success = false;
        ServerLevel hostLevel = context.getLevel();
        ServerLevel fieldLevel = hostLevel != null
                ? RegistryDataManager.resolveDimension(hostLevel.getServer(),
                        getInput(context, StandardPorts.DIMENSION.getId(), String.class))
                : null;
        String fieldId = getInput(context, StandardPorts.FORCE_FIELD_ID.getId(), String.class);
        String areaId = getInput(context, StandardPorts.AREA_ID.getId(), String.class);
        if (hostLevel != null && fieldLevel != null && fieldId != null && !fieldId.isBlank()
                && areaId != null && !areaId.isBlank()) {
            ForceFieldAddress address = ForceFieldAddress.tryCreate(fieldLevel.dimension(), fieldId);
            AreaAddress area = AreaAddress.tryCreate(fieldLevel.dimension(), areaId);
            if (address != null && area != null) {
                String stableId = context.getCurrentNodeStableId();
                if (stableId == null || stableId.isBlank()) stableId = Integer.toString(context.getCurrentNodeId());
                GraphResourceId owner = GraphResourceIds.forKey(context, stableId,
                        GraphResourceTypeRegistry.FORCE_FIELD, address.id());
                Object rawStrengthInput = getRawInput(context, StandardPorts.STRENGTH.getId());
                Float rawStrength = getInput(context, StandardPorts.STRENGTH.getId(), Float.class);
                float strengthSnapshot = rawStrength != null && Float.isFinite(rawStrength)
                        ? rawStrength : DEFAULT_STRENGTH;
                ExpressionSpec strengthExpression = expressionSpec(rawStrengthInput);
                LiveValue<Float> strength = LiveValues.captureFloat(
                        STRENGTH_PORT, strengthSnapshot, strengthExpression);
                for (String diagnostic : strength.diagnostics()) {
                    GeometryNode.LOGGER.warn("Invalid force field strength expression for '{}': {}",
                            address.id(), diagnostic);
                }
                ForceFieldResourceStore.INSTANCE.upsert(hostLevel.getServer(), address, owner, area,
                        fieldLevel.getGameTime(), strength);
                success = true;
            }
        }
        context.setTempData(tempKey(context), success);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        return StandardPorts.BOOL.getId().equals(portName) ? context.getTempData(tempKey(context)) : null;
    }

    private static String tempKey(ExecutionContext context) {
        return TYPE_ID + ":" + context.getCurrentNodeId() + ":" + StandardPorts.BOOL.getId();
    }

    private static ExpressionSpec expressionSpec(Object rawInput) {
        if (!(rawInput instanceof DynamicData dynamic)) return null;
        ExpressionData expression = dynamic.expression();
        if (expression == null) return null;
        return new ExpressionSpec(expression.component(0), expression.bindings());
    }
}
