package com.mine.geometry_node.core.engine.system.scene;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** World save owns authored objects; Minecraft's SavedData storage handles atomic file replacement. */
public final class SceneSavedData extends SavedData {
    public static final int FORMAT_VERSION = 4;
    public static final SavedDataType<SceneSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("geometry_node", "editor_scene"), SceneSavedData::new,
            CompoundTag.CODEC.xmap(SceneSavedData::load, SceneSavedData::save));
    private final Map<String, SceneDocument> dimensions = new LinkedHashMap<>();
    public static SceneSavedData get(MinecraftServer server) { return server.getDataStorage().computeIfAbsent(TYPE); }
    public synchronized SceneDocument document(String dimension) {
        Identifier.parse(dimension);
        return dimensions.computeIfAbsent(dimension, ignored -> new SceneDocument());
    }
    /** Server-thread metadata access across persisted dimensions, including dimensions without a live level. */
    public synchronized List<String> worldQueryDimensions() {
        return dimensions.keySet().stream().limit(257).toList();
    }
    public synchronized void visitWorldQuery(java.util.function.BiPredicate<String, SceneObject> visitor) {
        for (var dimension : dimensions.entrySet())
            for (SceneObject object : dimension.getValue().objects())
                if (!visitor.test(dimension.getKey(), object)) return;
    }
    public synchronized boolean apply(String dimension, List<SceneChange> changes) {
        boolean changed = document(dimension).apply(changes);
        if (changed) setDirty();
        return changed;
    }
    public synchronized CompoundTag save() {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", FORMAT_VERSION);
        ListTag list = new ListTag();
        dimensions.forEach((dimension, document) -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", dimension);
            tag.putLong("Revision", document.revision());
            ListTag objects = new ListTag();
            for (SceneObject object : document.objects()) objects.add(writeObject(object));
            tag.put("Objects", objects);
            list.add(tag);
        });
        root.put("Dimensions", list);
        return root;
    }
    public static SceneSavedData load(CompoundTag root) {
        int version = root.getIntOr("Version", -1);
        if (version != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported scene save version");
        SceneSavedData result = new SceneSavedData();
        ListTag list = required(root.getList("Dimensions"), "Dimensions");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompoundOrEmpty(i);
            String dimension = Identifier.parse(required(tag.getString("Dimension"), "Dimension")).toString();
            List<SceneObject> objects = new ArrayList<>();
            ListTag values = required(tag.getList("Objects"), "Objects");
            for (int j = 0; j < values.size(); j++) objects.add(readObject(values.getCompoundOrEmpty(j)));
            if (result.dimensions.putIfAbsent(dimension, new SceneDocument(required(tag.getLong("Revision"), "Revision"), objects)) != null)
                throw new IllegalArgumentException("Duplicate scene dimension");
        }
        return result;
    }
    private static CompoundTag writeObject(SceneObject object) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", object.id().toString()); tag.putString("Type", object.typeId()); tag.putString("Name", object.name());
        tag.putDouble("X", object.position().x); tag.putDouble("Y", object.position().y); tag.putDouble("Z", object.position().z);
        tag.putDouble("R", object.color().x); tag.putDouble("G", object.color().y); tag.putDouble("B", object.color().z);
        CompoundTag parameters=new CompoundTag();object.parameters().forEach(parameters::putString);tag.put("Parameters",parameters);
        tag.putDouble("RX",object.rotation().x);tag.putDouble("RY",object.rotation().y);tag.putDouble("RZ",object.rotation().z);
        tag.putDouble("SX",object.scale().x);tag.putDouble("SY",object.scale().y);tag.putDouble("SZ",object.scale().z);
        tag.putBoolean("Enabled", object.enabled()); tag.putLong("Revision", object.revision());
        return tag;
    }
    private static SceneObject readObject(CompoundTag tag) {
        java.util.Map<String,String> parameters=new java.util.LinkedHashMap<>();
        CompoundTag values=required(tag.getCompound("Parameters"), "Parameters");
        for(String key:values.keySet())parameters.put(key,required(values.getString(key), key));
        return new SceneObject(UUID.fromString(required(tag.getString("Id"), "Id")), required(tag.getString("Type"), "Type"),
                required(tag.getString("Name"), "Name"), new Vec3(required(tag.getDouble("X"), "X"), required(tag.getDouble("Y"), "Y"), required(tag.getDouble("Z"), "Z")),
                new Vec3(required(tag.getDouble("R"), "R"), required(tag.getDouble("G"), "G"), required(tag.getDouble("B"), "B")),
                required(tag.getBoolean("Enabled"), "Enabled"), required(tag.getLong("Revision"), "Revision"), parameters,
                new Vec3(required(tag.getDouble("RX"), "RX"),required(tag.getDouble("RY"), "RY"),required(tag.getDouble("RZ"), "RZ")),
                new Vec3(required(tag.getDouble("SX"), "SX"),required(tag.getDouble("SY"), "SY"),required(tag.getDouble("SZ"), "SZ")));
    }
    private static <T> T required(Optional<T> value, String field) {
        return value.orElseThrow(() -> new IllegalArgumentException("Missing or invalid scene field: " + field));
    }
}
