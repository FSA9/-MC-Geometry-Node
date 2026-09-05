package com.mine.geometry_node.core.engine.graph.value;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mine.geometry_node.core.node.value.SlotRef;
import com.mine.geometry_node.core.node.value.color.ColorValue;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Built-in JSON codecs referenced by persistent graph value registrations. */
final class GraphValueJsonCodecs {
    private static final Gson GSON = new Gson();
    private static final String DYNAMIC_TYPE = "type";
    private static final String DYNAMIC_VALUE = "value";

    static final GraphValueJsonCodec INTEGER = codec(
            (value, registries) -> new JsonPrimitive(number(value).intValue()),
            (value, registries) -> value.getAsInt());
    static final GraphValueJsonCodec LONG = codec(
            (value, registries) -> new JsonPrimitive(number(value).longValue()),
            (value, registries) -> value.getAsLong());
    static final GraphValueJsonCodec FLOAT = codec(
            (value, registries) -> new JsonPrimitive(number(value).floatValue()),
            (value, registries) -> value.getAsFloat());
    static final GraphValueJsonCodec BOOLEAN = codec(
            (value, registries) -> new JsonPrimitive((Boolean) value),
            (value, registries) -> value.getAsBoolean());
    static final GraphValueJsonCodec STRING = codec(
            (value, registries) -> new JsonPrimitive(String.valueOf(value)),
            (value, registries) -> value.getAsString());
    static final GraphValueJsonCodec RICH_TEXT = codec(
            (value, registries) -> GSON.toJsonTree(RichTextValue.from(value).toMap()),
            (value, registries) -> RichTextValue.from(GSON.fromJson(value, Object.class)));
    static final GraphValueJsonCodec ENTITY = codec(
            (value, registries) -> encodeEntity(value),
            (value, registries) -> decodeEntity(value.getAsJsonObject()));
    static final GraphValueJsonCodec ENTITY_TEMPLATE = codec(
            (value, registries) -> encodeEntityTemplate(EntityTemplateValue.from(value)),
            (value, registries) -> decodeEntityTemplate(value.getAsJsonObject()));
    static final GraphValueJsonCodec ITEM = codec(
            (value, registries) -> new JsonPrimitive(itemId(value).toString()),
            (value, registries) -> decodeItem(value.getAsString()));
    static final GraphValueJsonCodec ITEM_STACK = codec(
            (value, registries) -> encodeWithRegistries(ItemStack.CODEC, (ItemStack) value, registries),
            (value, registries) -> decodeWithRegistries(ItemStack.CODEC, value, registries));
    static final GraphValueJsonCodec SLOT = codec(
            (value, registries) -> new JsonPrimitive(
                    value instanceof SlotRef slot ? slot.serialize() : String.valueOf(value)),
            (value, registries) -> SlotRef.parse(value.getAsString()));
    static final GraphValueJsonCodec BLOCK_STATE = codec(
            (value, registries) -> encodeWithRegistries(BlockState.CODEC, (BlockState) value, registries),
            (value, registries) -> decodeWithRegistries(BlockState.CODEC, value, registries));
    static final GraphValueJsonCodec GEOMETRY = codec(
            (value, registries) -> encodeGeometry((GeometryValue) value),
            (value, registries) -> decodeGeometry(value.getAsJsonArray()));
    static final GraphValueJsonCodec XYZ = codec(
            (value, registries) -> encodeXyz(value),
            (value, registries) -> decodeXyzVector(value.getAsJsonArray()));
    static final GraphValueJsonCodec COLOR = codec(
            (value, registries) -> GSON.toJsonTree(requireColor(value)),
            (value, registries) -> GSON.fromJson(value, ColorValue.class));
    static final GraphValueJsonCodec LIST = codec(
            (value, registries) -> encodeList((List<?>) value, registries),
            (value, registries) -> decodeList(value.getAsJsonArray(), registries));
    static final GraphValueJsonCodec MAP = codec(
            (value, registries) -> encodeMap((Map<?, ?>) value, registries),
            (value, registries) -> decodeMap(value.getAsJsonObject(), registries));

    private GraphValueJsonCodecs() {
    }

    private static GraphValueJsonCodec codec(JsonEncoder encoder, JsonDecoder decoder) {
        return new GraphValueJsonCodec() {
            @Override
            public JsonElement encode(Object value, HolderLookup.Provider registries) {
                return encoder.encode(value, registries);
            }

            @Override
            public Object decode(JsonElement value, HolderLookup.Provider registries) {
                return decoder.decode(value, registries);
            }
        };
    }

    private static JsonObject encodeEntity(Object value) {
        UUID entityId = value instanceof Entity entity ? entity.getUUID() : (UUID) value;
        JsonObject json = new JsonObject();
        json.addProperty("uuid", entityId.toString());
        return json;
    }

    private static UUID decodeEntity(JsonObject value) {
        return UUID.fromString(value.get("uuid").getAsString());
    }

    private static JsonObject encodeEntityTemplate(EntityTemplateValue value) {
        JsonObject json = new JsonObject();
        json.addProperty("entity_type", value.entityTypeId());
        json.addProperty("entity_data", value.data().toString());
        return json;
    }

    private static EntityTemplateValue decodeEntityTemplate(JsonObject value) {
        try {
            return new EntityTemplateValue(
                    value.get("entity_type").getAsString(),
                    TagParser.parseCompoundFully(value.get("entity_data").getAsString()));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid entity template", exception);
        }
    }

    private static JsonArray encodeXyz(Object value) {
        double x;
        double y;
        double z;
        if (value instanceof Vec3 vec) {
            x = vec.x;
            y = vec.y;
            z = vec.z;
        } else if (value instanceof BlockPos pos) {
            x = pos.getX();
            y = pos.getY();
            z = pos.getZ();
        } else if (value instanceof List<?> values && values.size() >= 3) {
            x = number(values.get(0)).doubleValue();
            y = number(values.get(1)).doubleValue();
            z = number(values.get(2)).doubleValue();
        } else {
            throw new IllegalArgumentException("XYZ value must be Vec3, BlockPos, or a three-number list");
        }
        JsonArray json = new JsonArray();
        json.add(x);
        json.add(y);
        json.add(z);
        return json;
    }

    private static List<Float> decodeXyz(JsonArray value) {
        if (value.size() != 3) throw new IllegalArgumentException("XYZ value must contain exactly three numbers");
        return List.of(value.get(0).getAsFloat(), value.get(1).getAsFloat(), value.get(2).getAsFloat());
    }

    private static JsonArray encodeGeometry(GeometryValue value) {
        JsonArray result = new JsonArray();
        for (GeometryValue.Primitive primitive : value.primitives()) {
            JsonObject json = new JsonObject();
            json.addProperty("type", primitive.type().id());
            json.add("center", vector(primitive.center()));
            switch (primitive.type()) {
                case CUBE -> {
                    json.add("size", vector(primitive.size()));
                    json.addProperty("vertices_x", primitive.verticesX());
                    json.addProperty("vertices_y", primitive.verticesY());
                    json.addProperty("vertices_z", primitive.verticesZ());
                }
                case CYLINDER -> {
                    json.addProperty("radius", primitive.size().x * 0.5D);
                    json.addProperty("depth", primitive.size().y);
                    json.addProperty("radial_vertices", primitive.radialVertices());
                    json.addProperty("side_segments", primitive.sideSegments());
                    json.addProperty("fill_segments", primitive.fillSegments());
                    json.addProperty("fill_type", primitive.fillType().id());
                }
                case UV_SPHERE -> {
                    json.addProperty("radius", primitive.size().x * 0.5D);
                    json.addProperty("segments", primitive.sphereSegments());
                    json.addProperty("rings", primitive.sphereRings());
                }
            }
            result.add(json);
        }
        return result;
    }

    private static GeometryValue decodeGeometry(JsonArray values) {
        List<GeometryValue.Primitive> primitives = new ArrayList<>(values.size());
        for (JsonElement element : values) {
            JsonObject json = element.getAsJsonObject();
            GeometryValue.PrimitiveType type = GeometryValue.PrimitiveType.fromId(json.get("type").getAsString());
            Vec3 center = decodeVector(json.getAsJsonArray("center"));
            GeometryValue.Primitive primitive = switch (type) {
                case CUBE -> GeometryValue.Primitive.cube(
                        center, decodeVector(json.getAsJsonArray("size")),
                        json.get("vertices_x").getAsInt(), json.get("vertices_y").getAsInt(),
                        json.get("vertices_z").getAsInt());
                case CYLINDER -> GeometryValue.Primitive.cylinder(
                        center, json.get("radial_vertices").getAsInt(),
                        json.get("side_segments").getAsInt(), json.get("fill_segments").getAsInt(),
                        json.get("radius").getAsFloat(), json.get("depth").getAsFloat(),
                        GeometryValue.CylinderFillType.fromId(json.get("fill_type").getAsString()));
                case UV_SPHERE -> GeometryValue.Primitive.uvSphere(
                        center, json.get("segments").getAsInt(), json.get("rings").getAsInt(),
                        json.get("radius").getAsFloat());
            };
            primitives.add(primitive);
        }
        return GeometryValue.of(primitives.toArray(GeometryValue.Primitive[]::new));
    }

    private static JsonArray vector(Vec3 value) {
        JsonArray json = new JsonArray();
        json.add(value.x);
        json.add(value.y);
        json.add(value.z);
        return json;
    }

    private static Vec3 decodeVector(JsonArray value) {
        if (value == null || value.size() != 3) throw new IllegalArgumentException("Vector must contain three numbers");
        return new Vec3(value.get(0).getAsDouble(), value.get(1).getAsDouble(), value.get(2).getAsDouble());
    }

    private static JsonArray encodeList(List<?> values, HolderLookup.Provider registries) {
        JsonArray json = new JsonArray();
        for (Object value : values) json.add(encodeDynamic(value, registries));
        return json;
    }

    private static List<Object> decodeList(JsonArray values, HolderLookup.Provider registries) {
        List<Object> result = new ArrayList<>(values.size());
        values.forEach(value -> result.add(decodeDynamic(value, registries)));
        return result;
    }

    private static JsonObject encodeMap(Map<?, ?> values, HolderLookup.Provider registries) {
        JsonObject json = new JsonObject();
        values.forEach((key, value) -> {
            if (!(key instanceof String stringKey)) {
                throw new IllegalArgumentException("Data Library map keys must be strings");
            }
            json.add(stringKey, encodeDynamic(value, registries));
        });
        return json;
    }

    private static Map<String, Object> decodeMap(JsonObject values, HolderLookup.Provider registries) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.entrySet().forEach(entry -> result.put(entry.getKey(), decodeDynamic(entry.getValue(), registries)));
        return result;
    }

    private static JsonElement encodeDynamic(Object value, HolderLookup.Provider registries) {
        if (value == null) return JsonNull.INSTANCE;
        PortType type = PortType.getTypeOf(value);
        if (!GraphValueCodecRegistry.supportsPortType(type)) {
            throw new IllegalArgumentException("Unsupported nested Data Library value: " + value.getClass().getName());
        }
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty(DYNAMIC_TYPE, type.name());
        wrapper.add(DYNAMIC_VALUE, GraphValueCodecRegistry.toJson(type, value, registries));
        return wrapper;
    }

    private static Object decodeDynamic(JsonElement value, HolderLookup.Provider registries) {
        if (value == null || value.isJsonNull()) return null;
        JsonObject wrapper = value.getAsJsonObject();
        PortType type = PortType.valueOf(wrapper.get(DYNAMIC_TYPE).getAsString());
        return GraphValueCodecRegistry.fromJson(type, wrapper.get(DYNAMIC_VALUE), registries);
    }

    private static Vec3 decodeXyzVector(JsonArray value) {
        List<Float> components = decodeXyz(value);
        return new Vec3(components.get(0), components.get(1), components.get(2));
    }

    private static Identifier itemId(Object value) {
        Item item = value instanceof ItemStack stack ? stack.getItem() : (Item) value;
        return BuiltInRegistries.ITEM.getKey(item);
    }

    private static Item decodeItem(String id) {
        Identifier location = Identifier.parse(id);
        return BuiltInRegistries.ITEM.getOptional(location)
                .orElseThrow(() -> new IllegalArgumentException("Unknown item: " + id));
    }

    private static ColorValue requireColor(Object value) {
        ColorValue color = ColorValue.from(value);
        if (color == null) throw new IllegalArgumentException("Invalid color value");
        return color;
    }

    private static Number number(Object value) {
        if (value instanceof Number number) return number;
        throw new IllegalArgumentException("Expected numeric value, got: " + value);
    }

    private static <T> JsonElement encodeWithRegistries(
            com.mojang.serialization.Codec<T> codec, T value, HolderLookup.Provider registries) {
        Objects.requireNonNull(registries, "Registry provider is required for this Data Library type");
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        return codec.encodeStart(ops, value).getOrThrow(IllegalArgumentException::new);
    }

    private static <T> T decodeWithRegistries(
            com.mojang.serialization.Codec<T> codec, JsonElement value, HolderLookup.Provider registries) {
        Objects.requireNonNull(registries, "Registry provider is required for this Data Library type");
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        return codec.parse(ops, value).getOrThrow(IllegalArgumentException::new);
    }

    @FunctionalInterface
    private interface JsonEncoder {
        JsonElement encode(Object value, HolderLookup.Provider registries);
    }

    @FunctionalInterface
    private interface JsonDecoder {
        Object decode(JsonElement value, HolderLookup.Provider registries);
    }
}
