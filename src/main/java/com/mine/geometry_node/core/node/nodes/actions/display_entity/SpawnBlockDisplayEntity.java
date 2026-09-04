package com.mine.geometry_node.core.node.nodes.actions.display_entity;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.system.display.DisplayTransformController;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.utils.nbt.EntityNbtCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class SpawnBlockDisplayEntity extends BaseNode {

    public static final String TYPE_ID = "spawn_block_display_entity";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node.spawn_block_display_entity"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.DISPLAY_ENTITY, "display_entity")
                        .input(StandardPorts.WORLD_POSITION, "world_position")
                        .input(StandardPorts.WORLD_ROTATION, "world_rotation")
                        .input(StandardPorts.BLOCK_STATE, "block_state")
                        .input(StandardPorts.PIVOT, "pivot")
                        .input(StandardPorts.TRANSLATION, "translation")
                        .input(StandardPorts.ROTATION, "rotation")
                        .input(StandardPorts.SIZE_3, "size_3")
                        .input(StandardPorts.TICK, "teleport_tick")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.DISPLAY_ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.WORLD_POSITION.toInput(Vec3.ZERO), UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.WORLD_ROTATION.toInput(Vec3.ZERO), UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.BLOCK_STATE.toInput("minecraft:stone"), UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, RegistryDataManager.getAllBlocks().toArray(new String[0])))
                .addPassthroughInput(StandardPorts.PIVOT.toInput(Vec3.ZERO), UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.TRANSLATION.toInput(Vec3.ZERO), UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.ROTATION.toInput(Vec3.ZERO), UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)), UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.TICK.toInput(0)
                        .withDisplayName("geometry_node.port.tick.teleport"), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.TICK.toInputWithIndex(1, 0)
                        .withDisplayName("geometry_node.port.tick.interpolation"), UIHint.INPUT)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        context.setNodeResult(StandardPorts.DISPLAY_ENTITY.getId(), null);
        Level level = context.getLevel();
        if (level == null) return next(StandardPorts.FLOW_OUT.getId());

        Vec3 pos = getInput(context, StandardPorts.WORLD_POSITION.getId(), Vec3.class);
        if (pos == null) pos = Vec3.ZERO;
        Vec3 worldRotation = getInput(context, StandardPorts.WORLD_ROTATION.getId(), Vec3.class);
        if (worldRotation == null) worldRotation = Vec3.ZERO;

        BlockState blockState = getInput(context, StandardPorts.BLOCK_STATE.getId(), BlockState.class);

        if (blockState == null) blockState = Blocks.STONE.defaultBlockState();

        Vec3 translation = getInput(context, StandardPorts.TRANSLATION.getId(), Vec3.class);
        if (translation == null) translation = Vec3.ZERO;
        Vec3 pivot = getInput(context, StandardPorts.PIVOT.getId(), Vec3.class);
        if (pivot == null) pivot = Vec3.ZERO;

        Vec3 rotation = getInput(context, StandardPorts.ROTATION.getId(), Vec3.class);
        if (rotation == null) rotation = Vec3.ZERO;

        Vec3 scaleVec = getInput(context, StandardPorts.SIZE_3.getId(), Vec3.class);
        if (scaleVec == null) scaleVec = new Vec3(1, 1, 1);

        Integer tpDuration = getInput(context, StandardPorts.TICK.getId(), Integer.class);
        Integer interpDuration = getInput(context, StandardPorts.TICK.getIdWithIndex(1), Integer.class);

        Display.BlockDisplay displayEntity = EntityType.BLOCK_DISPLAY.create(level, EntitySpawnReason.COMMAND);
        if (displayEntity != null) {
            displayEntity.setPos(pos.x, pos.y, pos.z);

            CompoundTag nbt = EntityNbtCompat.saveWithoutId(displayEntity);

            nbt.put("block_state", NbtUtils.writeBlockState(blockState));

            DisplayTransformController.writeTransform(nbt, worldRotation, translation, rotation, scaleVec, pivot);

            nbt.putInt("teleport_duration", tpDuration != null ? Math.max(0, tpDuration) : 0);
            nbt.putInt("interpolation_duration", interpDuration != null ? Math.max(0, interpDuration) : 0);
            nbt.putInt("start_interpolation", 0);

            EntityNbtCompat.load(displayEntity, nbt);
            DisplayTransformController.initializePose(displayEntity, worldRotation, pivot);

            level.addFreshEntity(displayEntity);
            context.setNodeResult(StandardPorts.DISPLAY_ENTITY.getId(), displayEntity);
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (StandardPorts.DISPLAY_ENTITY.getId().equals(portName)) {
            return context.getNodeResult(StandardPorts.DISPLAY_ENTITY.getId());
        }
        return null;
    }
}
