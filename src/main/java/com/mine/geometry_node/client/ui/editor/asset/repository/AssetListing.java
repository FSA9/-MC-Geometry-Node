package com.mine.geometry_node.client.ui.editor.asset.repository;

import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;

import java.util.List;
import java.util.Map;

public record AssetListing(boolean success, AssetLocation location, List<AssetEntry> entries,
                           Map<String, List<String>> tagsByKey) {
    public AssetListing {
        entries = entries == null ? List.of() : List.copyOf(entries);
        tagsByKey = tagsByKey == null ? Map.of() : Map.copyOf(tagsByKey);
    }

    public static AssetListing empty(AssetLocation location) {
        return new AssetListing(true, location, List.of(), Map.of());
    }

    public static AssetListing failure(AssetLocation location) {
        return new AssetListing(false, location, List.of(), Map.of());
    }

    public List<String> tagsFor(AssetEntry entry) {
        return entry == null ? List.of() : tagsByKey.getOrDefault(entry.key(), List.of());
    }

    public String graphTypeIdFor(AssetEntry entry) {
        return entry == null ? "" : entry.metadata().variantId();
    }
}
