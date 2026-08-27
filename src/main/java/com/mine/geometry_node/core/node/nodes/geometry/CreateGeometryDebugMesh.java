package com.mine.geometry_node.core.node.nodes.geometry;

import com.mine.geometry_node.core.engine.blueprint.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.engine.blueprint.debug.GeometryDebugElement;
import com.mine.geometry_node.core.engine.blueprint.debug.GeometryDebugMeshFactory;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class CreateGeometryDebugMesh extends BaseNode {
    public static final String TYPE_ID = "create_geometry_debug_mesh";

    private static final int MAX_MESHES_PER_EXECUTION = 128;
    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.create_geometry_debug_mesh"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.GEOMETRY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.TRANSLATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.KEY.toInput(""), StandardPorts.KEY.toOutput(), UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        GeometryValue geometry = getInput(context, StandardPorts.GEOMETRY.getId(), GeometryValue.class);
        if (geometry == null || geometry.isEmpty()) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        Vec3 translation = getInput(context, StandardPorts.TRANSLATION.getId(), Vec3.class);
        String key = resolveKey(context, level);
        String sourceKey = DebugRendererSessionManager.geometryMeshSourceKey(level, key);

        List<GeometryDebugElement> meshes = GeometryDebugMeshFactory.buildMeshes(
                sourceKey,
                context.getGraphId(),
                "created",
                geometry,
                MAX_MESHES_PER_EXECUTION,
                translation
        );
        if (!meshes.isEmpty()) {
            DebugRendererSessionManager.replaceSourceGeometry(level, sourceKey, meshes);
            context.setTempData(tempKey(context), key);
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.KEY.getId().equals(portName)) {
            return context.getTempData(tempKey(context));
        }
        return null;
    }

    private String resolveKey(ExecutionContext context, ServerLevel level) {
        String configured = getInput(context, StandardPorts.KEY.getId(), String.class);
        String baseKey = configured != null ? configured.trim() : "";
        if (baseKey.isEmpty()) {
            return uniqueKey(context, level, "mesh");
        }
        return baseKey;
    }

    private static String uniqueKey(ExecutionContext context, ServerLevel level, String prefix) {
        String stableId = context.getCurrentNodeStableId();
        String nodePart = stableId != null && !stableId.isBlank() ? stableId : Integer.toString(context.getCurrentNodeId());
        return prefix + ":" + nodePart + ":" + level.getGameTime() + ":" + SEQUENCE.incrementAndGet();
    }

    private static String tempKey(ExecutionContext context) {
        return TYPE_ID + ":input:" + context.getCurrentNodeId();
    }

}
