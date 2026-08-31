package com.mine.geometry_node.core.node.nodes.behavior.entity;

import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorEntityExecutors;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Editor definitions for the first server-authoritative entity behavior actions. */
public final class BehaviorEntityActionNode extends BaseNode implements BehaviorExecutableNode {
    public enum Kind {
        SELECT_TARGET("geometry_node:behavior_select_target"),
        CLEAR_TARGET("geometry_node:behavior_clear_target"),
        MOVE_TO("geometry_node:behavior_move_to"),
        STOP_MOVING("geometry_node:behavior_stop_moving"),
        WANDER("geometry_node:behavior_wander"),
        LOOK_AT("geometry_node:behavior_look_at"),
        ATTACK_TARGET("geometry_node:behavior_attack_target");

        private final String typeId;

        Kind(String typeId) {
            this.typeId = typeId;
        }

        public String typeId() {
            return typeId;
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
                    StandardPorts.CANDIDATES, List.of())).build();
            case CLEAR_TARGET, STOP_MOVING -> builder.build();
            case MOVE_TO -> moveToDefinition(builder, targetMode(instanceData));
            case WANDER -> builder
                    .addRow(input(StandardPorts.SPEED, 1.0f))
                    .addRow(input(StandardPorts.HORIZONTAL_RANGE, 10))
                    .addRow(input(StandardPorts.VERTICAL_RANGE, 4)).build();
            case LOOK_AT -> builder
                    .addRow(input(StandardPorts.TARGET, null))
                    .addRow(tickInput(20, "geometry_node.port.tick.look")).build();
            case ATTACK_TARGET -> builder
                    .comment(NodeComment.builder(path(Kind.ATTACK_TARGET.typeId))
                            .text("summary")
                            .text("range")
                            .text("delegation")
                            .build())
                    .addRow(input(StandardPorts.TARGET, null))
                    .addRow(input(StandardPorts.TARGET_RANGE, 20.0f)).build();
        };
    }

    private static NodeDef moveToDefinition(NodeDef.Builder builder, String mode) {
        builder.comment(NodeComment.builder(path(Kind.MOVE_TO.typeId))
                .text("summary")
                .text("position_mode")
                .text("entity_mode")
                .text("blocked")
                .text("unreachable")
                .text("control_conflict")
                .build());
        builder.addRow(select(StandardPorts.TARGET_MODE,
                            mode,
                            new String[]{TARGET_MODE_ENTITY, TARGET_MODE_POSITION},
                            new String[]{"geometry_node.behavior.target_mode.entity",
                                    "geometry_node.behavior.target_mode.position"}));
        if (TARGET_MODE_POSITION.equals(mode)) {
            builder.addRow(new PortRow(StandardPorts.TARGET_POSITION.toInput(Vec3.ZERO),
                    null, UIHint.VECTOR, null, null));
        } else {
            builder.addRow(input(StandardPorts.TARGET_ENTITY, null));
        }
        return builder
                .addRow(input(StandardPorts.SPEED, 1.0f))
                .addRow(input(StandardPorts.ARRIVAL_DISTANCE, 1.5f))
                .build();
    }

    private static String targetMode(NodeData instanceData) {
        Object mode = instanceData != null
                ? instanceData.inputs.get(StandardPorts.TARGET_MODE.getId()) : null;
        return TARGET_MODE_POSITION.equals(mode) ? TARGET_MODE_POSITION : TARGET_MODE_ENTITY;
    }

    private static PortRow input(StandardPorts port, Object value) {
        UIHint hint = port.getType() == PortType.ENTITY || port.getType() == PortType.LIST
                ? UIHint.DEFAULT : UIHint.INPUT;
        return new PortRow(port.toInput(value),
                null, hint, null, null);
    }

    private static PortRow tickInput(int value, String translationKey) {
        return new PortRow(StandardPorts.TICK.toInput(value).withDisplayName(translationKey),
                null, UIHint.INPUT, null, null);
    }

    private static PortRow select(StandardPorts port, String value, String[] options, String[] labels) {
        return new PortRow(port.toInput(value).hiddenPin(), null, UIHint.SELECT, null,
                Map.of(PortMetaKeys.OPTIONS, options, PortMetaKeys.OPTION_LABELS, labels));
    }

    private static PortDef parentPort() {
        return StandardPorts.BEHAVIOR_PARENT.toInput();
    }

    private static String path(String typeId) {
        return typeId.substring(typeId.indexOf(':') + 1);
    }

    public static final String TARGET_MODE_ENTITY = "entity";
    public static final String TARGET_MODE_POSITION = "position";

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorEntityExecutors.forKind(kind);
    }

    @Override
    public Set<Resource> requiredResources() {
        return switch (kind) {
            case SELECT_TARGET, CLEAR_TARGET, ATTACK_TARGET -> Set.of(Resource.TARGET);
            case MOVE_TO, STOP_MOVING, WANDER -> Set.of(Resource.MOVEMENT);
            case LOOK_AT -> Set.of(Resource.LOOK);
        };
    }
}
