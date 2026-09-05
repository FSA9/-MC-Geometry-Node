package com.mine.geometry_node.core.node.nodes.actions.display_entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.system.display.DisplayTransformController;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SetDisplayTransform extends BaseNode {

    public static final String TYPE_ID = "set_display_transform";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node.set_display_transform"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(StandardPorts.DISPLAY_ENTITY, "display_entity")
                        .input(StandardPorts.WORLD_ROTATION, "world_rotation")
                        .input(StandardPorts.TRANSLATION, "translation")
                        .input(StandardPorts.ROTATION, "rotation")
                        .input(StandardPorts.SIZE_3, "size_3")
                        .input(StandardPorts.TICK, "interpolation_tick")
                        .input(StandardPorts.TICK.getIdWithIndex(1), "delay_tick")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.DISPLAY_ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.WORLD_ROTATION.toInput(Vec3.ZERO), UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.TRANSLATION.toInput(Vec3.ZERO), UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.ROTATION.toInput(Vec3.ZERO), UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)), UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.TICK.toInput(0)
                        .withDisplayName("geometry_node.port.tick.interpolation"), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.TICK.toInputWithIndex(1, 0)
                        .withDisplayName("geometry_node.port.tick.delay"), UIHint.INPUT)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputs(context, StandardPorts.DISPLAY_ENTITY.getId(), Entity.class);
        if (entities.isEmpty()) return next(StandardPorts.FLOW_OUT.getId());

        Vec3 worldRotation = getInput(context, StandardPorts.WORLD_ROTATION.getId(), Vec3.class);
        Vec3 translation = getInput(context, StandardPorts.TRANSLATION.getId(), Vec3.class);
        Vec3 rotation = getInput(context, StandardPorts.ROTATION.getId(), Vec3.class);
        Vec3 scaleVec = getInput(context, StandardPorts.SIZE_3.getId(), Vec3.class);
        Integer interpDuration = getInput(context, StandardPorts.TICK.getId(), Integer.class);
        Integer startInterp = getInput(context, StandardPorts.TICK.getIdWithIndex(1), Integer.class);

        if (worldRotation == null) worldRotation = Vec3.ZERO;
        if (translation == null) translation = Vec3.ZERO;
        if (rotation == null) rotation = Vec3.ZERO;
        if (scaleVec == null) scaleVec = new Vec3(1, 1, 1);

        for (Entity entity : entities) {
            if (entity == null) continue;
            if (entity instanceof Display displayEntity) {
                DisplayTransformController.applyTransform(
                        displayEntity,
                        worldRotation,
                        translation,
                        rotation,
                        scaleVec,
                        interpDuration != null ? interpDuration : 0,
                        startInterp != null ? startInterp : 0);
            }
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
