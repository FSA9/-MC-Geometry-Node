package com.mine.geometry_node.core.node.nodes.actions.display_entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.utils.nbt.EntityNbtCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Moves Display entities to an absolute world position without changing their model transform. */
public final class SetDisplayPosition extends BaseNode {
    public static final String TYPE_ID = "set_display_position";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node.set_display_position"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(StandardPorts.DISPLAY_ENTITY, "display_entity")
                        .input(StandardPorts.WORLD_POSITION, "world_position")
                        .input(StandardPorts.TICK, "teleport_tick")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(),
                        UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.DISPLAY_ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.WORLD_POSITION.toInput(), UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.TICK.toInput(0)
                        .withDisplayName("geometry_node.port.tick.teleport"), UIHint.INPUT)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputs(context, StandardPorts.DISPLAY_ENTITY.getId(), Entity.class);
        Vec3 worldPosition = getInput(context, StandardPorts.WORLD_POSITION.getId(), Vec3.class);
        if (entities.isEmpty() || worldPosition == null) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        Integer teleportTick = getInput(context, StandardPorts.TICK.getId(), Integer.class);
        int duration = teleportTick != null ? Math.max(0, teleportTick) : 0;
        for (Entity entity : entities) {
            if (entity == null) continue;
            if (!(entity instanceof Display display)) continue;
            CompoundTag nbt = EntityNbtCompat.saveWithoutId(display);
            nbt.putInt("teleport_duration", duration);
            EntityNbtCompat.load(display, nbt);
            display.teleportTo(worldPosition.x, worldPosition.y, worldPosition.z);
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
