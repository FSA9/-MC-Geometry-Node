package com.mine.geometry_node.core.node.nodes.actions.display_entity;

import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionResult;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.TypeConverter;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.Map;

public class SpawnBlockDisplayEntity extends BaseNode {

    public static final String TYPE_ID = "spawn_block_display_entity";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.spawn_block_display_entity"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.START_POS.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.BLOCK_STATE.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        StandardPorts.STRING.toInput().hiddenPin(), null, UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, RegistryDataManager.getAllBlocks().toArray(new String[0]))
                ))
                .addRow(new PortRow(StandardPorts.TRANSLATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.ROTATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.TELEPORT_DURATION.toInput(0), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.INTERPOLATION_DURATION.toInput(0), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Level level = context.getLevel();
        if (level == null) return next(StandardPorts.FLOW_OUT.getId());

        Vec3 pos = getInput(context, StandardPorts.START_POS.getId(), Vec3.class);
        if (pos == null) pos = Vec3.ZERO;

        BlockState blockState = getInput(context, StandardPorts.BLOCK_STATE.getId(), BlockState.class);
        if (blockState == null) {
            String selectedBlockId = getInput(context, StandardPorts.STRING.getId(), String.class);
            if (selectedBlockId != null && !selectedBlockId.isEmpty()) {
                blockState = TypeConverter.convert(selectedBlockId, BlockState.class, context);
            }
        }

        if (blockState == null) blockState = Blocks.STONE.defaultBlockState();

        Vec3 translation = getInput(context, StandardPorts.TRANSLATION.getId(), Vec3.class);
        if (translation == null) translation = Vec3.ZERO;

        Vec3 rotation = getInput(context, StandardPorts.ROTATION.getId(), Vec3.class);
        if (rotation == null) rotation = Vec3.ZERO;

        Vec3 scaleVec = getInput(context, StandardPorts.SIZE_3.getId(), Vec3.class);
        if (scaleVec == null) scaleVec = new Vec3(1, 1, 1);

        Integer tpDuration = getInput(context, StandardPorts.TELEPORT_DURATION.getId(), Integer.class);
        Integer interpDuration = getInput(context, StandardPorts.INTERPOLATION_DURATION.getId(), Integer.class);

        Quaternionf leftRotation = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(rotation.y),
                (float) Math.toRadians(rotation.x),
                (float) Math.toRadians(rotation.z)
        );

        Display.BlockDisplay displayEntity = EntityType.BLOCK_DISPLAY.create(level);
        if (displayEntity != null) {
            displayEntity.setPos(pos.x, pos.y, pos.z);

            CompoundTag nbt = new CompoundTag();
            displayEntity.saveWithoutId(nbt);

            nbt.put("block_state", NbtUtils.writeBlockState(blockState));

            CompoundTag transTag = new CompoundTag();
            transTag.put("translation", createFloatList((float) translation.x, (float) translation.y, (float) translation.z));
            transTag.put("scale", createFloatList((float) scaleVec.x, (float) scaleVec.y, (float) scaleVec.z));
            transTag.put("left_rotation", createFloatList(leftRotation.x(), leftRotation.y(), leftRotation.z(), leftRotation.w()));
            transTag.put("right_rotation", createFloatList(0f, 0f, 0f, 1f));
            nbt.put("transformation", transTag);

            nbt.putInt("teleport_duration", tpDuration != null ? Math.max(0, tpDuration) : 0);
            nbt.putInt("interpolation_duration", interpDuration != null ? Math.max(0, interpDuration) : 0);
            nbt.putInt("start_interpolation", 0);

            displayEntity.load(nbt);

            level.addFreshEntity(displayEntity);
            context.setTempData("spawned_block_display", displayEntity);
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.ENTITY.getId().equals(portName)) {
            return context.getTempData("spawned_block_display");
        }
        return null;
    }

    private ListTag createFloatList(float... values) {
        ListTag list = new ListTag();
        for (float v : values) {
            list.add(FloatTag.valueOf(v));
        }
        return list;
    }
}