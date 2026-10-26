package com.mine.geometry_node.core.engine.system.entity.edit;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.DoubleUnaryOperator;

/** Capability adapters, independent of concrete registry ids. Numeric attributes expose base values, retaining all modifiers. */
public final class EntityEditorAdapters {
    public static final List<EquipmentSlot> EQUIPMENT = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);
    private static final List<EntityEditorAdapter> EXTENSIONS = new CopyOnWriteArrayList<>();
    private static final EntityEditorAdapter BUILTIN = new Builtin();
    private EntityEditorAdapters() {}
    public static void register(EntityEditorAdapter adapter) { EXTENSIONS.add(Objects.requireNonNull(adapter)); }
    public static EntityEditorAdapter resolve(Entity entity) {
        for (EntityEditorAdapter adapter : EXTENSIONS) if (adapter.supports(entity)) return adapter;
        return BUILTIN;
    }

    private static final class Builtin implements EntityEditorAdapter {
        @Override public boolean supports(Entity entity) { return true; }
        @Override public Map<String, Object> attributes(Entity entity) { return EntityEditorAttributes.values(entity); }
        @Override public List<Map<String, Object>> attributeFields(Entity entity) { return EntityEditorAttributes.descriptors(entity); }
        @Override public void validateAttributes(Entity entity, Map<String, Object> changes) { EntityEditorAttributes.validate(entity, changes); }
        @Override public void applyAttributes(Entity entity, Map<String, Object> changes) { EntityEditorAttributes.apply(entity, changes); }
        @Override public List<EquipmentSlot> equipmentSlots(Entity entity) {
            return entity instanceof LivingEntity living ? EQUIPMENT.stream().filter(living::canUseSlot).toList() : List.of();
        }
        @Override public boolean acceptsEquipment(Entity entity, EquipmentSlot slot, ItemStack stack) {
            if (!(entity instanceof LivingEntity living) || !equipmentSlots(entity).contains(slot)) return false;
            if (stack.isEmpty()) return true;
            if (stack.getCount() > (slot.isArmor() ? 1 : stack.getMaxStackSize())) return false;
            return slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND || living.isEquippableInSlot(stack, slot);
        }
    }

    /** Host-independent shape validation for extension adapters and isolated protocol tests.
     * Host bounds belong to the registered attribute schema, not a second editor-wide numeric limit. */
    public static void validateValues(Map<String, Object> supported, Map<String, Object> changes) {
        for (var field : changes.entrySet()) {
            String key = field.getKey();
            if (!supported.containsKey(key)) throw new IllegalArgumentException("Unsupported attribute");
            Object previous = supported.get(key);
            String kind = key.equals("name") ? "name" : previous instanceof Number ? "number" : previous instanceof Boolean ? "boolean" : "";
            Double min = key.equals("health") ? (double) Float.MIN_VALUE : null;
            Double max = key.equals("health") ? (double) Float.MAX_VALUE : null;
            new EntityEditorAttributes.Field(key, "", "", kind, min, max, false).validate(previous, field.getValue());
        }
    }
    private static double number(Object value) { return EntityEditorAttributes.number(value); }
    static void validateAttributeRange(DoubleUnaryOperator sanitize, Object requested) {
        double value = number(requested);
        if (Double.compare(sanitize.applyAsDouble(value), value) != 0) throw new IllegalArgumentException("Attribute outside host range");
    }
    static void validateHealth(Map<String, Object> changes, double currentHealth, double projectedMaximum) {
        double health = changes.containsKey("health") ? number(changes.get("health")) : currentHealth;
        if (!Double.isFinite(projectedMaximum) || health > projectedMaximum || health <= 0 || projectedMaximum <= 0)
            throw new IllegalArgumentException("Invalid health/max health pair");
    }
}
