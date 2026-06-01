package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;

public class AddEffect extends BaseNode {

    public static final String TYPE_ID = "add_effect";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.add_effect"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        StandardPorts.STRING.toInput().hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, RegistryDataManager.getAllEffects().toArray(new String[0]))
                ))

                .addRow(new PortRow(StandardPorts.TICK.toInput(600), null, UIHint.DEFAULT, null, null)) // 时长 (默认30秒)
                .addRow(new PortRow(StandardPorts.INT.toInput(0), null, UIHint.DEFAULT, null, null))   // 等级 (Amplifier)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);

        String effectId = getInput(context, StandardPorts.STRING.getId(), String.class);

        Integer duration = getInput(context, StandardPorts.TICK.getId(), Integer.class);
        Integer amplifier = getInput(context, StandardPorts.INT.getId(), Integer.class);

        if (!entities.isEmpty() && effectId != null) {
            ResourceLocation rl = ResourceLocation.tryParse(effectId);
            if (rl != null) {
                var effectHolder = BuiltInRegistries.MOB_EFFECT.getHolder(rl);
                if (effectHolder.isPresent()) {
                    int dur = duration != null ? duration : 600;
                    int amp = amplifier != null ? amplifier : 0;

                    for (Entity entity : entities) {
                        if (entity instanceof LivingEntity living) {
                            living.addEffect(new MobEffectInstance(effectHolder.get(), dur, amp));
                        }
                    }
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}