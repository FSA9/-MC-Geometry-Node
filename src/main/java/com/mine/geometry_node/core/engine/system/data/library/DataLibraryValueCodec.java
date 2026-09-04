package com.mine.geometry_node.core.engine.system.data.library;

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
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;

/** JSON representation of values stored inside a Data Library entry. */
public final class DataLibraryValueCodec {
    private static final Gson GSON = new Gson();
    private static final String DYNAMIC_TYPE = "type";
    private static final String DYNAMIC_VALUE = "value";

    private DataLibraryValueCodec() {
    }

    public static JsonElement encode(PortType type, Object value, HolderLookup.Provider registries) {
        if (!DataLibraryTypes.supports(type)) {
            throw new IllegalArgumentException("Unsupported Data Library type: " + type);
        }
        if (value == null) return JsonNull.INSTANCE;
        return switch (type) {
            case INTEGER -> new JsonPrimitive(number(value).intValue());
            case LONG -> new JsonPrimitive(number(value).longValue());
            case FLOAT -> new JsonPrimitive(number(value).floatValue());
            case BOOLEAN -> new JsonPrimitive((Boolean) value);
            case STRING, PATH -> new JsonPrimitive(String.valueOf(value));
            case RICH_TEXT -> GSON.toJsonTree(RichTextValue.from(value).toMap());
            case ENTITY -> encodeEntity(value);
            case ENTITY_TEMPLATE -> encodeEntityTemplate(EntityTemplateValue.from(value));
            case ITEM -> new JsonPrimitive(itemId(value).toString());
            case ITEM_STACK -> encodeWithRegistries(ItemStack.CODEC, (ItemStack) value, registries);
            case SLOT -> new JsonPrimitive(value instanceof SlotRef slot ? slot.serialize() : String.valueOf(value));
            case BLOCK -> encodeWithRegistries(BlockState.CODEC, (BlockState) value, registries);
            case GEOMETRY -> encodeGeometry((GeometryValue) value);
            case XYZ -> encodeXyz(value);
            case COLOR -> GSON.toJsonTree(requireColor(value));
            case LIST -> encodeList((List<?>) value, registries);
            case DICT, SHOP -> encodeMap((Map<?, ?>) value, registries);
            default -> throw new IllegalArgumentException("Unsupported Data Library type: " + type);
        };
    }

    public static Object decode(PortType type, JsonElement value, HolderLookup.Provider registries) {
        if (!DataLibraryTypes.supports(type)) {
            throw new IllegalArgumentException("Unsupported Data Library type: " + type);
        }
        if (value == null || value.isJsonNull()) return null;
        return switch (type) {
            case INTEGER -> value.getAsInt();
            case LONG -> value.getAsLong();
            case FLOAT -> value.getAsFloat();
            case BOOLEAN -> value.getAsBoolean();
            case STRING, PATH -> value.getAsString();
            case RICH_TEXT -> RichTextValue.from(GSON.fromJson(value, Object.class));
            case ENTITY -> decodeEntity(value.getAsJsonObject());
            case ENTITY_TEMPLATE -> decodeEntityTemplate(value.getAsJsonObject());
            case ITEM -> decodeItem(value.getAsString());
            case ITEM_STACK -> decodeWithRegistries(ItemStack.CODEC, value, registries);
            case SLOT -> SlotRef.parse(value.getAsString());
            case BLOCK -> decodeWithRegistries(BlockState.CODEC, value, registries);
            case GEOMETRY -> decodeGeometry(value.getAsJsonArray());
            case XYZ -> decodeXyzVector(value.getAsJsonArray());
            case COLOR -> GSON.fromJson(value, ColorValue.class);
            case LIST -> decodeList(value.getAsJsonArray(), registries);
            case DICT, SHOP -> decodeMap(value.getAsJsonObject(), registries);
            default -> throw new IllegalArgumentException("Unsupported Data Library type: " + type);
        };
    }

    private static JsonObject encodeEntity(Object value) {
        DataLibraryEntityReference reference = value instanceof Entity entity
                ? DataLibraryEntityReference.capture(entity)
                : (DataLibraryEntityReference) value;
        JsonObject json = new JsonObject();
        json.addProperty("dimension", reference.dimension().toString());
        json.addProperty("uuid", reference.entityId().toString());
        return json;
    }

    private static DataLibraryEntityReference decodeEntity(JsonObject value) {
        return new DataLibraryEntityReference(
                Identifier.parse(value.get("dimension").getAsString()),
                UUID.fromString(value.get("uuid").getAsString()));
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
        } else if (value instanceof List<?> values && values.size() >= 3) {
            x = number(values.get(0)).doubleValue();
            y = number(values.get(1)).doubleValue();
            z = number(values.get(2)).doubleValue();
        } else {
            throw new IllegalArgumentException("XYZ value must be Vec3 or a three-number list");
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
        PortType type = value instanceof DataLibraryEntityReference ? PortType.ENTITY : PortType.getTypeOf(value);
        if (!DataLibraryTypes.supports(type)) {
            throw new IllegalArgumentException("Unsupported nested Data Library value: " + value.getClass().getName());
        }
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty(DYNAMIC_TYPE, type.name());
        wrapper.add(DYNAMIC_VALUE, encode(type, value, registries));
        return wrapper;
    }

    private static Object decodeDynamic(JsonElement value, HolderLookup.Provider registries) {
        if (value == null || value.isJsonNull()) return null;
        JsonObject wrapper = value.getAsJsonObject();
        PortType type = PortType.valueOf(wrapper.get(DYNAMIC_TYPE).getAsString());
        if (type == PortType.XYZ) return decodeXyzVector(wrapper.getAsJsonArray(DYNAMIC_VALUE));
        return decode(type, wrapper.get(DYNAMIC_VALUE), registries);
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
}
