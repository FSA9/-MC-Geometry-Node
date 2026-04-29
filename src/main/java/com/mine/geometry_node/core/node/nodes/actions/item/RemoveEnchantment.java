package com.mine.geometry_node.core.node.nodes.actions.item;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
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

public class RemoveEnchantment extends BaseNode {

    public static final String TYPE_ID = "remove_enchantment";
    public static final String PROPERTY_SELECTED = PortMetaKeys.DYNAMIC_REGISTRY_ID.id();

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.remove_enchantment"))
                // 1. 执行流
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))

                // 2. 物品直通流：左进右出
                .addRow(new PortRow(StandardPorts.ITEM_STACK.toInput(), StandardPorts.ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))

                // 3. 要移除的附魔 ID (与 AddEnchantment 保持一致的 UI)
                .addRow(new PortRow(
                        StandardPorts.TYPE.toInput("minecraft:sharpness"),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(
                                PortMetaKeys.BIND_PROPERTY, PROPERTY_SELECTED,
                                PortMetaKeys.DYNAMIC_REGISTRY_ID, "minecraft:enchantment"
                        )
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ItemStack stack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);

        String enchantId = getInput(context, StandardPorts.TYPE.getId(), String.class);
        if (enchantId == null || enchantId.isEmpty()) {
            enchantId = (String) context.getNodeProperty(PROPERTY_SELECTED);
        }

        // 核心逻辑：物品存在且有效，且附魔 ID 不为空
        if (stack != null && !stack.isEmpty() && enchantId != null && !enchantId.isEmpty()) {
            ResourceLocation loc = ResourceLocation.tryParse(enchantId);
            if (loc != null && context.getLevel() instanceof ServerLevel serverLevel) {

                RegistryAccess registryAccess = serverLevel.registryAccess();
                Optional<Holder.Reference<Enchantment>> enchantOpt = registryAccess
                        .registryOrThrow(Registries.ENCHANTMENT)
                        .getHolder(loc);

                if (enchantOpt.isPresent()) {
                    Holder<Enchantment> holder = enchantOpt.get();

                    // 1.21 数据组件的核心处理方式
                    // 获取当前物品上的所有附魔（如果没有附魔则返回空的 ItemEnchantments）
                    ItemEnchantments currentEnchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

                    // 仅当该物品确实包含这个附魔时才进行操作，节省性能
                    if (currentEnchants.getLevel(holder) > 0) {
                        // 1. 将其转换为可变状态 (Mutable)
                        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(currentEnchants);

                        // 2. 将等级设为 0，这在底层的 Map 中等同于将其移除
                        mutable.set(holder, 0);

                        // 3. 【洁癖处理】：如果移除后，这把剑上一个附魔都没有了
                        if (mutable.keySet().isEmpty()) {
                            // 直接扒掉整个组件，让它变回一把干干净净的白板剑
                            stack.remove(DataComponents.ENCHANTMENTS);
                        } else {
                            // 否则将剩下的附魔打包为不可变对象，覆盖回去
                            stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
                        }
                    }
                }
            }
        }

        if (stack != null) {
            context.setTempData(StandardPorts.ITEM_STACK.getId(), stack);
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.ITEM_STACK.getId().equals(portName)) {
            return context.getTempData(StandardPorts.ITEM_STACK.getId());
        }
        return null;
    }
}