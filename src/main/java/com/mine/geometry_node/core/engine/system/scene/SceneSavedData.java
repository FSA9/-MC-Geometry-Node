package com.mine.geometry_node.core.engine.system.scene;

import com.mojang.serialization.Codec;
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
    public static final SavedDataType<SceneSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("geometry_node", "editor_scene"), SceneSavedData::new,
            CompoundTag.CODEC.xmap(SceneSavedData::load, SceneSavedData::save));
    private final Map<String, SceneDocument> dimensions = new LinkedHashMap<>();
    public static SceneSavedData get(MinecraftServer server) { return server.getDataStorage().computeIfAbsent(TYPE); }
    public synchronized SceneDocument document(String dimension) {
        Identifier.parse(dimension);
        return dimensions.computeIfAbsent(dimension, ignored -> new SceneDocument());
    }
    public synchronized boolean apply(String dimension, List<SceneChange> changes) {
        boolean changed = document(dimension).apply(changes);
        if (changed) setDirty();
        return changed;
    }
    public synchronized CompoundTag save() {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 1);
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
        if (root.getIntOr("Version", 1) != 1) throw new IllegalArgumentException("Unsupported scene save version");
        SceneSavedData result = new SceneSavedData();
        ListTag list = root.getListOrEmpty("Dimensions");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompoundOrEmpty(i);
            String dimension = Identifier.parse(tag.getStringOr("Dimension", "")).toString();
            List<SceneObject> objects = new ArrayList<>();
            ListTag values = tag.getListOrEmpty("Objects");
            for (int j = 0; j < values.size(); j++) objects.add(readObject(values.getCompoundOrEmpty(j)));
            if (result.dimensions.putIfAbsent(dimension, new SceneDocument(tag.getLongOr("Revision", 0), objects)) != null)
                throw new IllegalArgumentException("Duplicate scene dimension");
        }
        return result;
    }
    private static CompoundTag writeObject(SceneObject object) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", object.id().toString()); tag.putString("Type", object.typeId()); tag.putString("Name", object.name());
        tag.putDouble("X", object.position().x); tag.putDouble("Y", object.position().y); tag.putDouble("Z", object.position().z);
        tag.putDouble("R", object.color().x); tag.putDouble("G", object.color().y); tag.putDouble("B", object.color().z);
        tag.putFloat("Intensity", object.intensity()); tag.putFloat("Range", object.range());
        tag.putBoolean("Enabled", object.enabled()); tag.putLong("Revision", object.revision());
        return tag;
    }
    private static SceneObject readObject(CompoundTag tag) {
        return new SceneObject(UUID.fromString(tag.getStringOr("Id", "")), tag.getStringOr("Type", ""),
                tag.getStringOr("Name", ""), new Vec3(tag.getDoubleOr("X", 0), tag.getDoubleOr("Y", 0), tag.getDoubleOr("Z", 0)),
                new Vec3(tag.getDoubleOr("R", 1), tag.getDoubleOr("G", 1), tag.getDoubleOr("B", 1)),
                tag.getFloatOr("Intensity", 3), tag.getFloatOr("Range", 16), tag.getBooleanOr("Enabled", true),
                tag.getLongOr("Revision", 0));
    }
}
