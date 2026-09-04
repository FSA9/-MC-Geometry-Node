package com.mine.geometry_node.core.node.nodes.behavior.entity;

import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorEntityExecutors;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;

public final class BehaviorMoveToNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "behavior_move_to";
    public static final String TARGET_MODE_ENTITY = "entity";
    public static final String TARGET_MODE_POSITION = "position";

    @Override
    public NodeDef getDefaultDefinition() {
        return definition(null);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        return definition(instanceData);
    }

    private static NodeDef definition(NodeData instanceData) {
        String mode = targetMode(instanceData);
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node.behavior_move_to"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .text("position_mode")
                        .text("entity_mode")
                        .text("blocked")
                        .text("unreachable")
                        .text("control_conflict")
                        .build())
                .addRow(BehaviorEntityNodeSupport.parentRow())
                .addPassthroughInput(StandardPorts.TARGET_MODE.toInput(mode).hiddenPin(), UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS,
                                        new String[]{TARGET_MODE_ENTITY, TARGET_MODE_POSITION},
                                PortMetaKeys.OPTION_LABELS,
                                        new String[]{"geometry_node.behavior.target_mode.entity",
                                                "geometry_node.behavior.target_mode.position"}));
        if (TARGET_MODE_POSITION.equals(mode)) {
            builder.addPassthroughInput(StandardPorts.TARGET_POSITION.toInput(Vec3.ZERO), UIHint.VECTOR);
        } else {
            builder.addRow(BehaviorEntityNodeSupport.input(StandardPorts.TARGET_ENTITY, null));
        }
        return builder
                .addRow(BehaviorEntityNodeSupport.input(StandardPorts.SPEED, 1.0f))
                .addRow(BehaviorEntityNodeSupport.input(StandardPorts.ARRIVAL_DISTANCE, 1.5f))
                .build();
    }

    private static String targetMode(NodeData instanceData) {
        Object mode = instanceData != null
                ? instanceData.inputs.get(StandardPorts.TARGET_MODE.getId()) : null;
        return TARGET_MODE_POSITION.equals(mode) ? TARGET_MODE_POSITION : TARGET_MODE_ENTITY;
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorEntityExecutors.moveTo();
    }

    @Override
    public Set<Resource> requiredResources() {
        return Set.of(Resource.MOVEMENT);
    }
}
