package com.mine.geometry_node.core.engine.system.scene.property;

import com.mine.geometry_node.core.cache.*;
import java.util.concurrent.CompletionStage;

import com.mine.geometry_node.core.engine.system.scene.SceneNativeInteractions;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.engine.system.scene.SceneObjectCapabilities;

import com.mine.geometry_node.core.engine.system.scene.geometry.SceneTransforms;
import com.mine.geometry_node.core.engine.system.scene.volume.SceneCollisionTransforms;
import com.mine.geometry_node.core.engine.system.scene.property.SceneInspectorSchemas.*;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaShape;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.port.*;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.actions.area.CreateArea;
import com.mine.geometry_node.core.node.nodes.actions.collision.CreateGeometryCollision;
import com.mine.geometry_node.core.node.nodes.actions.geometry.CreateMesh;
import com.mine.geometry_node.core.node.nodes.actions.display_entity.SpawnInteractionEntity;
import com.mine.geometry_node.core.node.nodes.actions.light.LightActionNode;
import com.mine.geometry_node.core.node.nodes.actions.light.SetLightPropertyNode;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.*;

/** Cached adapters for actual dynamic node definitions. No client registry, UI, or world singleton. */
public final class SceneNodeInputs {
    public enum Use { MAPPED, CONTEXT, UNSUPPORTED }
    public record Input(String nodeType, String port, Use use, String reason) {}
    private record Model(List<SceneProperty> parameters, Schema schema, List<Input> inputs) {}
    private static final Map<String, Model> MODELS = new HashMap<>();
    static {
        CacheRegistry.GLOBAL.register(
                new CacheDescriptor("geometry_node:scene.node_schemas",
                        "geometry_node.cache.scene.node_schemas",
                        CacheDescriptor.Domain.COMMON,
                        CacheDescriptor.Storage.MEMORY,
                        CacheDescriptor.Lifetime.APPLICATION),
                new ManagedCache() {
                    public CompletionStage<CacheStats> stats() {
                        return CacheExecutors.submit(() -> {
                            synchronized (SceneNodeInputs.class) {
                                return CacheStats.memory(MODELS.size(), -1);
                            }
                        });
                    }
                    public boolean supports(CacheClearRequest request) {
                        return request.namespace() == null && request.reason() == CacheClearRequest.Reason.USER;
                    }
                    public CompletionStage<Void> clear(CacheClearRequest request) {
                        return CacheExecutors.run(() -> {
                            synchronized (SceneNodeInputs.class) { MODELS.clear(); }
                        });
                    }
                });
    }
    private SceneNodeInputs() {}

    public static List<SceneProperty> properties(String typeId) { return model(typeId).parameters(); }
    public static Schema schema(String typeId) { return model(typeId).schema(); }
    /** Every non-flow input has an explicit disposition; a newly added node input fails adapter construction. */
    public static List<Input> coverage(String typeId) { return model(typeId).inputs(); }
    private static synchronized Model model(String typeId) { return MODELS.computeIfAbsent(typeId, SceneNodeInputs::build); }
    private static Model build(String typeId) {
        Builder b = new Builder();
        if (!typeId.equals(SceneObject.POINT_LIGHT) && !typeId.equals(SceneNativeInteractions.TYPE_ID) && !Set.of("geometry", "collision", "area").stream()
                .anyMatch(category -> List.of("cube", "cylinder", "uv_sphere").stream().anyMatch(shape -> typeId.equals(category + "/" + shape))))
            return new Model(List.of(), new Schema(List.copyOf(b.fields.values())), List.of());
        if (SceneNativeInteractions.TYPE_ID.equals(typeId)) {
            b.fields.remove("name");
            for (String axis : List.of("x", "y", "z")) b.fields.remove("rotation." + axis);
            b.vectorFields("scale", StandardPorts.SIZE_3.toInput().displayName(), SceneObject::scale,
                    (o, value) -> SceneObjectCapabilities.transformed(o, o.position(), o.rotation(), value));
            b.consume(new SpawnInteractionEntity().getDefinition(new NodeData()), Map.of(
                    StandardPorts.XYZ.getId(), row -> b.envelopeVector(row, "position", SceneObject::position, SceneObject::withPosition),
                    // The type validator bounds effective dimensions after generic scale is applied.
                    StandardPorts.WIDTH.getId(), row -> b.scalar(row, "width", Double.MIN_VALUE, Double.MAX_VALUE),
                    StandardPorts.HEIGHT.getId(), row -> b.scalar(row, "height", Double.MIN_VALUE, Double.MAX_VALUE),
                    StandardPorts.RESPONSIVE.getId(), row -> b.scalar(row, "responsive", 0, 1)));
        } else if (SceneObject.POINT_LIGHT.equals(typeId)) {
            NodeData data = data("create_light", "type", "point");
            NodeDef def = new LightActionNode(LightActionNode.Operation.CREATE).getDefinition(data);
            Map<String, Consumer<PortRow>> lightBindings = new HashMap<>();
            lightBindings.put(StandardPorts.KEY.getId(), row -> b.context(row, o -> "scene:" + o.id(), "Scene object UUID supplies resource ownership"));
            lightBindings.put("type", row -> b.context(row, o -> "point", "Fixed point-light preset"));
            lightBindings.put(StandardPorts.SPATIAL_ANCHOR.getId(), row -> b.provided(row, "World anchor supplied by the enclosing scene"));
            lightBindings.put("color", b::color);
            lightBindings.put("world_offset", row -> b.envelopeVector(row, "position", SceneObject::position, SceneObject::withPosition));
            lightBindings.put("world_rotation", row -> b.nodeRotation(row, false));
            lightBindings.put("local_offset", row -> b.provided(row, "Scene objects use their world transform; local offset is zero"));
            lightBindings.put("local_rotation", row -> b.provided(row, "Scene objects use their world transform; local rotation is zero"));
            lightBindings.put("config", row -> b.provided(row, "The scene preset supplies default light configuration"));
            lightBindings.put("source_visible", row -> b.scalar(row, "source_visible", 0, 1));
            lightBindings.put("intensity", row -> b.scalar(row, "intensity", 0, 1024));
            lightBindings.put("range", row -> b.scalar(row, "range", .01, 32));
            lightBindings.put("source_size", row -> b.scalar(row, "source_size", 0, 4));
            b.consume(def, lightBindings);
            b.consume(new SetLightPropertyNode(SetLightPropertyNode.Property.LIFECYCLE).getDefinition(data), Map.of(
                    "resource_id", row -> b.provided(row, "Scene object UUID identifies the existing light"),
                    "duration_ticks", row -> b.unsupported(row, "Persisted scene objects have no timed destruction lifecycle"),
                    "enabled", row -> b.enabled(row)));
        } else if (typeId.startsWith("geometry/") || typeId.startsWith("collision/")) {
            String shape = typeId.substring(typeId.indexOf('/') + 1);
            boolean collision = typeId.startsWith("collision/");
            NodeDef def = new CreateMesh().getDefinition(data(CreateMesh.TYPE_ID, "shape", shape));
            Map<String, Consumer<PortRow>> bindings = new HashMap<>();
            bindings.put("shape", row -> b.context(row, o -> shape, "Fixed primitive preset"));
            bindings.put("center", row -> b.parameterVector(row, "center", -256, 256));
            switch (GeometryValue.PrimitiveType.fromId(shape)) {
                case CUBE -> {
                    bindings.put("size_3", row -> b.parameterVector(row, new String[]{"width", "height", "depth"}, .01, 256));
                    for (String axis : List.of("x", "y", "z")) bindings.put("vertices_" + axis, row -> {
                        if (collision) b.unsupported(row, "Solid box collision has no surface subdivisions");
                        else b.scalar(row, row.leftPort().id(), 2, 32);
                    });
                }
                case CYLINDER -> {
                    bindings.put("radius", row -> b.scalar(row, "radius", .01, 256));
                    bindings.put("depth", row -> b.scalar(row, "height", .01, 256));
                    bindings.put("vertices", row -> b.scalar(row, "segments", 3, 64));
                    for (String port : List.of("fill_type", "side_segments", "fill_segments")) bindings.put(port, row -> {
                        if (collision) b.unsupported(row, "Collision is a closed solid; surface fill and subdivisions do not change it");
                        else b.scalar(row, row.leftPort().id(), 1, row.leftPort().id().equals("fill_segments") ? 16 : 32);
                    });
                }
                case UV_SPHERE -> {
                    bindings.put("radius", row -> b.scalar(row, "radius", .01, 256));
                    bindings.put("vertices", row -> b.scalar(row, "segments", 3, 64));
                    bindings.put("rings", row -> b.scalar(row, "rings", 2, 32));
                }
            }
            b.consume(def, bindings);
            if (collision) b.consume(new CreateGeometryCollision().getDefinition(new NodeData()), Map.of(
                    StandardPorts.KEY.getId(), row -> b.context(row, o -> "scene:" + o.id(), "Scene object UUID supplies resource ownership"),
                    "geometry", row -> b.context(row, SceneObject::typeId, "Provided by the selected CreateMesh primitive"),
                    StandardPorts.SPATIAL_ANCHOR.getId(), row -> b.provided(row, "World anchor supplied by the enclosing scene"),
                    "world_offset", row -> b.envelopeVector(row, "position", SceneObject::position, SceneObject::withPosition),
                    "world_rotation", row -> b.nodeRotation(row, true),
                    "pivot", row -> b.parameterVector(row, "pivot", -256, 256),
                    "local_offset", row -> b.parameterVector(row, "translation", -256, 256),
                    "local_rotation", row -> b.parameterVector(row, "local_rotation", -36000, 36000),
                    "size_3", row -> b.envelopeVector(row, "scale", SceneObject::scale,
                            (o, value) -> o.withTransform(o.position(), o.rotation(), value))));
        } else if (typeId.startsWith("area/")) {
            String shape = switch (typeId) {
                case "area/cube" -> AreaShape.BOX.id();
                case "area/cylinder" -> AreaShape.CYLINDER.id();
                case "area/uv_sphere" -> AreaShape.SPHERE.id();
                default -> throw new IllegalArgumentException("Unsupported area preset: " + typeId);
            };
            NodeData data = data(CreateArea.TYPE_ID, CreateArea.SHAPE_PORT, shape);
            NodeDef def = new CreateArea().getDefinition(data);
            Map<String, Consumer<PortRow>> bindings = new HashMap<>();
            bindings.put(StandardPorts.AREA_ID.getId(), row -> b.context(row, o -> "scene/" + o.id(), "Scene object UUID supplies the area ID"));
            bindings.put(StandardPorts.SPATIAL_ANCHOR.getId(), row -> b.provided(row, "World anchor supplied by the enclosing scene"));
            bindings.put("dimension", row -> b.provided(row, "Provided by the enclosing scene document; not stored on the object"));
            bindings.put(CreateArea.SHAPE_PORT, row -> b.context(row, o -> shape, "Fixed area shape preset"));
            bindings.put("world_offset", row -> b.envelopeVector(row, "position", SceneObject::position, SceneObject::withPosition));
            bindings.put("world_rotation", row -> b.nodeRotation(row, false));
            bindings.put("local_offset", row -> b.provided(row, "The scene object has zero local offset"));
            bindings.put("local_rotation", row -> b.provided(row, "The scene object has zero local rotation"));
            bindings.put("size_3", row -> b.parameterVector(row, new String[]{"width", "height", "depth"}, .01, 256));
            bindings.put("radius", row -> b.scalar(row, "radius", .01, 256));
            bindings.put("height", row -> b.scalar(row, "height", .01, 256));
            b.consume(def, bindings);
        }
        return new Model(List.copyOf(b.parameters.values()), new Schema(List.copyOf(b.fields.values())), List.copyOf(b.inputs));
    }
    private static NodeData data(String type, String key, String value) {
        NodeData data = new NodeData(); data.type = type; data.inputs.put(key, value); return data;
    }
    public static Vec3 vector(SceneObject object, String prefix) {
        return new Vec3(object.number(prefix + ".x"), object.number(prefix + ".y"), object.number(prefix + ".z"));
    }
    public static SceneObject parameter(SceneObject object, String key, String value) {
        Map<String, String> parameters = new LinkedHashMap<>(object.parameters()); parameters.put(key, value);
        return object.withProperties(object.name(), object.color(), object.enabled(), parameters);
    }
    private static double component(Vec3 value, int axis) { return axis == 0 ? value.x : axis == 1 ? value.y : value.z; }
    private static Vec3 component(Vec3 value, int axis, double number) {
        return new Vec3(axis == 0 ? number : value.x, axis == 1 ? number : value.y, axis == 2 ? number : value.z);
    }
    private static List<Choice> choices(PortRow row) {
        if (row.hintParams() == null) return List.of();
        String[] values = (String[]) row.hintParams().get(PortMetaKeys.OPTIONS);
        String[] labels = (String[]) row.hintParams().get(PortMetaKeys.OPTION_LABELS);
        if (values == null) return List.of();
        List<Choice> result = new ArrayList<>();
        for (int i = 0; i < values.length; i++) result.add(new Choice(values[i], labels != null && i < labels.length
                ? Component.translatable(labels[i]) : Component.literal(values[i])));
        return List.copyOf(result);
    }
    private static final class Builder {
        final Map<String, SceneProperty> parameters = new LinkedHashMap<>();
        final Map<String, Field> fields = new LinkedHashMap<>();
        final List<Input> inputs = new ArrayList<>();
        Builder() {
            put(new Field("name", Component.translatable("geometry_node.game_view.scene.name"), Kind.TEXT, List.of(),
                    SceneObject::name, (o, value) -> o.withProperties(value, o.color(), o.enabled(), o.parameters())));
            vectorFields("position", StandardPorts.WORLD_POSITION.toInput().displayName(), SceneObject::position, SceneObject::withPosition);
            vectorFields("rotation", StandardPorts.ROTATION.toInput().displayName().copy().append(" (XYZ °)"),
                    o -> o.rotation().scale(180 / Math.PI),
                    (o, value) -> o.withTransform(o.position(), value.scale(Math.PI / 180), o.scale()));
            vectorFields("scale", StandardPorts.SIZE_3.toInput().displayName(), SceneObject::scale,
                    (o, value) -> o.withTransform(o.position(), o.rotation(), value));
        }
        void put(Field field) { fields.put(field.key(), field); }
        void consume(NodeDef def, Map<String, Consumer<PortRow>> bindings) {
            for (PortRow row : def.rows()) {
                if (row.leftPort() == null || row.leftPort().type().isFlow()) continue;
                Consumer<PortRow> binding = bindings.get(row.leftPort().id());
                if (binding == null) throw new IllegalStateException("Unaccounted scene node input: " + def.typeId() + "/" + row.leftPort().id());
                int start = inputs.size(); binding.accept(row);
                if (inputs.size() == start) inputs.add(new Input(def.typeId(), row.leftPort().id(), Use.MAPPED, "Bound to an object field"));
                else {
                    Input input = inputs.getLast();
                    inputs.set(inputs.size() - 1, new Input(def.typeId(), input.port(), input.use(), input.reason()));
                }
            }
        }
        void context(PortRow row, Function<SceneObject, String> getter, String reason) {
            put(new Field("context." + row.leftPort().id(), row.leftPort().displayName(), choices(row).isEmpty() ? Kind.TEXT : Kind.CHOICE,
                    choices(row), false, getter, null));
            inputs.add(new Input("", row.leftPort().id(), Use.CONTEXT, reason));
        }
        void unsupported(PortRow row, String reason) { inputs.add(new Input("", row.leftPort().id(), Use.UNSUPPORTED, reason)); }
        void provided(PortRow row, String reason) { inputs.add(new Input("", row.leftPort().id(), Use.CONTEXT, reason)); }
        void enabled(PortRow row) {
            if (row.leftPort().type() != PortType.BOOLEAN) throw new IllegalArgumentException("Expected boolean lifecycle input");
            put(new Field("enabled", row.leftPort().displayName(), Kind.BOOLEAN, List.of(),
                    o -> Boolean.toString(o.enabled()),
                    (o, text) -> o.withProperties(o.name(), o.color(), Boolean.parseBoolean(text), o.parameters())));
        }
        void scalar(PortRow row, String key, double min, double max) {
            PortDef port = row.leftPort(); List<Choice> options = choices(row);
            Kind kind = !options.isEmpty() ? Kind.CHOICE : switch (port.type()) {
                case FLOAT, INTEGER, LONG -> Kind.NUMBER;
                case BOOLEAN -> Kind.BOOLEAN;
                case STRING, PATH -> Kind.TEXT;
                default -> throw new IllegalArgumentException("Unsupported scalar input: " + port.id());
            };
            SceneProperty property;
            if (kind == Kind.NUMBER) {
                Number nodeMin = row.hintParams() == null ? null : (Number) row.hintParams().get(PortMetaKeys.NUMERIC_MIN);
                Number nodeMax = row.hintParams() == null ? null : (Number) row.hintParams().get(PortMetaKeys.NUMERIC_MAX);
                property = SceneProperty.number(key, ((Number) port.defaultValue()).doubleValue(),
                        nodeMin == null ? min : nodeMin.doubleValue(), nodeMax == null ? max : nodeMax.doubleValue(), port.type() != PortType.FLOAT);
            } else property = new SceneProperty(key, "", String.valueOf(port.defaultValue()), text -> {
                if (kind == Kind.CHOICE && options.stream().noneMatch(option -> option.value().equals(text)))
                    throw new IllegalArgumentException("Invalid choice: " + key);
                if (kind == Kind.BOOLEAN && !text.equals("true") && !text.equals("false"))
                    throw new IllegalArgumentException("Invalid boolean: " + key);
            });
            parameters.put(key, property);
            put(new Field(key, port.displayName(), kind, options, o -> o.parameters().get(key), (o, text) -> {
                property.validate().accept(text); return parameter(o, key, text);
            }));
        }
        void parameterVector(PortRow row, String prefix, double min, double max) {
            parameterVector(row, new String[]{prefix + ".x", prefix + ".y", prefix + ".z"}, min, max);
        }
        void parameterVector(PortRow row, String[] keys, double min, double max) {
            PortDef port = row.leftPort();
            if (port.type() != PortType.XYZ) throw new IllegalArgumentException("Expected XYZ input: " + port.id());
            Vec3 fallback = (Vec3) port.defaultValue();
            for (int axis = 0; axis < 3; axis++) {
                String key = keys[axis]; SceneProperty property = SceneProperty.number(key, component(fallback, axis), min, max, false);
                parameters.put(key, property);
                put(new Field(key, port.displayName().copy().append(" " + "XYZ".charAt(axis)), Kind.NUMBER, List.of(),
                        o -> o.parameters().get(key), (o, text) -> { property.validate().accept(text); return parameter(o, key, text); }));
            }
        }
        void envelopeVector(PortRow row, String key, Function<SceneObject, Vec3> getter, BiFunction<SceneObject, Vec3, SceneObject> setter) {
            if (row.leftPort().type() != PortType.XYZ) throw new IllegalArgumentException("Expected XYZ input");
            vectorFields(key, row.leftPort().displayName(), getter, setter);
        }
        void vectorFields(String key, Component label, Function<SceneObject, Vec3> getter, BiFunction<SceneObject, Vec3, SceneObject> setter) {
            var binding = new SceneInspectorSchemas.VectorBinding(getter, setter);
            for (int axis = 0; axis < 3; axis++) {
                int index = axis;
                put(new Field(key + "." + "xyz".charAt(axis), label.copy().append(" " + "XYZ".charAt(axis)), Kind.NUMBER, List.of(),
                        o -> Double.toString(component(getter.apply(o), index)),
                        binding.component(index)));
            }
        }
        void nodeRotation(PortRow row, boolean collision) {
            vectorFields("rotation", row.leftPort().displayName().copy().append(" (YXZ °)"),
                    o -> collision ? SceneCollisionTransforms.nodeAngles(o.rotation()) : SceneTransforms.yxzDegrees(o.rotation()),
                    (o, value) -> o.withTransform(o.position(), collision ? SceneCollisionTransforms.sceneAngles(value) : SceneTransforms.xyzRadians(value), o.scale()));
        }
        void color(PortRow row) {
            if (row.leftPort().type() != PortType.COLOR) throw new IllegalArgumentException("Expected color input");
            for (int axis = 0; axis < 3; axis++) {
                int index = axis;
                put(new Field("color." + "rgb".charAt(axis), row.leftPort().displayName().copy().append(" " + "RGB".charAt(axis)),
                        Kind.NUMBER, List.of(), o -> Double.toString(component(o.color(), index)),
                        (o, text) -> o.withProperties(o.name(), component(o.color(), index, Double.parseDouble(text)), o.enabled(), o.parameters())));
            }
        }
    }
}
