package com.mine.geometry_node.core.network.packet.worldquery;

import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectGraph;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectGraphFilter;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectPage;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectQuery;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.*;
import java.util.*;
import com.mine.geometry_node.core.query.ColumnFilters;

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
        b.writeDouble(q.radius()); b.writeVarInt(q.limit()); columns(b, q.columns());
        strings(b, q.searchFallbackTypes(), ColumnFilters.MAX_TEXT);
        strings(b, q.columnNameFallbackTypes(), ColumnFilters.MAX_TEXT);
        b.writeUtf(q.registryType(), WorldObjectQuery.MAX_FILTER_TEXT);
        b.writeUtf(q.graphFilter().type(), WorldObjectQuery.MAX_FILTER_TEXT);
        b.writeUtf(q.graphFilter().name(), WorldObjectQuery.MAX_FILTER_TEXT);
    }
    static WorldObjectQuery query(RegistryFriendlyByteBuf b) {
        DecodeBudget budget = new DecodeBudget();
        int range = count(b, WorldObjectQuery.Range.values().length - 1);
        String dimension = budget.text(b, WorldObjectQuery.MAX_FILTER_TEXT, TextScope.LEGACY);
        Set<String> providers = filters(b, budget), types = filters(b, budget);
        String name = budget.text(b, WorldObjectQuery.MAX_FILTER_TEXT, TextScope.LEGACY);
        Set<String> tags = filters(b, budget), sources = filters(b, budget), lifetimes = filters(b, budget);
        double radius = b.readDouble(); int limit = b.readVarInt();
        ColumnFilters columns = columns(b, budget);
        Set<String> searchFallbackTypes = budget.set(b, ColumnFilters.MAX_CHOICES, ColumnFilters.MAX_TEXT, TextScope.FALLBACK);
        Set<String> columnNameFallbackTypes = budget.set(b, ColumnFilters.MAX_CHOICES, ColumnFilters.MAX_TEXT, TextScope.FALLBACK);
        String registryType = budget.text(b, WorldObjectQuery.MAX_FILTER_TEXT, TextScope.COLUMN);
        var graphFilter = new WorldObjectGraphFilter(
                budget.text(b, WorldObjectQuery.MAX_FILTER_TEXT, TextScope.GRAPH),
                budget.text(b, WorldObjectQuery.MAX_FILTER_TEXT, TextScope.GRAPH));
        return new WorldObjectQuery(WorldObjectQuery.Range.values()[range], dimension, providers, types, name,
                tags, sources, lifetimes, radius, limit, columns, searchFallbackTypes, columnNameFallbackTypes, registryType, graphFilter);
    }
    private static Set<String> filters(RegistryFriendlyByteBuf b, DecodeBudget budget) {
        return budget.set(b, WorldObjectQuery.MAX_FILTERS, WorldObjectQuery.MAX_FILTER_TEXT, TextScope.LEGACY);
    }
    private enum TextScope { LEGACY, COLUMN, FALLBACK, GRAPH }
    /** Reject each text before allocating its UTF-8 array/string or inserting into any collection. */
    private static final class DecodeBudget {
        int totalText, legacyText, columnText, wireBytes = 256;
        String text(RegistryFriendlyByteBuf b, int max, TextScope scope) {
            int start = b.readerIndex();
            int bytes = b.readVarInt();
            if (bytes < 0 || bytes > max * 3 || bytes > WorldObjectQuery.MAX_REQUEST_BYTES - wireBytes - 5
                    || bytes > b.readableBytes())
                throw new IllegalArgumentException("World query wire text budget exceeded");
            // UTF-8 has at most three bytes per UTF-16 code unit. Reject certain overflow before decoding.
            int remaining = Math.min(WorldObjectQuery.MAX_TOTAL_TEXT - totalText,
                    scope == TextScope.COLUMN ? ColumnFilters.MAX_TOTAL_TEXT - columnText
                            : scope == TextScope.LEGACY ? 4096 - legacyText : WorldObjectQuery.MAX_TOTAL_TEXT - totalText);
            if (bytes > remaining * 3) throw new IllegalArgumentException("World query text budget exceeded");
            b.readerIndex(start);
            String text = b.readUtf(Math.min(max, remaining));
            wireBytes += 5 + bytes;
            totalText += text.length();
            if (scope == TextScope.COLUMN) columnText += text.length();
            else if (scope == TextScope.LEGACY) legacyText += text.length();
            return text;
        }
        Set<String> set(RegistryFriendlyByteBuf b, int maxCount, int maxText, TextScope scope) {
            int n = count(b, maxCount);
            Set<String> result = new LinkedHashSet<>();
            for (int i = 0; i < n; i++) {
                String value = text(b, maxText, scope);
                if (!result.add(value)) throw new IllegalArgumentException("Duplicate world query filter");
            }
            return Set.copyOf(result);
        }
    }
    static void columns(RegistryFriendlyByteBuf b, ColumnFilters c) {
        b.writeUtf(c.name(), ColumnFilters.MAX_TEXT);
        choice(b, c.types()); number(b, c.value()); choice(b, c.dimensions());
        number(b, c.x()); number(b, c.y()); number(b, c.z()); choice(b, c.lifetimes());
        strings(b, c.includedTags(), ColumnFilters.MAX_TEXT); strings(b, c.excludedTags(), ColumnFilters.MAX_TEXT);
    }
    static ColumnFilters columns(RegistryFriendlyByteBuf b) { return columns(b, new DecodeBudget()); }
    private static ColumnFilters columns(RegistryFriendlyByteBuf b, DecodeBudget budget) {
        return new ColumnFilters(budget.text(b, ColumnFilters.MAX_TEXT, TextScope.COLUMN), choice(b, budget), number(b), choice(b, budget),
                number(b), number(b), number(b), choice(b, budget), columnSet(b, budget), columnSet(b, budget));
    }
    private static Set<String> columnSet(RegistryFriendlyByteBuf b, DecodeBudget budget) {
        return budget.set(b, ColumnFilters.MAX_CHOICES, ColumnFilters.MAX_TEXT, TextScope.COLUMN);
    }
    private static void choice(RegistryFriendlyByteBuf b, ColumnFilters.Choice c) {
        b.writeBoolean(c.restricted()); strings(b, c.selected(), ColumnFilters.MAX_TEXT);
    }
    private static ColumnFilters.Choice choice(RegistryFriendlyByteBuf b, DecodeBudget budget) {
        return new ColumnFilters.Choice(b.readBoolean(), columnSet(b, budget));
    }
    private static void number(RegistryFriendlyByteBuf b, ColumnFilters.NumberRange n) {
        b.writeBoolean(n.minimum() != null); if (n.minimum() != null) b.writeDouble(n.minimum());
        b.writeBoolean(n.maximum() != null); if (n.maximum() != null) b.writeDouble(n.maximum());
    }
    private static ColumnFilters.NumberRange number(RegistryFriendlyByteBuf b) {
        return new ColumnFilters.NumberRange(b.readBoolean() ? b.readDouble() : null, b.readBoolean() ? b.readDouble() : null);
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
        b.writeVarInt(e.graphs().size());
        for (var graph : e.graphs()) {
            b.writeUtf(graph.id(), WorldObjectEntry.MAX_TEXT);
            b.writeUtf(graph.type(), WorldObjectEntry.MAX_TEXT);
            b.writeUtf(graph.name(), WorldObjectEntry.MAX_TEXT);
        }
    }
    static WorldObjectEntry entry(RegistryFriendlyByteBuf b) {
        return entry(b, WorldObjectPage.MAX_ENTRY_BYTES);
    }
    private static WorldObjectEntry entry(RegistryFriendlyByteBuf b, int remainingBytes) {
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
        var base = new WorldObjectEntry(key, provider, type, name, dimension, pos, bounds, tags, source, lifetime, scene, entity, refs);
        var budget = new GraphMetadataBudget(remainingBytes - base.estimatedBytes());
        int graphCount = count(b, WorldObjectEntry.MAX_GRAPHS);
        List<WorldObjectGraph> graphs = new ArrayList<>(graphCount);
        for (int i = 0; i < graphCount; i++)
            graphs.add(new WorldObjectGraph(budget.text(b), budget.text(b), budget.text(b)));
        return new WorldObjectEntry(key, provider, type, name, dimension, pos, bounds, tags, source, lifetime, scene, entity, refs, graphs);
    }
    /** Apply the remaining page budget before allocating each graph text body. */
    private static final class GraphMetadataBudget {
        int remaining;
        GraphMetadataBudget(int remaining) {
            if (remaining < 0) throw new IllegalArgumentException("World query byte limit exceeded");
            this.remaining = remaining;
        }
        String text(RegistryFriendlyByteBuf b) {
            int start = b.readerIndex(), bytes = b.readVarInt();
            if (bytes < 0 || bytes > WorldObjectEntry.MAX_TEXT * 3 || bytes > remaining - 5 || bytes > b.readableBytes())
                throw new IllegalArgumentException("World query graph byte limit exceeded");
            remaining -= bytes + 5;
            b.readerIndex(start);
            return b.readUtf(WorldObjectEntry.MAX_TEXT);
        }
    }
    static void page(RegistryFriendlyByteBuf b, WorldObjectPage p) {
        b.writeLong(p.requestId()); b.writeBoolean(p.truncated()); b.writeUtf(p.message(), WorldObjectEntry.MAX_TEXT);
        strings(b, p.dimensionIds(), WorldObjectEntry.MAX_TEXT); strings(b, p.coverage(), WorldObjectEntry.MAX_TEXT);
        b.writeVarInt(p.types().size());
        for (var t : p.types()) { b.writeUtf(t.provider(), WorldObjectEntry.MAX_TEXT); b.writeUtf(t.id(), WorldObjectEntry.MAX_TEXT); b.writeUtf(t.labelKey(), WorldObjectEntry.MAX_TEXT); }
        b.writeVarInt(p.entries().size()); for (var entry : p.entries()) entry(b, entry);
        b.writeBoolean(p.counts() != null);
        if (p.counts() != null) {
            b.writeBoolean(p.counts().partial());
            counts(b, p.counts().categories()); counts(b, p.counts().otherProviders());
        }
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
            var entry = entry(b, WorldObjectPage.MAX_ENTRY_BYTES - (int) bytes); bytes += entry.estimatedBytes();
            if (bytes > WorldObjectPage.MAX_ENTRY_BYTES) throw new IllegalArgumentException("World query byte limit exceeded");
            entries.add(entry);
        }
        WorldObjectPage.Counts counts = null;
        if (b.readBoolean()) {
            boolean partial = b.readBoolean();
            counts = new WorldObjectPage.Counts(counts(b), counts(b), partial);
        }
        return new WorldObjectPage(requestId, entries, truncated, dims, types, coverage, message, counts);
    }
    private static void counts(RegistryFriendlyByteBuf b, Map<String, Long> values) {
        b.writeVarInt(values.size());
        values.forEach((id, value) -> { b.writeUtf(id, WorldObjectEntry.MAX_TEXT); b.writeVarLong(value); });
    }
    private static Map<String, Long> counts(RegistryFriendlyByteBuf b) {
        int size = count(b, WorldObjectPage.MAX_METADATA);
        Map<String, Long> values = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String id = b.readUtf(WorldObjectEntry.MAX_TEXT);
            long value = b.readVarLong();
            if (value < 0 || values.putIfAbsent(id, value) != null)
                throw new IllegalArgumentException("Invalid category count");
        }
        return values;
    }
}
