package com.mine.geometry_node.client.feature.asset.query;

import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.feature.asset.repository.AssetListing;
import com.mine.geometry_node.core.utils.ColumnFilters;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/** In-memory table criteria. Filtering/sorting never rereads a file or sends a browse request. */
public record AssetListQuery(String name, String description, ColumnFilters.Choice types,
                             Set<String> includedTags, Set<String> excludedTags,
                             ColumnFilters.Choice favorites, Column sort, boolean descending) {
    public enum Column { TYPE, NAME, DESCRIPTION, TAGS, FAVORITE }
    public AssetListQuery {
        name = name == null ? "" : name.trim();
        description = description == null ? "" : description.trim();
        includedTags = Set.copyOf(includedTags);
        excludedTags = Set.copyOf(excludedTags);
        java.util.Objects.requireNonNull(types);
        java.util.Objects.requireNonNull(favorites);
        java.util.Objects.requireNonNull(sort);
    }
    public static AssetListQuery empty() {
        return new AssetListQuery("", "", ColumnFilters.Choice.all(), Set.of(), Set.of(),
                ColumnFilters.Choice.all(), Column.NAME, false);
    }
    public static String typeKey(AssetEntry entry) {
        return entry.metadata().variantId().isEmpty() ? entry.type().id()
                : entry.type().id() + ":" + entry.metadata().variantId();
    }
    public boolean matches(AssetEntry entry, AssetListing listing, boolean favorite) {
        List<String> tags = listing.tagsFor(entry);
        return contains(entry.name(), name) && contains(entry.descriptor().documentInfo().description(), description)
                && types.matches(typeKey(entry)) && favorites.matches(Boolean.toString(favorite))
                && tags.containsAll(includedTags) && Collections.disjoint(tags, excludedTags);
    }
    public Comparator<AssetEntry> comparator(AssetListing listing, Predicate<AssetEntry> favorite) {
        Comparator<AssetEntry> values = switch (sort) {
            case TYPE -> Comparator.comparing(AssetListQuery::typeKey, String.CASE_INSENSITIVE_ORDER);
            case NAME -> Comparator.comparing(AssetEntry::name, String.CASE_INSENSITIVE_ORDER);
            case DESCRIPTION -> Comparator.comparing(e -> e.descriptor().documentInfo().description(), String.CASE_INSENSITIVE_ORDER);
            case TAGS -> Comparator.comparing(e -> String.join("\u0000", listing.tagsFor(e)), String.CASE_INSENSITIVE_ORDER);
            case FAVORITE -> Comparator.comparing(e -> favorite.test(e));
        };
        if (descending) values = values.reversed();
        // Reverse the selected column only; folders always precede files, including descending sorts.
        return Comparator.comparing(AssetEntry::isDirectory).reversed().thenComparing(values)
                .thenComparing(AssetEntry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(AssetEntry::key);
    }
    private static boolean contains(String text, String query) {
        return query.isEmpty() || text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
    public boolean active() {
        return !name.isEmpty() || !description.isEmpty() || types.active() || !includedTags.isEmpty()
                || !excludedTags.isEmpty() || favorites.active();
    }
    public AssetListQuery withoutFilters() { return new AssetListQuery("", "", ColumnFilters.Choice.all(), Set.of(), Set.of(), ColumnFilters.Choice.all(), sort, descending); }
    public AssetListQuery withName(String value) { return new AssetListQuery(value, description, types, includedTags, excludedTags, favorites, sort, descending); }
    public AssetListQuery withDescription(String value) { return new AssetListQuery(name, value, types, includedTags, excludedTags, favorites, sort, descending); }
    public AssetListQuery withTypes(ColumnFilters.Choice value) { return new AssetListQuery(name, description, value, includedTags, excludedTags, favorites, sort, descending); }
    public AssetListQuery withTags(Set<String> include, Set<String> exclude) { return new AssetListQuery(name, description, types, include, exclude, favorites, sort, descending); }
    public AssetListQuery withFavorites(ColumnFilters.Choice value) { return new AssetListQuery(name, description, types, includedTags, excludedTags, value, sort, descending); }
    public AssetListQuery sortBy(Column column) { return new AssetListQuery(name, description, types, includedTags, excludedTags, favorites, column, sort == column && !descending); }
}
