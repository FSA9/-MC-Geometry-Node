package com.mine.geometry_node.core.network.packet.worldquery;

import com.mine.geometry_node.core.engine.system.worldquery.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.*;
import java.util.*;

final class WorldObjectPacketCodecs {
    private WorldObjectPacketCodecs() {}
    static int count(RegistryFriendlyByteBuf b, int max) {
        int n = b.readVarInt();
        if (n < 0 || n > max) throw new IllegalArgumentException("World query collection limit exceeded");
        return n;
    }
    static void strings(RegistryFriendlyByteBuf b, Collection<String> values, int textLimit) {
        b.writeVarInt(values.size()); for (String value : values) b.writeUtf(value, textLimit);
    }
    static List<String> strings(RegistryFriendlyByteBuf b, int max, int textLimit) {
        int n = count(b, max); List<String> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) result.add(b.readUtf(textLimit));
        return result;
    }
    static void query(RegistryFriendlyByteBuf b, WorldObjectQuery q) {
        b.writeVarInt(q.range().ordinal()); b.writeUtf(q.dimension(), WorldObjectQuery.MAX_FILTER_TEXT);
        for (Set<String> values : List.of(q.providers(), q.types())) strings(b, values, WorldObjectQuery.MAX_FILTER_TEXT);
        b.writeUtf(q.name(), WorldObjectQuery.MAX_FILTER_TEXT);
        for (Set<String> values : List.of(q.tags(), q.sources(), q.lifetimes())) strings(b, values, WorldObjectQuery.MAX_FILTER_TEXT);
        b.writeDouble(q.radius()); b.writeVarInt(q.limit());
    }
    static WorldObjectQuery query(RegistryFriendlyByteBuf b) {
        int range = count(b, WorldObjectQuery.Range.values().length - 1);
        return new WorldObjectQuery(WorldObjectQuery.Range.values()[range], b.readUtf(WorldObjectQuery.MAX_FILTER_TEXT),
                filters(b), filters(b), b.readUtf(WorldObjectQuery.MAX_FILTER_TEXT), filters(b), filters(b), filters(b),
                b.readDouble(), b.readVarInt());
    }
    private static Set<String> filters(RegistryFriendlyByteBuf b) {
        return Set.copyOf(strings(b, WorldObjectQuery.MAX_FILTERS, WorldObjectQuery.MAX_FILTER_TEXT));
    }
    static void vec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    static Vec3 vec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    static void entry(RegistryFriendlyByteBuf b, WorldObjectEntry e) {
        for (String value : List.of(e.key(), e.provider(), e.type(), e.name(), e.dimension())) b.writeUtf(value, WorldObjectEntry.MAX_TEXT);
        vec(b, e.position());
        var a = e.bounds(); vec(b, new Vec3(a.minX, a.minY, a.minZ)); vec(b, new Vec3(a.maxX, a.maxY, a.maxZ));
        strings(b, e.tags(), WorldObjectEntry.MAX_TEXT);
        b.writeUtf(e.source(), WorldObjectEntry.MAX_TEXT); b.writeUtf(e.lifetime(), WorldObjectEntry.MAX_TEXT);
        b.writeBoolean(e.sceneObjectId() != null); if (e.sceneObjectId() != null) b.writeUUID(e.sceneObjectId());
        b.writeBoolean(e.entityId() != null); if (e.entityId() != null) b.writeInt(e.entityId());
        b.writeVarInt(e.references().size());
        e.references().forEach((k, v) -> { b.writeUtf(k, WorldObjectEntry.MAX_TEXT); b.writeUtf(v, WorldObjectEntry.MAX_TEXT); });
    }
    static WorldObjectEntry entry(RegistryFriendlyByteBuf b) {
        String key = b.readUtf(WorldObjectEntry.MAX_TEXT), provider = b.readUtf(WorldObjectEntry.MAX_TEXT),
                type = b.readUtf(WorldObjectEntry.MAX_TEXT), name = b.readUtf(WorldObjectEntry.MAX_TEXT),
                dimension = b.readUtf(WorldObjectEntry.MAX_TEXT);
        Vec3 pos = vec(b); AABB bounds = new AABB(vec(b), vec(b));
        List<String> tags = strings(b, WorldObjectEntry.MAX_TAGS, WorldObjectEntry.MAX_TEXT);
        String source = b.readUtf(WorldObjectEntry.MAX_TEXT), lifetime = b.readUtf(WorldObjectEntry.MAX_TEXT);
        UUID scene = b.readBoolean() ? b.readUUID() : null;
        Integer entity = b.readBoolean() ? b.readInt() : null;
        int count = count(b, WorldObjectEntry.MAX_REFERENCES); Map<String, String> refs = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            if (refs.putIfAbsent(b.readUtf(WorldObjectEntry.MAX_TEXT), b.readUtf(WorldObjectEntry.MAX_TEXT)) != null)
                throw new IllegalArgumentException("Duplicate reference metadata");
        }
        return new WorldObjectEntry(key, provider, type, name, dimension, pos, bounds, tags, source, lifetime, scene, entity, refs);
    }
    static void page(RegistryFriendlyByteBuf b, WorldObjectPage p) {
        b.writeLong(p.requestId()); b.writeBoolean(p.truncated()); b.writeUtf(p.message(), WorldObjectEntry.MAX_TEXT);
        strings(b, p.dimensionIds(), WorldObjectEntry.MAX_TEXT); strings(b, p.coverage(), WorldObjectEntry.MAX_TEXT);
        b.writeVarInt(p.types().size());
        for (var t : p.types()) { b.writeUtf(t.provider(), WorldObjectEntry.MAX_TEXT); b.writeUtf(t.id(), WorldObjectEntry.MAX_TEXT); b.writeUtf(t.labelKey(), WorldObjectEntry.MAX_TEXT); }
        b.writeVarInt(p.entries().size()); for (var entry : p.entries()) entry(b, entry);
    }
    static WorldObjectPage page(RegistryFriendlyByteBuf b) {
        long requestId = b.readLong(); boolean truncated = b.readBoolean(); String message = b.readUtf(WorldObjectEntry.MAX_TEXT);
        var dims = strings(b, WorldObjectPage.MAX_METADATA, WorldObjectEntry.MAX_TEXT);
        var coverage = strings(b, WorldObjectPage.MAX_METADATA, WorldObjectEntry.MAX_TEXT);
        int n = count(b, WorldObjectPage.MAX_METADATA); List<WorldObjectType> types = new ArrayList<>(n);
        for (int i = 0; i < n; i++) types.add(new WorldObjectType(b.readUtf(WorldObjectEntry.MAX_TEXT), b.readUtf(WorldObjectEntry.MAX_TEXT), b.readUtf(WorldObjectEntry.MAX_TEXT)));
        n = count(b, WorldObjectQuery.MAX_RESULTS); List<WorldObjectEntry> entries = new ArrayList<>(n);
        long bytes = 0;
        for (int i = 0; i < n; i++) {
            var entry = entry(b); bytes += entry.estimatedBytes();
            if (bytes > WorldObjectPage.MAX_ENTRY_BYTES) throw new IllegalArgumentException("World query byte limit exceeded");
            entries.add(entry);
        }
        return new WorldObjectPage(requestId, entries, truncated, dims, types, coverage, message);
    }
}
