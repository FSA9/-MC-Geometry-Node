package com.mine.geometry_node.core.node.value.light;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonDeserializationContext;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchorCodec;
import java.lang.reflect.Type;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import com.mine.geometry_node.core.engine.graph.value.GraphValueCodec;
import com.mine.geometry_node.core.engine.graph.value.GraphValueJsonCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Shared persistence representation for graph defaults and saved values. */
public final class LightConfigCodec implements GraphValueCodec<LightConfig>, GraphValueJsonCodec {
    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(Vec3.class,
            (JsonDeserializer<Vec3>) (json, type, context) -> {
                if (json.isJsonArray()) {
                    var a = json.getAsJsonArray();
                    if (a.size() != 3) throw new JsonParseException("Light vector requires three coordinates");
                    return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
                }
                var o = json.getAsJsonObject();
                return new Vec3(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble());
            }).registerTypeAdapter(SpatialAnchor.class, new AnchorAdapter()).create();

    private static final class AnchorAdapter implements JsonSerializer<SpatialAnchor>, JsonDeserializer<SpatialAnchor> {
        private final SpatialAnchorCodec codec = new SpatialAnchorCodec();
        @Override public JsonElement serialize(SpatialAnchor value, Type type, JsonSerializationContext context) {
            return codec.encode(value, null);
        }
        @Override public SpatialAnchor deserialize(JsonElement value, Type type, JsonDeserializationContext context) {
            return codec.decode(value, null);
        }
    }
    public static LightConfig fromMap(Map<?, ?> value) {
        try {
            return new LightConfigCodec().decode(GSON.toJsonTree(value), null);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid light configuration", invalid);
        }
    }
    public String getTypeId() { return "light_config"; }
    public Class<LightConfig> getTargetClass() { return LightConfig.class; }
    public Tag serialize(LightConfig value, HolderLookup.Provider provider) {
        return StringTag.valueOf(encode(value, provider).toString());
    }
    public LightConfig deserialize(Tag tag, HolderLookup.Provider provider) {
        return decode(JsonParser.parseString(tag.asString().orElseThrow()), provider);
    }
    public JsonElement encode(Object value, HolderLookup.Provider provider) {
        return GSON.toJsonTree((LightConfig) value);
    }
    public LightConfig decode(JsonElement value, HolderLookup.Provider provider) {
        if (value.isJsonObject() && value.getAsJsonObject().has("quality")
                && value.getAsJsonObject().get("quality").isJsonObject()) {
            value = value.deepCopy();
            var quality = value.getAsJsonObject().getAsJsonObject("quality");
            if (!quality.has("volumeEmitterSamples")) {
                quality.addProperty("volumeEmitterSamples", quality.has("shadowSamples")
                        ? Math.max(4, quality.get("shadowSamples").getAsInt()) : 4);
            }
        }
        LightConfig result = GSON.fromJson(value, LightConfig.class);
        if (result == null) throw new IllegalArgumentException("Null light config");
        return result;
    }
}
