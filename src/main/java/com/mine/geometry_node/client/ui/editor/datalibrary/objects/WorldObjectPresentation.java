package com.mine.geometry_node.client.ui.editor.datalibrary.objects;

import com.mine.geometry_node.core.engine.system.worldquery.metadata.WorldObjectDataEdits;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import com.mine.geometry_node.core.engine.system.faction.model.FactionCatalog;

import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;

/** Presentation helpers for world-object table cells; owns no query or View state. */
final class WorldObjectPresentation {
    private WorldObjectPresentation() {}

    static List<String> graphNames(WorldObjectEntry entry) {
        return entry.graphs().stream().map(graph -> graph.name().isEmpty() ? graph.id() : graph.name()).toList();
    }
    static List<String> factionNames(WorldObjectEntry entry, FactionCatalog catalog, String missingLabel) {
        if (entry.entityState() == null) return List.of();
        return entry.entityState().factions().stream()
                .map(id -> catalog.find(id).map(value -> value.name().plain()).orElse(missingLabel))
                .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()))
                .toList();
    }
    static boolean hasEntityPreview(WorldObjectEntry entry) {
        return entry.provider().equals("native") && !entry.type().equals("minecraft:interaction");
    }
    static UUID entityId(WorldObjectEntry entry) {
        return entry.entityState() != null ? entry.entityState().entityUuid()
                : UUID.fromString(entry.references().getOrDefault("entityUuid", ""));
    }
    static boolean isLoaded(WorldObjectEntry entry) {
        return entry.entityState() == null || entry.entityState().loaded();
    }
    static boolean isAlive(WorldObjectEntry entry) {
        return entry.entityState() == null || entry.entityState().alive();
    }
    static String capturedAt(WorldObjectEntry entry) {
        if (entry.entityState() == null || entry.entityState().capturedAtMillis() <= 0) return "—";
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(entry.entityState().capturedAtMillis()));
    }
    static int dataCount(WorldObjectEntry entry) {
        try { return Math.max(0, Integer.parseInt(entry.references().getOrDefault(WorldObjectDataEdits.COUNT_REFERENCE, "0"))); }
        catch (NumberFormatException invalid) { return 0; }
    }
    static String position(WorldObjectEntry entry) {
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", entry.position().x, entry.position().y, entry.position().z);
    }
    static boolean isNativeEntity(WorldObjectEntry entry) {
        if (!entry.provider().equals("native")) return false;
        try { entityId(entry); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }
}
