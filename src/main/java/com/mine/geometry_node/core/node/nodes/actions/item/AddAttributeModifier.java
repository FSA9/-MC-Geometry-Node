package com.mine.geometry_node.core.node.nodes.actions.item;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
                .addRow(new PortRow(null, StandardPorts.RESULT_ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ITEM_STACK.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.TYPE.toInput("minecraft:generic.attack_damage"), UIHint.SELECT, null, Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, "minecraft:attribute"))
                // 增量值
                .addPassthroughInput(StandardPorts.FLOAT_VALUE.toInput(1.0f), UIHint.INPUT)
                // 运算方式下拉框
                .addPassthroughInput(StandardPorts.TYPE.toInputWithIndex(1, "ADD_VALUE"), UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, OPERATIONS))
                // 生效槽位下拉框
                .addPassthroughInput(StandardPorts.TYPE.toInputWithIndex(2, "MAINHAND"), UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, SLOTS))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        context.setNodeResult(StandardPorts.RESULT_ITEM_STACK.getId(), null);
        ItemStack stack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        Float amount = getInput(context, StandardPorts.FLOAT_VALUE.getId(), Float.class);
        String opStr = getInput(context, StandardPorts.TYPE.getIdWithIndex(1), String.class);
        String slotStr = getInput(context, StandardPorts.TYPE.getIdWithIndex(2), String.class);
        String attrId = getInput(context, StandardPorts.TYPE.getId(), String.class);
        if (attrId == null || attrId.isEmpty()) {
            attrId = (String) context.getStaticInput(PROPERTY_SELECTED_ATTR);
        }

        if (stack != null && !stack.isEmpty() && attrId != null && !attrId.isEmpty() && amount != null) {
            Identifier loc = Identifier.tryParse(attrId);
            if (loc != null) {
                Optional<Holder<Attribute>> attrOpt = BuiltInRegistries.ATTRIBUTE
                        .getOptional(loc)
                        .map(BuiltInRegistries.ATTRIBUTE::wrapAsHolder);

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

                    Identifier modifierId = Identifier.fromNamespaceAndPath("geometry_node", "modifier_" + loc.getPath());
                    AttributeModifier modifier = new AttributeModifier(modifierId, amount, operation);

                    ItemAttributeModifiers current = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
                    ItemAttributeModifiers updated = current.withModifierAdded(attributeHolder, modifier, slotGroup);
                    stack.set(DataComponents.ATTRIBUTE_MODIFIERS, updated);
                });
            }
            context.setNodeResult(StandardPorts.RESULT_ITEM_STACK.getId(), stack);
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.RESULT_ITEM_STACK.getId().equals(portName)) return context.getNodeResult(StandardPorts.RESULT_ITEM_STACK.getId());
        return null;
    }
}
