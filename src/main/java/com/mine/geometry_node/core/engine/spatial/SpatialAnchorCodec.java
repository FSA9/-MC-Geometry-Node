package com.mine.geometry_node.core.engine.spatial;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mine.geometry_node.core.engine.graph.value.GraphValueCodec;
import com.mine.geometry_node.core.engine.graph.value.GraphValueJsonCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Locale;
import java.util.UUID;

/** Versioned, registry-independent wire and graph persistence representation. */
public final class SpatialAnchorCodec implements GraphValueCodec<SpatialAnchor>, GraphValueJsonCodec {
    private static final int VERSION = 2;
    private static final Gson GSON = new Gson();

    /** Restores graph document defaults, which are read as generic JSON maps. */
    public static SpatialAnchor fromMap(Map<?, ?> value) {
        return new SpatialAnchorCodec().decode(GSON.toJsonTree(value), null);
    }

    @Override public String getTypeId() { return "spatial_anchor"; }
    @Override public Class<SpatialAnchor> getTargetClass() { return SpatialAnchor.class; }

    @Override
    public CompoundTag serialize(SpatialAnchor value) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", VERSION);
        tag.putString("kind", value.isEntity() ? "entity" : "world");
        if (value.dimension() != null) tag.putString("dimension", value.dimension().identifier().toString());
        if (value.isEntity()) {
            tag.putString("entity_id", value.entityId().toString());
            tag.putString("origin", value.origin().name().toLowerCase(Locale.ROOT));
            tag.putBoolean("follow_position", value.followPosition());
            tag.putBoolean("follow_orientation", value.followOrientation());
            tag.putString("orientation_source", value.orientationSource().name().toLowerCase(Locale.ROOT));
        }
        ListTag position = new ListTag();
        position.add(DoubleTag.valueOf(value.position().x));
        position.add(DoubleTag.valueOf(value.position().y));
        position.add(DoubleTag.valueOf(value.position().z));
        tag.put("position", position);
        return tag;
    }

    @Override
    public SpatialAnchor deserialize(Tag value) {
        if (!(value instanceof CompoundTag tag)) throw invalid("Expected a compound");
        if (!(tag.get("version") instanceof IntTag version) || version.intValue() != VERSION) {
            throw invalid("Unsupported version");
        }
        ResourceKey<Level> dimension = tag.contains("dimension")
                ? dimension(requireString(tag, "dimension")) : null;
        return switch (requireString(tag, "kind")) {
            case "world" -> {
                yield SpatialAnchor.world(dimension, position(tag));
            }
            case "entity" -> new SpatialAnchor(dimension,
                    UUID.fromString(requireString(tag, "entity_id")), position(tag), origin(requireString(tag, "origin")),
                    requireBoolean(tag, "follow_position"), requireBoolean(tag, "follow_orientation"),
                    source(requireString(tag, "orientation_source")));
            default -> throw invalid("Unknown kind");
        };
    }

    @Override
    public JsonObject encode(Object rawValue, HolderLookup.Provider registries) {
        SpatialAnchor value = (SpatialAnchor) rawValue;
        JsonObject json = new JsonObject();
        json.addProperty("version", VERSION);
        json.addProperty("kind", value.isEntity() ? "entity" : "world");
        if (value.dimension() != null) json.addProperty("dimension", value.dimension().identifier().toString());
        if (value.isEntity()) {
            json.addProperty("entity_id", value.entityId().toString());
            json.addProperty("origin", value.origin().name().toLowerCase(Locale.ROOT));
            json.addProperty("follow_position", value.followPosition());
            json.addProperty("follow_orientation", value.followOrientation());
            json.addProperty("orientation_source", value.orientationSource().name().toLowerCase(Locale.ROOT));
        }
        JsonArray position = new JsonArray();
        position.add(value.position().x);
        position.add(value.position().y);
        position.add(value.position().z);
        json.add("position", position);
        return json;
    }

    @Override
    public SpatialAnchor decode(JsonElement value, HolderLookup.Provider registries) {
        if (value == null || !value.isJsonObject()) throw invalid("Expected an object");
        JsonObject json = value.getAsJsonObject();
        JsonElement version = json.get("version");
        if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()
                || version.getAsDouble() != VERSION) throw invalid("Unsupported version");
        ResourceKey<Level> dimension = json.has("dimension")
                ? dimension(requireString(json, "dimension")) : null;
        return switch (requireString(json, "kind")) {
            case "world" -> {
                yield SpatialAnchor.world(dimension, position(json));
            }
            case "entity" -> new SpatialAnchor(dimension,
                    UUID.fromString(requireString(json, "entity_id")), position(json), origin(requireString(json, "origin")),
                    requireBoolean(json, "follow_position"), requireBoolean(json, "follow_orientation"),
                    source(requireString(json, "orientation_source")));
            default -> throw invalid("Unknown kind");
        };
    }

    private static Vec3 position(CompoundTag tag) {
        if (!(tag.get("position") instanceof ListTag position) || position.size() != 3) {
            throw invalid("Position requires three numbers");
        }
        double[] coordinates = new double[3];
        for (int i = 0; i < 3; i++) {
            if (!(position.get(i) instanceof NumericTag)) throw invalid("Invalid position number");
            coordinates[i] = position.getDoubleOr(i, Double.NaN);
        }
        return new Vec3(coordinates[0], coordinates[1], coordinates[2]);
    }

    private static Vec3 position(JsonObject json) {
        JsonElement raw = json.get("position");
        if (raw == null || !raw.isJsonArray() || raw.getAsJsonArray().size() != 3) {
            throw invalid("Position requires three numbers");
        }
        JsonArray position = raw.getAsJsonArray();
        double[] coordinates = new double[3];
        for (int i = 0; i < 3; i++) {
            JsonElement coordinate = position.get(i);
            if (!coordinate.isJsonPrimitive() || !coordinate.getAsJsonPrimitive().isNumber()) {
                throw invalid("Invalid position number");
            }
            coordinates[i] = coordinate.getAsDouble();
        }
        return new Vec3(coordinates[0], coordinates[1], coordinates[2]);
    }

    private static boolean requireBoolean(CompoundTag tag, String name) {
        if (tag.get(name) instanceof ByteTag value && (value.byteValue() == 0 || value.byteValue() == 1)) {
            return value.byteValue() != 0;
        }
        throw invalid("Expected boolean: " + name);
    }

    private static boolean requireBoolean(JsonObject json, String name) {
        JsonElement value = json.get(name);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        throw invalid("Expected boolean: " + name);
    }

    private static SpatialAnchor.OrientationSource source(String value) {
        return switch (value) {
            case "body" -> SpatialAnchor.OrientationSource.BODY;
            case "view" -> SpatialAnchor.OrientationSource.VIEW;
            default -> throw invalid("Unknown orientation source");
        };
    }

    private static String requireString(CompoundTag value, String name) {
        if (value.get(name) instanceof StringTag string) return string.value();
        throw invalid("Expected string: " + name);
    }

    private static String requireString(JsonObject value, String name) {
        JsonElement field = value.get(name);
        if (field != null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isString()) return field.getAsString();
        throw invalid("Expected string: " + name);
    }

    private static ResourceKey<Level> dimension(String value) {
        Identifier id = Identifier.tryParse(value);
        if (id == null) throw invalid("Invalid dimension");
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    private static SpatialAnchor.Origin origin(String value) {
        return switch (value) {
            case "base" -> SpatialAnchor.Origin.BASE;
            case "eye" -> SpatialAnchor.Origin.EYE;
            default -> throw invalid("Unknown entity origin");
        };
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid spatial anchor: " + message);
    }
}
