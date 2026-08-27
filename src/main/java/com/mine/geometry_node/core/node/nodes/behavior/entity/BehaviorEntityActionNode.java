package com.mine.geometry_node.core.node.nodes.behavior.entity;

import com.mine.geometry_node.core.engine.behavior.document.BehaviorNodeTypes;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/** Editor definitions for the first server-authoritative entity behavior actions. */
public final class BehaviorEntityActionNode extends BaseNode {
    public enum Kind {
        SELECT_TARGET(BehaviorNodeTypes.SELECT_TARGET), CLEAR_TARGET(BehaviorNodeTypes.CLEAR_TARGET),
        MOVE_TO(BehaviorNodeTypes.MOVE_TO), STOP_MOVING(BehaviorNodeTypes.STOP_MOVING),
        WANDER(BehaviorNodeTypes.WANDER), LOOK_AT(BehaviorNodeTypes.LOOK_AT),
        ATTACK_TARGET(BehaviorNodeTypes.ATTACK_TARGET);

        private final String typeId;

        Kind(String typeId) {
            this.typeId = typeId;
        }
    }

    private final Kind kind;

    public BehaviorEntityActionNode(Kind kind) {
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
        NodeDef.Builder builder = NodeDef.builder(kind.typeId, NodeType.ACTION,
                        Component.translatable("geometry_node.node." + path(kind.typeId)))
                .addRow(new PortRow(parentPort(), null, UIHint.DEFAULT, null, null));
        return switch (kind) {
            case SELECT_TARGET -> builder.addRow(input(
                    BehaviorNodeTypes.CANDIDATES_PORT, PortType.LIST, List.of())).build();
            case CLEAR_TARGET, STOP_MOVING -> builder.build();
            case MOVE_TO -> moveToDefinition(builder, targetMode(instanceData));
            case WANDER -> builder
                    .addRow(input(BehaviorNodeTypes.SPEED_PORT, PortType.FLOAT, 1.0f))
                    .addRow(input(BehaviorNodeTypes.HORIZONTAL_RANGE_PORT, PortType.INTEGER, 10))
                    .addRow(input(BehaviorNodeTypes.VERTICAL_RANGE_PORT, PortType.INTEGER, 4)).build();
            case LOOK_AT -> builder
                    .addRow(input(BehaviorNodeTypes.TARGET_PORT, PortType.ENTITY, null))
                    .addRow(input(BehaviorNodeTypes.DURATION_PORT, PortType.INTEGER, 20)).build();
            case ATTACK_TARGET -> builder
                    .addRow(input(BehaviorNodeTypes.TARGET_PORT, PortType.ENTITY, null))
                    .addRow(input(BehaviorNodeTypes.ATTACK_RANGE_PORT, PortType.FLOAT, 2.5f))
                    .addRow(input(BehaviorNodeTypes.ATTACK_COOLDOWN_PORT, PortType.INTEGER, 20)).build();
        };
    }

    private static NodeDef moveToDefinition(NodeDef.Builder builder, String mode) {
        builder.comment(NodeComment.builder(path(BehaviorNodeTypes.MOVE_TO))
                .text("summary")
                .text("position_mode")
                .text("entity_mode")
                .text("blocked")
                .text("unreachable")
                .text("control_conflict")
                .build());
        builder.addRow(select(BehaviorNodeTypes.TARGET_MODE_PORT,
                            mode,
                            new String[]{BehaviorNodeTypes.TARGET_MODE_ENTITY,
                                    BehaviorNodeTypes.TARGET_MODE_POSITION},
                            new String[]{"geometry_node.behavior.target_mode.entity",
                                    "geometry_node.behavior.target_mode.position"}));
        if (BehaviorNodeTypes.TARGET_MODE_POSITION.equals(mode)) {
            builder.addRow(new PortRow(PortDef.create(BehaviorNodeTypes.TARGET_POSITION_PORT,
                    "geometry_node.port." + BehaviorNodeTypes.TARGET_POSITION_PORT,
                    PortType.XYZ, Vec3.ZERO), null, UIHint.VECTOR, null, null));
        } else {
            builder.addRow(input(BehaviorNodeTypes.TARGET_ENTITY_PORT, PortType.ENTITY, null));
        }
        return builder
                .addRow(input(BehaviorNodeTypes.SPEED_PORT, PortType.FLOAT, 1.0f))
                .addRow(input(BehaviorNodeTypes.ARRIVAL_DISTANCE_PORT, PortType.FLOAT, 1.5f))
                .build();
    }

    private static String targetMode(NodeData instanceData) {
        Object mode = instanceData != null
                ? instanceData.inputs.get(BehaviorNodeTypes.TARGET_MODE_PORT) : null;
        return BehaviorNodeTypes.TARGET_MODE_POSITION.equals(mode)
                ? BehaviorNodeTypes.TARGET_MODE_POSITION : BehaviorNodeTypes.TARGET_MODE_ENTITY;
    }

    private static PortRow input(String id, PortType type, Object value) {
        return new PortRow(PortDef.create(id, "geometry_node.port." + id, type, value),
                null, UIHint.INPUT, null, null);
    }

    private static PortRow select(String id, String value, String[] options, String[] labels) {
        return new PortRow(PortDef.create(id, "geometry_node.port." + id,
                PortType.STRING, value).hiddenPin(), null, UIHint.SELECT, null,
                Map.of(PortMetaKeys.OPTIONS, options, PortMetaKeys.OPTION_LABELS, labels));
    }

    private static PortDef parentPort() {
        return PortDef.create(BehaviorNodeTypes.PARENT_PORT,
                "geometry_node.port." + BehaviorNodeTypes.PARENT_PORT, PortType.BEHAVIOR_STRUCTURE);
    }

    private static String path(String typeId) {
        return typeId.substring(typeId.indexOf(':') + 1);
    }
}
