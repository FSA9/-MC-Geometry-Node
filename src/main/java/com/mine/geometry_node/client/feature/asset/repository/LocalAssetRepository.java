package com.mine.geometry_node.client.feature.asset.repository;

import com.mine.geometry_node.core.engine.system.asset.AssetRepositoryFiles;
import com.mine.geometry_node.client.platform.ClientThread;
import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.feature.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.feature.asset.model.AssetType;
import com.mine.geometry_node.client.feature.asset.model.AssetTypeAction;
import com.mine.geometry_node.client.feature.asset.model.AssetTypeRegistry;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class LocalAssetRepository implements AssetRepository {
    @FunctionalInterface
    public interface TagReader {
        List<String> readTags(File file) throws Exception;
    }

    private final TagReader tagReader;

    public LocalAssetRepository() {
        this(file -> AssetRepositoryRegistry.INSTANCE.readLocalTags(file));
    }

    public LocalAssetRepository(TagReader tagReader) {
        this.tagReader = java.util.Objects.requireNonNull(tagReader);
    }

    private static final ExecutorService LOAD_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GeometryNode-LocalAssetRepository");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public AssetSourceKind sourceKind() {
        return AssetSourceKind.LOCAL;
    }

    @Override
    public boolean supports(AssetRepositoryOperation operation) {
        return operation == AssetRepositoryOperation.BROWSE
                || operation == AssetRepositoryOperation.CREATE
                || operation == AssetRepositoryOperation.MANAGE;
    }

    @Override
    public AssetRequest browse(AssetBrowseRequest request, Consumer<AssetListing> onResult) {
        if (!(request.location() instanceof AssetLocation.Local location)) {
            throw new IllegalArgumentException("local repository requires a local location");
        }
        Consumer<AssetListing> callback = onResult != null ? onResult : ignored -> {};
        AtomicBoolean cancelled = new AtomicBoolean();
        Future<?> future = LOAD_EXECUTOR.submit(() -> {
            AssetListing result;
            try {
                result = load(location, request.query());
            } catch (Exception e) {
                e.printStackTrace();
                result = AssetListing.failure(location);
            }
            if (!Thread.currentThread().isInterrupted()) {
                AssetListing completed = result;
                ClientThread.execute(() -> {
                    if (!cancelled.get()) callback.accept(completed);
                });
            }
        });
        return () -> {
            cancelled.set(true);
            future.cancel(true);
        };
    }

    private AssetListing load(AssetLocation.Local location, AssetQuery query) {
        if (Thread.currentThread().isInterrupted()) return AssetListing.empty(location);
        File directory = location.directory();
        if (!location.favorites() && directory == null) return AssetListing.empty(location);

        List<File> files = new ArrayList<>();
        Map<String, List<String>> tagCache = new HashMap<>();
        List<String> tagTerms = query.tagTerms();
        String nameQuery = query.normalizedName();
        if (location.favorites()) {
            for (String path : location.favoritePaths()) {
                if (Thread.currentThread().isInterrupted()) return AssetListing.empty(location);
                File file = new File(path);
                AssetType type = resolveType(file);
                if (!isRegularType(file, type) || !type.supports(AssetTypeAction.FAVORITE)) continue;
                if (matchesSearch(file, type, nameQuery, tagTerms, tagCache)) files.add(file);
            }
        } else if (!query.hasSearch()) {
            File[] listed = directory.listFiles();
            if (listed != null) {
                for (File file : listed) if (isDisplayable(file)) files.add(file);
            }
        } else {
            collectSearchMatches(directory, nameQuery, tagTerms, tagCache, files);
        }

        if (Thread.currentThread().isInterrupted()) return AssetListing.empty(location);
        sortFiles(files, directory, location.favorites());
        return toListing(files, location, query.includeTags(), tagCache);
    }

    private void collectSearchMatches(File directory, String nameQuery, List<String> tagTerms,
                                      Map<String, List<String>> tagCache, List<File> out) {
        File[] files = directory != null ? directory.listFiles() : null;
        if (files == null) return;
        for (File file : files) {
            if (Thread.currentThread().isInterrupted()) return;
            try {
                if (Files.isSymbolicLink(file.toPath()) || AssetRepositoryFiles.isInternal(file.toPath())) continue;
            } catch (Exception ignored) {
                continue;
            }
            AssetType type = resolveType(file);
            if (type.displayInBrowser() && matchesSearch(file, type, nameQuery, tagTerms, tagCache)) out.add(file);
            if (file.isDirectory()) collectSearchMatches(file, nameQuery, tagTerms, tagCache, out);
        }
    }

    private boolean matchesSearch(File file, AssetType type, String nameQuery, List<String> tagTerms,
                                  Map<String, List<String>> tagCache) {
        if (!nameQuery.isEmpty() && !file.getName().toLowerCase(java.util.Locale.ROOT).contains(nameQuery)) {
            return false;
        }
        if (tagTerms.isEmpty()) return true;
        if (!AssetTypeRegistry.GRAPH_ID.equals(type.id())) return false;
        List<String> tags = readTags(file, tagCache);
        for (String term : tagTerms) {
            boolean matched = false;
            for (String tag : tags) {
                if (tag.contains(term)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return true;
    }

    private AssetListing toListing(List<File> files, AssetLocation.Local location, boolean includeTags,
                                   Map<String, List<String>> tagCache) {
        List<AssetEntry> entries = new ArrayList<>(files.size());
        Map<String, List<String>> tagsByKey = includeTags ? new HashMap<>() : Collections.emptyMap();
        for (File file : files) {
            AssetEntry entry = AssetEntry.local(file, pathKey(file),
                    relativeLabel(file, location.directory(), location.favorites()), includeTags);
            entries.add(entry);
            if (!AssetTypeRegistry.GRAPH_ID.equals(entry.type().id())) continue;
            if (includeTags) tagsByKey.put(entry.key(), readTags(file, tagCache));
        }
        return new AssetListing(true, location, entries, tagsByKey);
    }

    private static AssetType resolveType(File file) {
        return AssetTypeRegistry.INSTANCE.resolveLocal(file);
    }

    private static boolean isRegularType(File file, AssetType type) {
        return file != null && file.isFile() && type != null && type.displayInBrowser();
    }

    private static boolean isDisplayable(File file) {
        if (file == null || Files.isSymbolicLink(file.toPath())
                || AssetRepositoryFiles.isInternal(file.toPath())
                || !(file.isDirectory() || file.isFile())) return false;
        AssetType type = resolveType(file);
        return type != null && type.displayInBrowser();
    }

    private List<String> readTags(File file, Map<String, List<String>> cache) {
        String key = pathKey(file);
        if (cache.containsKey(key)) return cache.get(key);
        List<String> tags;
        try {
            tags = List.copyOf(tagReader.readTags(file));
        } catch (Exception ignored) {
            tags = List.of();
        }
        cache.put(key, tags);
        return tags;
    }

    static void sortFiles(List<File> files, File baseDirectory, boolean favorites) {
        // Filesystem metadata and display paths are stable inputs for this listing. Read each
        // once, rather than doing disk stat calls and path allocation inside every comparison.
        List<SortEntry> entries = new ArrayList<>(files.size());
        for (File file : files) {
            entries.add(new SortEntry(file, file.isDirectory(), relativeLabel(file, baseDirectory, favorites)));
        }
        entries.sort((first, second) -> {
            if (first.directory() != second.directory()) return first.directory() ? -1 : 1;
            return first.label().compareToIgnoreCase(second.label());
        });
        for (int i = 0; i < entries.size(); i++) files.set(i, entries.get(i).file());
    }

    private record SortEntry(File file, boolean directory, String label) {}

    public static String pathKey(File file) {
        try {
            return file.getCanonicalPath();
        } catch (Exception ignored) {
            return file.getAbsolutePath();
        }
    }

    private static String relativeLabel(File file, File baseDirectory, boolean favorites) {
        if (favorites) return file.getAbsolutePath();
        if (baseDirectory == null) return file.getName();
        try {
            return baseDirectory.toPath().relativize(file.toPath()).toString();
        } catch (Exception ignored) {
            return file.getName();
        }
    }
}
