package com.mine.geometry_node.core.engine.system.scene;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Registration seam for future authored geometry, model and schematic types. */
public final class SceneObjectTypes {
    public record Type(String id, Consumer<SceneObject> validate) {
        public Type { Objects.requireNonNull(id); Objects.requireNonNull(validate); }
    }
    private static final Map<String, Type> TYPES = new LinkedHashMap<>();
    static { register(new Type(SceneObject.POINT_LIGHT, ignored -> {})); }
    private SceneObjectTypes() {}
    public static synchronized void register(Type type) {
        if (TYPES.putIfAbsent(type.id(), type) != null) throw new IllegalArgumentException("Duplicate scene type: " + type.id());
    }
    public static synchronized List<Type> all() { return List.copyOf(TYPES.values()); }
    public static synchronized void validate(SceneObject object) {
        Type type = TYPES.get(object.typeId());
        if (type == null) throw new IllegalArgumentException("Unsupported scene object type: " + object.typeId());
        type.validate().accept(object);
    }
}
