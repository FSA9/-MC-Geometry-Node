package com.mine.geometry_node.core.engine.system.entity.edit;

import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceSpec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Shared, lossless editor carrier. Target availability and parameter schemas are validated by the server. */
public final class EntityEditorAppearance {
    private static final Set<String> KEYS = Set.of("version", "targetType", "parameters");

    private EntityEditorAppearance() {}

    public static Map<String, Object> values(EntityAppearanceSpec spec) {
        return Map.of("version", spec.version(), "targetType", spec.targetTypeId(), "parameters", spec.parameters());
    }

    /** No coercion, omitted defaults, extra keys, NBT or captured templates are accepted. */
    public static EntityAppearanceSpec read(Object value) {
        if (!(value instanceof Map<?, ?> map) || !map.keySet().equals(KEYS)
                || !(map.get("version") instanceof Integer version)
                || !(map.get("targetType") instanceof String target)
                || !(map.get("parameters") instanceof Map<?, ?> raw)
                || raw.size() > EntityAppearanceSpec.MAX_PARAMETERS)
            throw new IllegalArgumentException("Invalid appearance carrier");
        Map<String, String> parameters = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof String parameter))
                throw new IllegalArgumentException("Expected string appearance parameters");
            parameters.put(key, parameter);
        }
        return new EntityAppearanceSpec(version, target, parameters);
    }
}
