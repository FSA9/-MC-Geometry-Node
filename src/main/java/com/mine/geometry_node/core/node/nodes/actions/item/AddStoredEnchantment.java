package com.mine.geometry_node.core.node.nodes.actions.item;

import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Map;
import java.util.Optional;

public class AddStoredEnchantment extends BaseNode {
    public static final String TYPE_ID = "add_stored_enchantment";
    public static final String PROPERTY_SELECTED = PortMetaKeys.DYNAMIC_REGISTRY_ID.id();

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node." + TYPE_ID))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ITEM_STACK.toInput(), StandardPorts.ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        StandardPorts.TYPE.toInput("minecraft:sharpness"), null, UIHint.SELECT, null,
                        Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, "minecraft:enchantment")
                ))
                .addRow(new PortRow(StandardPorts.INT.toInput(1), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ItemStack stack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        Integer level = getInput(context, StandardPorts.INT.getId(), Integer.class);
        String enchantId = getInput(context, StandardPorts.TYPE.getId(), String.class);
        if (enchantId == null || enchantId.isEmpty()) enchantId = (String) context.getStaticInput(PROPERTY_SELECTED);

        if (stack != null && !stack.isEmpty() && enchantId != null && level != null && level > 0 && context.getLevel() instanceof ServerLevel serverLevel) {
            ResourceLocation loc = ResourceLocation.tryParse(enchantId);
            if (loc != null) {
                Optional<Holder.Reference<Enchantment>> enchantOpt = serverLevel.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(loc);
                enchantOpt.ifPresent(holder -> {
                    ItemEnchantments current = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
                    ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
                    mutable.set(holder, level);
                    stack.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
                });
            }
            context.setTempData(StandardPorts.ITEM_STACK.getId(), stack);
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.ITEM_STACK.getId().equals(portName)) return context.getTempData(StandardPorts.ITEM_STACK.getId());
        return null;
    }
}