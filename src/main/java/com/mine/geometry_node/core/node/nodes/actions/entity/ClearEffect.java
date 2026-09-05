package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;

public class ClearEffect extends BaseNode {

    public static final String TYPE_ID = "clear_effect";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.clear_effect"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.STRING.toInput().hiddenPin(), UIHint.SELECT, null, Map.of(
                                PortMetaKeys.OPTIONS, RegistryDataManager.getAllEffects().toArray(new String[0])
                        ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputs(context, StandardPorts.ENTITY.getId(), Entity.class);
        String effectId = getInput(context, StandardPorts.STRING.getId(), String.class);

        if (!entities.isEmpty() && effectId != null) {
            Identifier rl = Identifier.tryParse(effectId);
            if (rl != null) {
                var effectHolder = BuiltInRegistries.MOB_EFFECT
                        .getOptional(rl)
                        .map(BuiltInRegistries.MOB_EFFECT::wrapAsHolder);
                if (effectHolder.isPresent()) {
                    for (Entity entity : entities) {
                        if (entity == null) continue;
                        if (entity instanceof LivingEntity living) {
                            living.removeEffect(effectHolder.get());
                        }
                    }
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
