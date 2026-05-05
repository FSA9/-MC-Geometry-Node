package com.mine.geometry_node.core.node.nodes.actions.display_entity;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.List;

public class SetDisplayTransform extends BaseNode {

    public static final String TYPE_ID = "set_display_transform";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_display_transform"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))

                // 1. 目标矩阵姿态
                .addRow(new PortRow(StandardPorts.TRANSLATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.ROTATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)), null, UIHint.VECTOR, null, null))

                // 2. 动画插值控制
                .addRow(new PortRow(StandardPorts.INTERPOLATION_DURATION.toInput(0), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.START_INTERPOLATION.toInput(0), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        if (entities.isEmpty()) return next(StandardPorts.FLOW_OUT.getId());

        Vec3 translation = getInput(context, StandardPorts.TRANSLATION.getId(), Vec3.class);
        Vec3 rotation = getInput(context, StandardPorts.ROTATION.getId(), Vec3.class);
        Vec3 scaleVec = getInput(context, StandardPorts.SIZE_3.getId(), Vec3.class);
        Integer interpDuration = getInput(context, StandardPorts.INTERPOLATION_DURATION.getId(), Integer.class);
        Integer startInterp = getInput(context, StandardPorts.START_INTERPOLATION.getId(), Integer.class);

        if (translation == null) translation = Vec3.ZERO;
        if (rotation == null) rotation = Vec3.ZERO;
        if (scaleVec == null) scaleVec = new Vec3(1, 1, 1);

        Quaternionf leftRotation = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(rotation.y),
                (float) Math.toRadians(rotation.x),
                (float) Math.toRadians(rotation.z)
        );

        for (Entity entity : entities) {
            if (entity instanceof Display displayEntity) {
                CompoundTag nbt = new CompoundTag();
                displayEntity.saveWithoutId(nbt);

                CompoundTag transTag = new CompoundTag();
                transTag.put("translation", createFloatList((float) translation.x, (float) translation.y, (float) translation.z));
                transTag.put("scale", createFloatList((float) scaleVec.x, (float) scaleVec.y, (float) scaleVec.z));
                transTag.put("left_rotation", createFloatList(leftRotation.x(), leftRotation.y(), leftRotation.z(), leftRotation.w()));
                transTag.put("right_rotation", createFloatList(0f, 0f, 0f, 1f));

                nbt.put("transformation", transTag);

                if (interpDuration != null) nbt.putInt("interpolation_duration", Math.max(0, interpDuration));

                nbt.putInt("start_interpolation", startInterp != null ? Math.max(0, startInterp) : 0);

                displayEntity.load(nbt);
            }
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }

    private ListTag createFloatList(float... values) {
        ListTag list = new ListTag();
        for (float v : values) {
            list.add(FloatTag.valueOf(v));
        }
        return list;
    }
}