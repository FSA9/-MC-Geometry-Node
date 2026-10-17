package com.mine.geometry_node.core.engine.system.scene.property;

import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Type plugins own bindings; inspectors only render fields and submit a batch of text values. */
public final class SceneInspectorSchemas {
    public enum Kind { TEXT, NUMBER, BOOLEAN, CHOICE }
    public record Choice(String value, Component label) {
        public Choice { Objects.requireNonNull(value); Objects.requireNonNull(label); }
    }
    public record Field(String key, Component label, Kind kind, List<Choice> options, boolean editable,
                        Function<SceneObject, String> getter,
                        BiFunction<SceneObject, String, SceneObject> setter) {
        public Field {
            Objects.requireNonNull(key); Objects.requireNonNull(label); Objects.requireNonNull(kind);
            Objects.requireNonNull(getter); options = List.copyOf(options);
            if (key.isBlank() || (editable && setter == null)) throw new IllegalArgumentException("Invalid field binding");
        }
        public Field(String key, Component label, Kind kind, List<Choice> options,
                     Function<SceneObject, String> getter, BiFunction<SceneObject, String, SceneObject> setter) {
            this(key, label, kind, options, setter != null, getter, setter);
        }
        public String value(SceneObject object) { return getter.apply(object); }
        private void check(String value) {
            Objects.requireNonNull(value);
            if (kind == Kind.NUMBER && !Double.isFinite(Double.parseDouble(value)))
                throw new IllegalArgumentException("Invalid number: " + key);
            if (kind == Kind.BOOLEAN && !value.equals("true") && !value.equals("false"))
                throw new IllegalArgumentException("Invalid boolean: " + key);
            if (kind == Kind.CHOICE && options.stream().noneMatch(option -> option.value().equals(value)))
                throw new IllegalArgumentException("Invalid choice: " + key);
        }
    }
    public record Schema(List<Field> fields) {
        public Schema {
            fields = List.copyOf(fields);
            Set<String> keys = new HashSet<>();
            for (Field field : fields) if (!keys.add(field.key())) throw new IllegalArgumentException("Duplicate field: " + field.key());
        }
        /** Missing fields retain their values, including hidden type parameters and lifecycle state. */
        public SceneObject apply(SceneObject original, Map<String, String> values) {
            Map<String, Field> byKey = new HashMap<>();
            for (Field field : fields) byKey.put(field.key(), field);
            values.forEach((key, value) -> {
                Field field = byKey.get(key);
                if (field == null || !field.editable()) throw new IllegalArgumentException("Field is not editable: " + key);
                field.check(value);
            });
            SceneObject result = original;
            Set<VectorBinding> applied = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Field field : fields) if (values.containsKey(field.key())) {
                if (field.setter() instanceof VectorSetter component) {
                    VectorBinding binding = component.binding();
                    if (!applied.add(binding)) continue;
                    Vec3 vector = binding.getter.apply(original);
                    boolean changed = false;
                    // Read all requested components before converting Euler conventions exactly once.
                    for (Field part : fields) if (part.setter() instanceof VectorSetter axis
                            && axis.binding() == binding && values.containsKey(part.key()) && !unchanged(part, original, values.get(part.key()))) {
                        vector = replace(vector, axis.axis(), Double.parseDouble(values.get(part.key())));
                        changed = true;
                    }
                    if (changed) result = binding.setter.apply(result, vector);
                } else if (!unchanged(field, original, values.get(field.key())))
                    result = field.setter().apply(result, values.get(field.key()));
            }
            if (!result.id().equals(original.id()) || !result.typeId().equals(original.typeId())
                    || result.revision() != original.revision()) throw new IllegalArgumentException("Inspector changed object identity");
            SceneObjectTypes.validate(result);
            return result;
        }
    }

    private static boolean unchanged(Field field, SceneObject object, String value) {
        String previous = field.value(object);
        return Objects.equals(previous, value) || (field.kind() == Kind.NUMBER
                && Double.parseDouble(previous) == Double.parseDouble(value));
    }
    static final class VectorBinding {
        final Function<SceneObject, Vec3> getter;
        final BiFunction<SceneObject, Vec3, SceneObject> setter;
        VectorBinding(Function<SceneObject, Vec3> getter, BiFunction<SceneObject, Vec3, SceneObject> setter) {
            this.getter = getter; this.setter = setter;
        }
        BiFunction<SceneObject, String, SceneObject> component(int axis) { return new VectorSetter(this, axis); }
    }
    private record VectorSetter(VectorBinding binding, int axis) implements BiFunction<SceneObject, String, SceneObject> {
        @Override public SceneObject apply(SceneObject object, String value) {
            return binding.setter.apply(object, replace(binding.getter.apply(object), axis, Double.parseDouble(value)));
        }
    }
    private static Vec3 replace(Vec3 value, int axis, double number) {
        return new Vec3(axis == 0 ? number : value.x, axis == 1 ? number : value.y, axis == 2 ? number : value.z);
    }

    private static final Map<String, Function<SceneObject, Schema>> PLUGINS = new HashMap<>();
    private SceneInspectorSchemas() {}
    public static synchronized void register(String typeId, Function<SceneObject, Schema> factory) {
        if (typeId == null || typeId.isBlank()) throw new IllegalArgumentException("Missing scene type");
        if (PLUGINS.putIfAbsent(typeId, Objects.requireNonNull(factory)) != null)
            throw new IllegalArgumentException("Duplicate inspector: " + typeId);
    }
    public static Schema schema(SceneObject object) {
        Function<SceneObject, Schema> factory;
        synchronized (SceneInspectorSchemas.class) { factory = PLUGINS.get(object.typeId()); }
        if (factory != null) return Objects.requireNonNull(factory.apply(object));
        return SceneNodeInputs.schema(object.typeId());
    }
}
