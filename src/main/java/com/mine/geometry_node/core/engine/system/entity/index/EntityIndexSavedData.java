package com.mine.geometry_node.core.engine.system.entity.index;

import com.mine.geometry_node.core.engine.metadata.OwnerMetadata;
import com.mine.geometry_node.core.engine.system.faction.model.FactionRules;
import com.mine.geometry_node.core.engine.system.entity.preview.appearance.EntityAppearanceSanitizer;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectGraph;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import java.nio.file.*;
import java.util.*;

/** One server-wide file. Only explicitly registered records are serialized. */
public final class EntityIndexSavedData extends SavedData {
    public static final int FORMAT_VERSION = 1;
    public static final SavedDataType<EntityIndexSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("geometry_node", "entity_index"), EntityIndexSavedData::new,
            CompoundTag.CODEC.xmap(EntityIndexSavedData::load, EntityIndexSavedData::save));
    private final EntityIndexCatalog catalog;
    private long writeRevision;
    public EntityIndexSavedData() { catalog = new EntityIndexCatalog(); }
    private EntityIndexSavedData(EntityIndexCatalog catalog) { this.catalog = catalog; }
    EntityIndexCatalog catalog() { return catalog; }
    long writeRevision() { return writeRevision; }
    void changed() { writeRevision = Math.incrementExact(writeRevision); setDirty(); }

    public static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATA).resolve("geometry_node/entity_index.dat");
    }
    public static EntityIndexSavedData get(MinecraftServer server) {
        Path file = file(server);
        validateExisting(file);
        // SavedDataStorage swallows decode/IO failures. Do not computeIfAbsent over a damaged file.
        EntityIndexSavedData current = server.getDataStorage().get(TYPE);
        if (current != null) return current;
        if (!Files.notExists(file)) throw new IllegalStateException("storage_unavailable");
        EntityIndexSavedData created = new EntityIndexSavedData();
        server.getDataStorage().set(TYPE, created);
        // Merely listing/looking up entities does not author a persistent directory file.
        created.setDirty(false);
        return created;
    }
    static void validateExisting(Path file) {
        if (Files.notExists(file)) return;
        // Bound decompression before vanilla's unlimited-heap loader. Never replace a broken file.
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.create(128L * 1024 * 1024));
            load(root.getCompound("data").orElseThrow());
        } catch (java.io.IOException | RuntimeException failure) {
            throw new IllegalStateException("storage_unavailable", failure);
        }
    }
    static void verifyWritten(Path file, long revision) throws java.io.IOException {
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.create(128L * 1024 * 1024));
        CompoundTag data = root.getCompound("data").orElseThrow(() -> new java.io.IOException("Missing saved entity directory"));
        if (data.getIntOr("Version", -1) != FORMAT_VERSION || data.getLongOr("WriteRevision", -1) < revision)
            throw new java.io.IOException("Entity directory write did not reach disk");
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", FORMAT_VERSION);
        root.putLong("Sequence", catalog.sequence());
        root.putLong("WriteRevision", writeRevision);
        ListTag records = new ListTag();
        catalog.visit(record -> { records.add(write(record)); return true; });
        root.put("Records", records);
        return root;
    }
    public static EntityIndexSavedData load(CompoundTag root) {
        if (root.getIntOr("Version", -1) != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported entity index version");
        long sequence = root.getLong("Sequence").orElseThrow();
        ListTag records = root.getList("Records").orElseThrow();
        if (records.size() > EntityIndexCatalog.MAX_RECORDS) throw new IllegalArgumentException("Too many registered entities");
        List<EntityIndexRecord> values = new ArrayList<>(records.size());
        for (Tag raw : records) {
            if (!(raw instanceof CompoundTag tag)) throw new IllegalArgumentException("Invalid entity index record");
            values.add(read(tag));
        }
        EntityIndexSavedData result = new EntityIndexSavedData(new EntityIndexCatalog(sequence, values));
        result.writeRevision = root.getLong("WriteRevision").orElseThrow();
        if (result.writeRevision < 0) throw new IllegalArgumentException("Invalid entity index write revision");
        return result;
    }
    private static CompoundTag write(EntityIndexRecord record) {
        CompoundTag out = new CompoundTag(); WorldObjectEntry entry = record.entry();
        out.putString("UUID", record.uuid().toString()); out.putLong("Revision", record.revision());
        out.putLong("ObservedAt", record.observedAtMillis()); out.putBoolean("Alive", record.alive());
        out.putString("Dimension", entry.dimension()); out.putString("Type", entry.type()); out.putString("Name", entry.name());
        out.putString("Source", entry.source());
        out.putDouble("X", entry.position().x); out.putDouble("Y", entry.position().y); out.putDouble("Z", entry.position().z);
        AABB box = entry.bounds();
        out.putDouble("MinX", box.minX); out.putDouble("MinY", box.minY); out.putDouble("MinZ", box.minZ);
        out.putDouble("MaxX", box.maxX); out.putDouble("MaxY", box.maxY); out.putDouble("MaxZ", box.maxZ);
        CompoundTag references = new CompoundTag(); entry.references().forEach(references::putString); out.put("References", references);
        ListTag graphs = new ListTag();
        for (WorldObjectGraph graph : entry.graphs()) {
            CompoundTag value = new CompoundTag(); value.putString("Id", graph.id());
            value.putString("Type", graph.type()); value.putString("Name", graph.name()); graphs.add(value);
        }
        out.put("Graphs", graphs); out.put("Metadata", record.metadata().save());
        ListTag factions = new ListTag();
        record.factions().stream().sorted().forEach(id -> factions.add(StringTag.valueOf(id.toString())));
        out.put("Factions", factions);
        if (!record.appearance().isEmpty()) out.put("Appearance", record.appearance().data());
        return out;
    }
    private static EntityIndexRecord read(CompoundTag in) {
        UUID id = UUID.fromString(text(in, "UUID"));
        String dimension = Identifier.parse(text(in, "Dimension")).toString();
        String type = Identifier.parse(text(in, "Type")).toString();
        OwnerMetadata metadata = OwnerMetadata.load(in.getCompound("Metadata").orElseThrow());
        Map<String, String> references = new LinkedHashMap<>();
        CompoundTag rawReferences = in.getCompound("References").orElseThrow();
        if (rawReferences.size() > WorldObjectEntry.MAX_REFERENCES) throw new IllegalArgumentException("Too many entity references");
        for (String key : rawReferences.keySet()) {
            if (!Set.of("entityUuid", "itemId", "previewStatus").contains(key))
                throw new IllegalArgumentException("Unsupported persistent entity reference");
            references.put(key, text(rawReferences, key));
        }
        ListTag rawGraphs = in.getList("Graphs").orElseThrow();
        if (rawGraphs.size() > WorldObjectEntry.MAX_GRAPHS) throw new IllegalArgumentException("Too many bound graphs");
        List<WorldObjectGraph> graphs = new ArrayList<>(rawGraphs.size());
        for (Tag raw : rawGraphs) {
            if (!(raw instanceof CompoundTag graph)) throw new IllegalArgumentException("Invalid bound graph");
            graphs.add(new WorldObjectGraph(text(graph, "Id"), text(graph, "Type"), text(graph, "Name")));
        }
        Vec3 position = new Vec3(number(in, "X"), number(in, "Y"), number(in, "Z"));
        double minX = number(in, "MinX"), minY = number(in, "MinY"), minZ = number(in, "MinZ");
        double maxX = number(in, "MaxX"), maxY = number(in, "MaxY"), maxZ = number(in, "MaxZ");
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Inverted entity bounds");
        WorldObjectEntry entry = new WorldObjectEntry(WorldObjectEntry.key("native", "", id.toString()),
                "native", type, text(in, "Name"), dimension, position, new AABB(minX, minY, minZ, maxX, maxY, maxZ),
                metadata.tags(), text(in, "Source"), "unloaded", null, null, references, graphs);
        EntityTemplateValue appearance = in.getCompound("Appearance")
                .map(tag -> new EntityTemplateValue(type, EntityAppearanceSanitizer.sanitize(tag))).orElse(EntityTemplateValue.EMPTY);
        // Version 1 predates membership snapshots. Missing means unknown/empty until next observation.
        Set<UUID> factions = new LinkedHashSet<>();
        if (in.contains("Factions")) {
            ListTag rawFactions = in.getList("Factions").orElseThrow();
            if (rawFactions.size() > FactionRules.MAX_ENTITY_REFERENCES)
                throw new IllegalArgumentException("Too many entity faction memberships");
            for (Tag raw : rawFactions) {
                if (!(raw instanceof StringTag value) || !factions.add(UUID.fromString(value.asString().orElseThrow())))
                    throw new IllegalArgumentException("Invalid or duplicate entity faction membership");
            }
        }
        return new EntityIndexRecord(id, in.getLong("Revision").orElseThrow(), in.getLong("ObservedAt").orElseThrow(),
                in.getBoolean("Alive").orElseThrow(), entry, appearance, metadata, factions);
    }
    private static String text(CompoundTag tag, String key) { return tag.getString(key).orElseThrow(); }
    private static double number(CompoundTag tag, String key) {
        double value = tag.getDouble(key).orElseThrow();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Invalid entity coordinate");
        return value;
    }
}
