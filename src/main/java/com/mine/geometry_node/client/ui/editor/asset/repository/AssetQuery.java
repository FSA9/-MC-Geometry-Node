package com.mine.geometry_node.client.ui.editor.asset.repository;

import com.mine.geometry_node.core.metadata.TagPolicy;
import com.mine.geometry_node.core.metadata.TagSet;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public record AssetQuery(String name, String tag, boolean includeTags) {
    public AssetQuery {
        name = name == null ? "" : name.trim();
        tag = tag == null ? "" : tag.trim();
    }

    public boolean hasSearch() {
        return !name.isEmpty() || !tag.isEmpty();
    }

    public String normalizedName() {
        return name.toLowerCase(Locale.ROOT);
    }

    public List<String> tagTerms() {
        if (tag.isBlank()) return List.of();
        return TagSet.normalize(Arrays.asList(tag.split("[,，;；\\s]+")), TagPolicy.GRAPH_LEGACY);
    }
}
