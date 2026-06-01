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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.Map;
import java.util.Optional;

public class AddAttributeModifier extends BaseNode {
    public static final String TYPE_ID = "add_attribute_modifier";

    public static final String PROPERTY_SELECTED_ATTR = "selected_attribute";

    private static final String[] OPERATIONS = {"ADD_VALUE", "ADD_MULTIPLIED_BASE", "ADD_MULTIPLIED_TOTAL"};
    private static final String[] SLOTS = {"ANY", "MAINHAND", "OFFHAND", "HEAD", "CHEST", "LEGS", "FEET"};

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node." + TYPE_ID))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ITEM_STACK.toInput(), StandardPorts.ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        StandardPorts.TYPE.toInput("minecraft:generic.attack_damage"),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, "minecraft:attribute")
                ))
                // 增量值
                .addRow(new PortRow(StandardPorts.VALUE.toInput(1.0f), null, UIHint.INPUT, null, null))
                // 运算方式下拉框
                .addRow(new PortRow(
                        StandardPorts.TYPE.toInputWithIndex(1, "ADD_VALUE"),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, OPERATIONS)
                ))
                // 生效槽位下拉框
                .addRow(new PortRow(
                        StandardPorts.TYPE.toInputWithIndex(2, "MAINHAND"),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, SLOTS)
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ItemStack stack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        Float amount = getInput(context, StandardPorts.VALUE.getId(), Float.class);
        String opStr = getInput(context, StandardPorts.TYPE.getIdWithIndex(1), String.class);
        String slotStr = getInput(context, StandardPorts.TYPE.getIdWithIndex(2), String.class);
        String attrId = getInput(context, StandardPorts.TYPE.getId(), String.class);
        if (attrId == null || attrId.isEmpty()) {
            attrId = (String) context.getStaticInput(PROPERTY_SELECTED_ATTR);
        }

        if (stack != null && !stack.isEmpty() && attrId != null && !attrId.isEmpty() && amount != null) {
            ResourceLocation loc = ResourceLocation.tryParse(attrId);
            if (loc != null) {
                Optional<Holder.Reference<Attribute>> attrOpt = BuiltInRegistries.ATTRIBUTE.getHolder(loc);

                attrOpt.ifPresent(attributeHolder -> {
                    AttributeModifier.Operation operation = switch (opStr != null ? opStr : "ADD_VALUE") {
                        case "ADD_MULTIPLIED_BASE" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                        case "ADD_MULTIPLIED_TOTAL" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                        default -> AttributeModifier.Operation.ADD_VALUE;
                    };

                    EquipmentSlotGroup slotGroup = switch (slotStr != null ? slotStr : "ANY") {
                        case "MAINHAND" -> EquipmentSlotGroup.MAINHAND;
                        case "OFFHAND" -> EquipmentSlotGroup.OFFHAND;
                        case "HEAD" -> EquipmentSlotGroup.HEAD;
                        case "CHEST" -> EquipmentSlotGroup.CHEST;
                        case "LEGS" -> EquipmentSlotGroup.LEGS;
                        case "FEET" -> EquipmentSlotGroup.FEET;
                        default -> EquipmentSlotGroup.ANY;
                    };

                    ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath("geometry_node", "modifier_" + loc.getPath());
                    AttributeModifier modifier = new AttributeModifier(modifierId, amount, operation);

                    ItemAttributeModifiers current = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
                    ItemAttributeModifiers updated = current.withModifierAdded(attributeHolder, modifier, slotGroup);
                    stack.set(DataComponents.ATTRIBUTE_MODIFIERS, updated);
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