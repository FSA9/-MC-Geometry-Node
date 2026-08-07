package com.mine.geometry_node.client.ui.editor.asset.repository;

import com.mine.geometry_node.client.ui.persistence.GraphTagIO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        List<String> terms = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String part : tag.split("[,，;；\\s]+")) {
            String normalized = GraphTagIO.normalizeTag(part);
            if (!normalized.isEmpty() && seen.add(normalized)) terms.add(normalized);
        }
        return terms;
    }
}
