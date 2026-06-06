package com.mine.geometry_node.client.ui.bottom_window.asset_library.right;

import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetEntry;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.tags.GraphTagIO;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class AssetEntryLoader {
    record Query(String name, String tag) {
        Query {
            name = name == null ? "" : name.trim();
            tag = tag == null ? "" : tag.trim();
        }

        boolean hasSearch() {
            return !name.isEmpty() || !tag.isEmpty();
        }

        String normalizedName() {
            return name.toLowerCase(Locale.ROOT);
        }

        List<String> tagTerms() {
            if (tag.isBlank()) {
                return List.of();
            }

            List<String> terms = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (String part : tag.split("[,，;；\\s]+")) {
                String normalized = GraphTagIO.normalizeTag(part);
                if (!normalized.isEmpty() && seen.add(normalized)) {
                    terms.add(normalized);
                }
            }
            return terms;
        }
    }

    static final class Result {
        private static final Result EMPTY = new Result(List.of(), Map.of());

        private final List<AssetEntry> mEntries;
        private final Map<String, List<String>> mTagsByKey;

        Result(List<AssetEntry> entries, Map<String, List<String>> tagsByKey) {
            mEntries = entries == null ? List.of() : entries;
            mTagsByKey = tagsByKey == null ? Map.of() : tagsByKey;
        }

        static Result empty() {
            return EMPTY;
        }

        static Result entriesOnly(List<AssetEntry> entries) {
            return new Result(entries, Map.of());
        }

        List<AssetEntry> entries() {
            return mEntries;
        }

        List<String> tagsFor(AssetEntry entry) {
            if (entry == null) return List.of();
            return mTagsByKey.getOrDefault(entry.key(), List.of());
        }
    }

    Result loadCurrentDirectory(File directory, Query query, boolean includeTags) {
        if (directory == null) {
            return Result.empty();
        }
        if (Thread.currentThread().isInterrupted()) {
            return Result.empty();
        }

        List<File> files = new ArrayList<>();
        Map<String, List<String>> tagCache = new HashMap<>();
        Query safeQuery = query == null ? new Query("", "") : query;
        List<String> tagTerms = safeQuery.tagTerms();
        String nameQuery = safeQuery.normalizedName();

        if (!safeQuery.hasSearch()) {
            File[] listed = directory.listFiles();
            if (listed != null) {
                for (File file : listed) {
                    if (isDisplayable(file)) files.add(file);
                }
            }
        } else {
            collectSearchMatches(directory, nameQuery, tagTerms, tagCache, files);
        }

        if (Thread.currentThread().isInterrupted()) {
            return Result.empty();
        }
        sortFiles(files, directory, false);
        return toResult(files, directory, false, includeTags, tagCache);
    }

    Result loadFavorites(List<String> favoritePaths, Query query, boolean includeTags) {
        if (Thread.currentThread().isInterrupted()) {
            return Result.empty();
        }

        List<File> files = new ArrayList<>();
        Map<String, List<String>> tagCache = new HashMap<>();
        Query safeQuery = query == null ? new Query("", "") : query;
        List<String> tagTerms = safeQuery.tagTerms();
        String nameQuery = safeQuery.normalizedName();

        if (favoritePaths != null) {
            for (String path : favoritePaths) {
                if (Thread.currentThread().isInterrupted()) {
                    return Result.empty();
                }
                File file = new File(path);
                if (!isLocalGraphFile(file)) continue;
                if (!matchesSearch(file, nameQuery, tagTerms, tagCache)) continue;
                files.add(file);
            }
        }

        sortFiles(files, null, true);
        return toResult(files, null, true, includeTags, tagCache);
    }

    AssetEntry toLocalEntry(File file, File baseDirectory, boolean favoritesMode) {
        return AssetEntry.local(file, pathKey(file), relativeLabel(file, baseDirectory, favoritesMode));
    }

    private void collectSearchMatches(File directory, String nameQuery, List<String> tagTerms, Map<String, List<String>> tagCache, List<File> out) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            try {
                if (Files.isSymbolicLink(file.toPath())) continue;
            } catch (Exception ignored) {
                continue;
            }

            boolean displayable = isDisplayable(file);
            if (displayable && matchesSearch(file, nameQuery, tagTerms, tagCache)) {
                out.add(file);
            }
            if (file.isDirectory()) {
                collectSearchMatches(file, nameQuery, tagTerms, tagCache, out);
            }
        }
    }

    private boolean matchesSearch(File file, String nameQuery, List<String> tagTerms, Map<String, List<String>> tagCache) {
        if (!nameQuery.isEmpty() && !file.getName().toLowerCase(Locale.ROOT).contains(nameQuery)) {
            return false;
        }
        if (tagTerms.isEmpty()) {
            return true;
        }
        if (!isLocalGraphFile(file)) {
            return false;
        }

        List<String> tags = readTags(file, tagCache);
        for (String term : tagTerms) {
            boolean matched = false;
            for (String tag : tags) {
                if (tag.contains(term)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private Result toResult(List<File> files, File baseDirectory, boolean favoritesMode, boolean includeTags, Map<String, List<String>> tagCache) {
        List<AssetEntry> entries = new ArrayList<>(files.size());
        Map<String, List<String>> tagsByKey = includeTags ? new HashMap<>() : Collections.emptyMap();
        for (File file : files) {
            AssetEntry entry = toLocalEntry(file, baseDirectory, favoritesMode);
            entries.add(entry);
            if (includeTags && entry.sourceKind() == AssetSourceKind.LOCAL && isLocalGraphFile(file)) {
                tagsByKey.put(entry.key(), readTags(file, tagCache));
            }
        }
        return new Result(entries, tagsByKey);
    }

    private List<String> readTags(File file, Map<String, List<String>> tagCache) {
        String key = pathKey(file);
        if (tagCache.containsKey(key)) {
            return tagCache.get(key);
        }

        List<String> tags;
        try {
            tags = List.copyOf(GraphTagIO.readTags(file));
        } catch (Exception ignored) {
            tags = List.of();
        }
        tagCache.put(key, tags);
        return tags;
    }

    private boolean isDisplayable(File file) {
        return file != null && (file.isDirectory() || file.getName().toLowerCase(Locale.ROOT).endsWith(".json"));
    }

    private void sortFiles(List<File> files, File baseDirectory, boolean favoritesMode) {
        files.sort((f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            return relativeLabel(f1, baseDirectory, favoritesMode).compareToIgnoreCase(relativeLabel(f2, baseDirectory, favoritesMode));
        });
    }

    static boolean isLocalGraphFile(File file) {
        return file != null
                && file.isFile()
                && file.getName().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    static String pathKey(File file) {
        try {
            return file.getCanonicalPath();
        } catch (Exception ignored) {
            return file.getAbsolutePath();
        }
    }

    private String relativeLabel(File file, File baseDirectory, boolean favoritesMode) {
        if (favoritesMode) return file.getAbsolutePath();
        if (baseDirectory == null) return file.getName();
        try {
            return baseDirectory.toPath().relativize(file.toPath()).toString();
        } catch (Exception ignored) {
            return file.getName();
        }
    }
}
