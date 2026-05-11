package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

public class SpawnEntity extends BaseNode {

    public static final String TYPE_ID = "spawn_entity";
    public static final String PROPERTY_SELECTED_TYPE = "selected_entity_type";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.spawn_entity"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.VECTOR.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(
                        StandardPorts.TYPE.toInput("minecraft:zombie"),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, "minecraft:entity_type")
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Vec3 pos = getInput(context, StandardPorts.VECTOR.getId(), Vec3.class);
        String typeId = getInput(context, StandardPorts.TYPE.getId(), String.class);
        if (typeId == null || typeId.isEmpty()) {
            typeId = (String) context.getStaticInput(PROPERTY_SELECTED_TYPE);
        }

        if (pos != null && typeId != null && !typeId.isEmpty() && context.getLevel() instanceof ServerLevel serverLevel) {
            ResourceLocation loc = ResourceLocation.tryParse(typeId);
            if (loc != null) {
                EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(loc).orElse(null);

                if (entityType != null) {
                    Entity entity = entityType.create(serverLevel);
                    if (entity != null) {
                        entity.moveTo(pos.x(), pos.y(), pos.z(), 0.0F, 0.0F);
                        serverLevel.addFreshEntity(entity);
                        context.setTempData(StandardPorts.ENTITY.getId(), List.of(entity));
                    }
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.ENTITY.getId().equals(portName)) {
            return context.getTempData(StandardPorts.ENTITY.getId());
        }
        return null;
    }
}