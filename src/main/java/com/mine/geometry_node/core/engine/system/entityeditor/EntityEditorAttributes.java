package com.mine.geometry_node.core.engine.system.entityeditor;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Server-owned allowlist and capability schema. The same resolved fields describe, validate and write the editor. */
final class EntityEditorAttributes {
    static final String MAX_ABSORPTION = "minecraft:max_absorption";
    static final Map<String, String> CAPACITY_STATES = Map.of("maxHealth", "health", MAX_ABSORPTION, "absorption");
    private static final String LABEL_PREFIX = "geometry_node.entity_editor.attributes.";
    private static final VarHandle PERSISTENT = persistenceFlag();

    private record State(String kind, Predicate<Entity> supported, Function<Entity, Object> read,
                         BiConsumer<Entity, Object> write, Double min, Double max) {}
    private record Definition(String key, String category, String attributeId, State state, boolean percentage) {
        boolean registered() { return attributeId != null; }
    }

    /** Wire metadata is deliberately independent of runtime entity/attribute instances. */
    record Field(String key, String labelKey, String category, String kind, Double min, Double max, boolean percentage) {
        Map<String, Object> descriptor() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("key", key); result.put("labelKey", labelKey); result.put("category", category); result.put("kind", kind);
            if (min != null) result.put("min", min);
            if (max != null) result.put("max", max);
            if (percentage) result.put("percentage", true);
            return Collections.unmodifiableMap(result);
        }
        void validate(Object supportedValue, Object value) {
            switch (kind) {
                case "name" -> {
                    if (supportedValue instanceof String && !(value instanceof String))
                        throw new IllegalArgumentException("Adapter only supports plain names");
                    EntityEditorNames.validate(value);
                }
                case "boolean" -> {
                    if (!(value instanceof Boolean)) throw new IllegalArgumentException("Expected boolean");
                }
                case "number" -> {
                    double number = number(value);
                    if (min != null && number < min || max != null && number > max)
                        throw new IllegalArgumentException("Attribute outside host range");
                }
                default -> throw new IllegalArgumentException("Unsupported attribute kind");
            }
        }
    }

    private record Resolved(Definition definition, Holder<Attribute> attribute, Field field) {
        Object read(Entity entity) {
            return attribute == null ? definition.state.read.apply(entity)
                    : Objects.requireNonNull(((LivingEntity) entity).getAttribute(attribute)).getBaseValue();
        }
        void write(Entity entity, Object value) {
            if (attribute == null) definition.state.write.accept(entity, value);
            else Objects.requireNonNull(((LivingEntity) entity).getAttribute(attribute)).setBaseValue(number(value));
        }
    }

    private static final List<Definition> DEFINITIONS = List.of(
            state("health", "life", new State("number", entity -> entity instanceof LivingEntity,
                    entity -> ((LivingEntity) entity).getHealth(),
                    (entity, value) -> ((LivingEntity) entity).setHealth((float) number(value)), (double) Float.MIN_VALUE, (double) Float.MAX_VALUE)),
            attribute("maxHealth", "life", "minecraft:max_health"),
            state("absorption", "life", new State("number", entity -> entity instanceof LivingEntity,
                    entity -> ((LivingEntity) entity).getAbsorptionAmount(),
                    (entity, value) -> ((LivingEntity) entity).setAbsorptionAmount((float) number(value)), 0D, (double) Float.MAX_VALUE)),
            attribute(MAX_ABSORPTION, "life"),
            attribute("minecraft:armor", "defense"),
            attribute("minecraft:armor_toughness", "defense"),
            percentage("minecraft:knockback_resistance", "defense"),
            percentage("minecraft:explosion_knockback_resistance", "defense"),
            attribute("attack", "attack", "minecraft:attack_damage"),
            attribute("minecraft:attack_knockback", "attack"),
            attribute("minecraft:attack_speed", "attack"),
            percentage("minecraft:sweeping_damage_ratio", "attack"),
            attribute("speed", "movement", "minecraft:movement_speed"),
            attribute("minecraft:burning_time", "survival"),
            attribute("minecraft:oxygen_bonus", "survival"),
            attribute("minecraft:safe_fall_distance", "survival"),
            attribute("minecraft:fall_damage_multiplier", "survival"),
            state("name", "appearance", new State("name", entity -> true,
                    entity -> EntityEditorNames.snapshot(entity.getCustomName(), entity.registryAccess()),
                    (entity, value) -> entity.setCustomName(EntityEditorNames.decode(value, entity.registryAccess())), null, null)),
            bool("nameVisible", "appearance", entity -> true, Entity::isCustomNameVisible, Entity::setCustomNameVisible),
            attribute("minecraft:scale", "appearance"),
            attribute("neoforge:nametag_distance", "appearance"),
            bool("noAI", "behavior", entity -> entity instanceof Mob, entity -> ((Mob) entity).isNoAi(),
                    (entity, value) -> ((Mob) entity).setNoAi(value)),
            bool("persistent", "behavior", entity -> entity instanceof Mob && PERSISTENT != null,
                    entity -> ((Mob) entity).isPersistenceRequired(), (entity, value) -> PERSISTENT.set((Mob) entity, (boolean) value)),
            bool("silent", "behavior", entity -> true, Entity::isSilent, Entity::setSilent),
            bool("invulnerable", "behavior", entity -> true, Entity::isInvulnerable, Entity::setInvulnerable),
            bool("gravity", "behavior", entity -> true, entity -> !entity.isNoGravity(), (entity, value) -> entity.setNoGravity(!value)));

    private EntityEditorAttributes() {}

    private static Definition attribute(String id, String category) { return attribute(id, category, id); }
    private static Definition attribute(String key, String category, String id) { return new Definition(key, category, id, null, false); }
    private static Definition percentage(String id, String category) { return new Definition(id, category, id, null, true); }
    private static Definition state(String key, String category, State state) { return new Definition(key, category, null, state, false); }
    private static Definition bool(String key, String category, Predicate<Entity> supports, Function<Entity, Boolean> read,
                                   BiConsumer<Entity, Boolean> write) {
        return state(key, category, new State("boolean", supports, entity -> read.apply(entity),
                (entity, value) -> write.accept(entity, (Boolean) value), null, null));
    }
    private static VarHandle persistenceFlag() {
        // Vanilla exposes only a one-way setter. Omit this capability if the host denies this typed flag accessor.
        try { return MethodHandles.privateLookupIn(Mob.class, MethodHandles.lookup()).findVarHandle(Mob.class, "persistenceRequired", boolean.class); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }

    private static List<Resolved> resolve(Entity entity) {
        List<Resolved> fields = new ArrayList<>();
        for (Definition definition : DEFINITIONS) {
            if (!definition.registered()) {
                if (definition.state.supported.test(entity)) fields.add(new Resolved(definition, null,
                        new Field(definition.key, LABEL_PREFIX + definition.key, definition.category, definition.state.kind,
                                definition.state.min, definition.state.max, false)));
                continue;
            }
            if (!(entity instanceof LivingEntity living)) continue;
            BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(definition.attributeId)).ifPresent(holder -> {
                if (living.getAttributes().hasAttribute(holder)) fields.add(new Resolved(definition, holder,
                        numericField(definition.key, definition.category, holder.value(), definition.percentage)));
            });
        }
        return List.copyOf(fields);
    }

    static Field numericField(String key, String category, Attribute attribute, boolean percentage) {
        Double min = null, max = null;
        if (attribute instanceof RangedAttribute ranged) {
            if (Double.isFinite(ranged.getMinValue())) min = ranged.getMinValue();
            if (Double.isFinite(ranged.getMaxValue())) max = ranged.getMaxValue();
        }
        return new Field(key, attribute.getDescriptionId(), category, "number", min, max, percentage);
    }

    static Map<String, Object> values(Entity entity) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Resolved field : resolve(entity)) values.put(field.definition.key, field.read(entity));
        return values;
    }
    static List<Map<String, Object>> descriptors(Entity entity) {
        return resolve(entity).stream().map(field -> field.field.descriptor()).toList();
    }
    static void validate(Entity entity, Map<String, Object> changes) {
        Map<String, Resolved> supported = new LinkedHashMap<>();
        for (Resolved field : resolve(entity)) supported.put(field.definition.key, field);
        for (var change : changes.entrySet()) {
            Resolved field = supported.get(change.getKey());
            if (field == null) throw new IllegalArgumentException("Unsupported attribute");
            field.field.validate(field.read(entity), change.getValue());
            if (field.attribute != null)
                EntityEditorAdapters.validateAttributeRange(field.attribute.value()::sanitizeValue, change.getValue());
        }
        if (changes.containsKey("name")) EntityEditorNames.component(changes.get("name"), entity.registryAccess());
        if (entity instanceof LivingEntity living) {
            if (changes.containsKey("health") || changes.containsKey("maxHealth"))
                EntityEditorAdapters.validateHealth(changes, living.getHealth(),
                        projectedMaximum(living, supported.get("maxHealth"), changes, living.getMaxHealth()));
            if (changes.containsKey("absorption") || changes.containsKey(MAX_ABSORPTION))
                validateAbsorption(changes, living.getAbsorptionAmount(),
                        projectedMaximum(living, supported.get(MAX_ABSORPTION), changes, living.getMaxAbsorption()));
        }
    }
    private static double projectedMaximum(LivingEntity entity, Resolved maximum, Map<String, Object> changes, double current) {
        // LivingEntity's maximum getters/setters use floats; match that host boundary before dependent-state validation.
        return maximum != null && changes.containsKey(maximum.definition.key)
                ? (float) projectedValue(Objects.requireNonNull(entity.getAttribute(maximum.attribute)), number(changes.get(maximum.definition.key))) : current;
    }
    static double projectedValue(AttributeInstance current, double base) {
        AttributeInstance projected = new AttributeInstance(current.getAttribute(), ignored -> {});
        projected.replaceFrom(current); projected.setBaseValue(base);
        return projected.getValue();
    }
    static void validateAbsorption(Map<String, Object> changes, double current, double maximum) {
        double absorption = changes.containsKey("absorption") ? number(changes.get("absorption")) : current;
        if (!Double.isFinite(maximum) || maximum < 0 || absorption < 0 || absorption > maximum)
            throw new IllegalArgumentException("Invalid absorption/max absorption pair");
    }
    static void apply(Entity entity, Map<String, Object> changes) {
        List<Resolved> fields = resolve(entity);
        // Apply every attribute first, so health/absorption writes use the projected maxima regardless of request order.
        for (Resolved field : fields)
            if (field.attribute != null && changes.containsKey(field.definition.key)) field.write(entity, changes.get(field.definition.key));
        for (Resolved field : fields)
            if (field.attribute == null && changes.containsKey(field.definition.key)) field.write(entity, changes.get(field.definition.key));
    }
    static double number(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))
            throw new IllegalArgumentException("Expected finite number");
        return number.doubleValue();
    }
}
