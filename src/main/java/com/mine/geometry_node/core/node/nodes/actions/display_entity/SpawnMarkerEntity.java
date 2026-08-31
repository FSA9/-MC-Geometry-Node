package com.mine.geometry_node.core.node.nodes.actions.display_entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.utils.nbt.EntityNbtCompat;
import com.mine.geometry_node.core.utils.nbt.NbtDictConverter; // 引入我们的工具类
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class SpawnMarkerEntity extends BaseNode {

    public static final String TYPE_ID = "spawn_marker_entity";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.spawn_marker_entity"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.XYZ.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.TAG.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.DATA.toInput(), null, UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Level level = context.getLevel();
        if (level == null || level.isClientSide()) return next(StandardPorts.FLOW_OUT.getId());

        Vec3 pos = getInput(context, StandardPorts.XYZ.getId(), Vec3.class);
        if (pos == null) pos = Vec3.ZERO;

        String tag = getInput(context, StandardPorts.TAG.getId(), String.class);
        Map<String, Object> dataDict = getInputDict(context, StandardPorts.DATA.getId());

        Marker marker = EntityType.MARKER.create(level, EntitySpawnReason.COMMAND);
        if (marker != null) {
            marker.setPos(pos.x, pos.y, pos.z);

            if (tag != null && !tag.trim().isEmpty()) {
                marker.addTag(tag.trim());
            }

            if (dataDict != null && !dataDict.isEmpty()) {
                CompoundTag nbt = EntityNbtCompat.saveWithoutId(marker);

                nbt.put("data", NbtDictConverter.dictToNbt(dataDict, level.registryAccess()));

                EntityNbtCompat.load(marker, nbt);
            }

            level.addFreshEntity(marker);
            context.setTempData("spawned_marker_" + context.getCurrentNodeId(), marker);
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.ENTITY.getId().equals(portName)) {
            return context.getTempData("spawned_marker_" + context.getCurrentNodeId());
        }
        return null;
    }
}
