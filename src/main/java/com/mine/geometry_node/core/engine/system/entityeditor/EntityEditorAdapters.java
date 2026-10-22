package com.mine.geometry_node.core.engine.system.entityeditor;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import java.lang.invoke.*;
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
        // Vanilla has only a one-way public persistence setter. This is a single typed flag accessor,
        // never an NBT restore or a generic field editor. Omit the capability if the host denies access.
        private static final VarHandle PERSISTENT = persistenceFlag();
        private static VarHandle persistenceFlag() {
            try { return MethodHandles.privateLookupIn(Mob.class, MethodHandles.lookup()).findVarHandle(Mob.class, "persistenceRequired", boolean.class); }
            catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
        }
        @Override public boolean supports(Entity entity) { return true; }
        @Override public Map<String, Object> attributes(Entity entity) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("name", entity.getCustomName() == null ? "" : entity.getCustomName().getString());
            values.put("nameVisible", entity.isCustomNameVisible()); values.put("silent", entity.isSilent());
            values.put("invulnerable", entity.isInvulnerable()); values.put("gravity", !entity.isNoGravity());
            if (entity instanceof LivingEntity living) {
                values.put("health", living.getHealth());
                for (String field : List.of("maxHealth", "speed", "attack")) {
                    AttributeInstance attribute = living.getAttribute(attribute(field));
                    if (attribute != null) values.put(field, attribute.getBaseValue());
                }
            }
            if (entity instanceof Mob mob) {
                values.put("noAI", mob.isNoAi());
                if (PERSISTENT != null) values.put("persistent", mob.isPersistenceRequired());
            }
            return values;
        }
        @Override public void validateAttributes(Entity entity, Map<String, Object> changes) {
            validateValues(attributes(entity), changes);
            if (entity instanceof LivingEntity living) {
                for (String field : List.of("maxHealth", "speed", "attack")) {
                    if (!changes.containsKey(field)) continue;
                    validateAttributeRange(attribute(field).value()::sanitizeValue, changes.get(field));
                }
                if (changes.containsKey("health") || changes.containsKey("maxHealth")) {
                    double max = living.getMaxHealth();
                    if (changes.containsKey("maxHealth")) {
                        AttributeInstance current = Objects.requireNonNull(living.getAttribute(Attributes.MAX_HEALTH));
                        AttributeInstance projected = new AttributeInstance(Attributes.MAX_HEALTH, ignored -> {});
                        projected.replaceFrom(current); projected.setBaseValue(number(changes.get("maxHealth")));
                        max = projected.getValue();
                    }
                    validateHealth(changes, living.getHealth(), max);
                }
            }
        }
        @Override public void applyAttributes(Entity entity, Map<String, Object> changes) {
            // Maximum before health, regardless of the client's insertion order.
            if (entity instanceof LivingEntity living) {
                for (String field : List.of("maxHealth", "speed", "attack"))
                    if (changes.containsKey(field)) Objects.requireNonNull(living.getAttribute(attribute(field))).setBaseValue(number(changes.get(field)));
                if (changes.containsKey("health")) living.setHealth((float) number(changes.get("health")));
            }
            for (var field : changes.entrySet()) switch (field.getKey()) {
                case "name" -> { String name = (String) field.getValue(); entity.setCustomName(name.isEmpty() ? null : Component.literal(name)); }
                case "nameVisible" -> entity.setCustomNameVisible((Boolean) field.getValue());
                case "silent" -> entity.setSilent((Boolean) field.getValue());
                case "invulnerable" -> entity.setInvulnerable((Boolean) field.getValue());
                case "gravity" -> entity.setNoGravity(!(Boolean) field.getValue());
                case "noAI" -> ((Mob) entity).setNoAi((Boolean) field.getValue());
                case "persistent" -> PERSISTENT.set((Mob) entity, (boolean) (Boolean) field.getValue());
                default -> { /* Numeric attributes were applied above. */ }
            }
        }
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

    /** Host-independent shape/range validation also used by isolated server validation tests. */
    public static void validateValues(Map<String, Object> supported, Map<String, Object> changes) {
        for (var field : changes.entrySet()) {
            String key = field.getKey(); Object value = field.getValue();
            if (!supported.containsKey(key)) throw new IllegalArgumentException("Unsupported attribute");
            switch (key) {
                case "name" -> {
                    if (!(value instanceof String text) || text.length() > 256 || text.codePoints().anyMatch(Character::isISOControl))
                        throw new IllegalArgumentException("Invalid name");
                }
                case "nameVisible", "noAI", "persistent", "silent", "invulnerable", "gravity" -> {
                    if (!(value instanceof Boolean)) throw new IllegalArgumentException("Expected boolean");
                }
                case "health", "maxHealth", "speed", "attack" -> {
                    double number = number(value);
                    if (number < 0 || number > 1_000_000 || (key.equals("health") || key.equals("maxHealth")) && number <= 0)
                        throw new IllegalArgumentException("Attribute outside editor range");
                }
                default -> throw new IllegalArgumentException("Unsupported attribute");
            }
        }
    }
    private static double number(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) throw new IllegalArgumentException("Expected finite number");
        return number.doubleValue();
    }
    static void validateAttributeRange(DoubleUnaryOperator sanitize, Object requested) {
        double value = number(requested);
        if (Double.compare(sanitize.applyAsDouble(value), value) != 0) throw new IllegalArgumentException("Attribute outside host range");
    }
    static void validateHealth(Map<String, Object> changes, double currentHealth, double projectedMaximum) {
        double health = changes.containsKey("health") ? number(changes.get("health")) : currentHealth;
        if (!Double.isFinite(projectedMaximum) || health > projectedMaximum || health <= 0 || projectedMaximum <= 0)
            throw new IllegalArgumentException("Invalid health/max health pair");
    }
    private static Holder<Attribute> attribute(String field) {
        return switch (field) {
            case "maxHealth" -> Attributes.MAX_HEALTH;
            case "speed" -> Attributes.MOVEMENT_SPEED;
            case "attack" -> Attributes.ATTACK_DAMAGE;
            default -> throw new IllegalArgumentException("Unknown numeric attribute");
        };
    }
}
