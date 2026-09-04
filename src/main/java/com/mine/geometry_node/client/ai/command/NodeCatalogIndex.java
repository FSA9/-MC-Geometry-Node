package com.mine.geometry_node.client.ai.command;

import com.mine.geometry_node.core.node.NodeCategory;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.nodes.BaseNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Model-facing projection of GeometryNode's authoritative node menu tree. */
public final class NodeCatalogIndex {
    private static final String MENU_PREFIX = "geometry_node.menu.";

    private NodeCatalogIndex() {}

    public record Entry(String path, String typeId, NodeDef definition) {}
    public record Directory(String path, NodeCategory category) {}

    public static String canonicalTypeId(String shortTypeId) {
        if (shortTypeId == null || shortTypeId.isBlank() || shortTypeId.indexOf(':') >= 0) {
            throw new IllegalArgumentException("type_id must be a non-empty GeometryNode short ID without namespace");
        }
        return NodeDef.canonicalTypeId(shortTypeId);
    }

    public static String shortTypeId(String canonicalTypeId) {
        return NodeDef.builtinShortTypeId(canonicalTypeId);
    }

    public static List<Entry> entries() {
        List<Entry> result = new ArrayList<>();
        collectEntries(NodeRegistry.INSTANCE.ROOT, "", result);
        result.sort(Comparator.comparing(Entry::path).thenComparing(Entry::typeId));
        return List.copyOf(result);
    }

    public static List<Directory> directories() {
        List<Directory> result = new ArrayList<>();
        collectDirectories(NodeRegistry.INSTANCE.ROOT, "", result);
        result.sort(Comparator.comparing(Directory::path));
        return List.copyOf(result);
    }

    public static NodeCategory resolveDirectory(String path) {
        String normalized = normalizePath(path);
        NodeCategory current = NodeRegistry.INSTANCE.ROOT;
        if (normalized.isEmpty()) return current;
        for (String segment : normalized.split("/")) {
            NodeCategory next = current.getSubCategories().stream()
                    .filter(category -> segment(category).equals(segment))
                    .findFirst().orElse(null);
            if (next == null) return null;
            current = next;
        }
        return current;
    }

    public static String instructionsCatalog() {
        StringBuilder text = new StringBuilder("\n\nNODE TYPE CATALOG\n")
                .append("- This live NodeRegistry projection is authoritative. Do not invent a type_id that is absent here.\n")
                .append("- All entries are exact short type_ids. Never add geometry_node:; the server adds it internally.\n")
                .append("- If the exact ID is known below, request its details directly. Otherwise browse the directory tree or search short IDs first.\n");
        appendDirectory(text, NodeRegistry.INSTANCE.ROOT, "", 0);
        return text.toString().stripTrailing();
    }

    public static String normalizePath(String path) {
        if (path == null || path.isBlank()) return "";
        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.contains("//") || normalized.equals(".") || normalized.contains("../")
                || normalized.contains("/..") || normalized.contains("./")) {
            throw new IllegalArgumentException("Invalid node catalog path");
        }
        return normalized;
    }

    public static String segment(NodeCategory category) {
        String key = category == null ? "" : category.translationKey;
        return key.startsWith(MENU_PREFIX) ? key.substring(MENU_PREFIX.length()) : key;
    }

    private static void collectEntries(NodeCategory category, String path, List<Entry> result) {
        for (BaseNode node : category.getNodes()) {
            NodeDef definition = node.getDefaultDefinition();
            String shortId = definition == null ? "" : shortTypeId(definition.typeId());
            if (!shortId.isEmpty()) result.add(new Entry(path, shortId, definition));
        }
        for (NodeCategory child : category.getSubCategories()) {
            String childPath = join(path, segment(child));
            collectEntries(child, childPath, result);
        }
    }

    private static void collectDirectories(NodeCategory category, String path, List<Directory> result) {
        result.add(new Directory(path, category));
        for (NodeCategory child : category.getSubCategories()) {
            collectDirectories(child, join(path, segment(child)), result);
        }
    }

    private static void appendDirectory(StringBuilder text, NodeCategory category, String path, int depth) {
        if (!path.isEmpty()) text.append("  ".repeat(depth)).append(path).append("/\n");
        int nodeDepth = path.isEmpty() ? 0 : depth + 1;
        category.getNodes().stream().map(BaseNode::getTypeId).map(NodeCatalogIndex::shortTypeId)
                .filter(id -> !id.isEmpty()).sorted()
                .forEach(id -> text.append("  ".repeat(nodeDepth)).append(id).append('\n'));
        for (NodeCategory child : category.getSubCategories().stream()
                .sorted(Comparator.comparing(NodeCatalogIndex::segment)).toList()) {
            appendDirectory(text, child, join(path, segment(child)), path.isEmpty() ? 0 : depth + 1);
        }
    }

    private static String join(String parent, String child) {
        return parent.isEmpty() ? child : parent + "/" + child;
    }
}
