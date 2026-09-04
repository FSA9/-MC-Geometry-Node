package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.utils.RateLimitedLog;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;

public class DamageEntity extends BaseNode {

    public static final String TYPE_ID = "damage_entity";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.damage_entity"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.FLOAT_VALUE.toInput(), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.DAMAGE_TYPE.toInput().hiddenPin(), UIHint.SELECT, null, Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, "minecraft:damage_type"))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Float damage = getInput(context, StandardPorts.FLOAT_VALUE.getId(), Float.class);
        String damageTypeId = getInput(context, StandardPorts.DAMAGE_TYPE.getId(), String.class);

        if (damage != null && damage > 0 && !entities.isEmpty()) {
            DamageSource finalSource = null;

            if (damageTypeId != null && !damageTypeId.isEmpty() && context.getLevel() != null) {
                try {
                    Identifier typeRes = Identifier.parse(damageTypeId);

                    Holder<DamageType> holder = context.getLevel().registryAccess()
                            .lookup(Registries.DAMAGE_TYPE)
                            .flatMap(registry -> registry.get(ResourceKey.create(Registries.DAMAGE_TYPE, typeRes)))
                            .orElse(null);
                    if (holder != null) {
                        finalSource = new DamageSource(holder);
                    }
                } catch (Exception e) {
                    if (RateLimitedLog.acquire(context, "damage_entity:damage_type:" + damageTypeId)) {
                        GeometryNode.LOGGER.warn("[DamageEntity] Invalid damage type ID: {}", damageTypeId);
                    }
                }
            }

            for (Entity entity : entities) {
                if (finalSource != null) {
                    entity.hurt(finalSource, damage);
                } else {
                    entity.hurt(entity.damageSources().generic(), damage);
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
