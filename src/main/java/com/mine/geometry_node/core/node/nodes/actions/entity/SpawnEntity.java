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
                // 1. 执行流
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))

                // 2. 输出生成的实体 (注意：为了兼容后续节点，我们统一输出 List)
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))

                // 3. 生成坐标 (默认在 0,0,0)
                .addRow(new PortRow(StandardPorts.VECTOR.toInput(), null, UIHint.VECTOR, null, null))

                // 4. 实体类型下拉框
                .addRow(new PortRow(
                        StandardPorts.TYPE.toInput("minecraft:zombie"),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(
                                PortMetaKeys.BIND_PROPERTY, PROPERTY_SELECTED_TYPE,
                                PortMetaKeys.DYNAMIC_REGISTRY_ID, "minecraft:entity_type" // 绑定刚才写的字典
                        )
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Vec3 pos = getInput(context, StandardPorts.VECTOR.getId(), Vec3.class);

        // 获取实体类型：优先看有没有连线输入字符串，没有就读下拉框
        String typeId = getInput(context, StandardPorts.TYPE.getId(), String.class);
        if (typeId == null || typeId.isEmpty()) {
            typeId = (String) context.getNodeProperty(PROPERTY_SELECTED_TYPE);
        }

        // 核心生成逻辑
        if (pos != null && typeId != null && !typeId.isEmpty() && context.getLevel() instanceof ServerLevel serverLevel) {
            ResourceLocation loc = ResourceLocation.tryParse(typeId);
            if (loc != null) {
                EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(loc).orElse(null);

                if (entityType != null) {
                    // 1. 在内存中创建实体骨架
                    Entity entity = entityType.create(serverLevel);

                    if (entity != null) {
                        // 2. 移动到指定坐标 (偏航角和俯仰角默认为0)
                        entity.moveTo(pos.x(), pos.y(), pos.z(), 0.0F, 0.0F);

                        // 3. 真正将其加入物理世界
                        serverLevel.addFreshEntity(entity);

                        // 4. 存入缓存，供下游节点获取
                        // 注意：这里用 List.of 包装，因为你的 TargetSelector 等后续修改节点默认接收 List<Entity>
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