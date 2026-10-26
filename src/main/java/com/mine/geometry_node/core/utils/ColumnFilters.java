package com.mine.geometry_node.core.utils;

import java.util.*;

/** Immutable, bounded column predicates. All active columns are combined with AND. */
public record ColumnFilters(String name, Choice types, NumberRange value, Choice dimensions,
                            NumberRange x, NumberRange y, NumberRange z, Choice lifetimes,
                            Set<String> includedTags, Set<String> excludedTags) {
    public static final int MAX_TEXT = 1024, MAX_CHOICES = 1024, MAX_TOTAL_TEXT = 8192;
    public ColumnFilters {
        checkText(name);
        Objects.requireNonNull(types); Objects.requireNonNull(value); Objects.requireNonNull(dimensions);
        Objects.requireNonNull(x); Objects.requireNonNull(y); Objects.requireNonNull(z); Objects.requireNonNull(lifetimes);
        includedTags = checkedSet(includedTags); excludedTags = checkedSet(excludedTags);
        int budget = name.length();
        for (var values : List.of(types.selected(), dimensions.selected(), lifetimes.selected(), includedTags, excludedTags))
            for (String text : values) budget += text.length();
        if (budget > MAX_TOTAL_TEXT) throw new IllegalArgumentException("Column filter text budget exceeded");
    }
    private static void checkText(String text) {
        if (text == null || text.length() > MAX_TEXT) throw new IllegalArgumentException("Column filter text too long");
    }
    private static Set<String> checkedSet(Set<String> values) {
        if (values.size() > MAX_CHOICES) throw new IllegalArgumentException("Too many column filter choices");
        var copy = Set.copyOf(values); int budget = 0;
        for (String text : copy) { checkText(text); budget += text.length(); }
        if (budget > MAX_TOTAL_TEXT) throw new IllegalArgumentException("Column filter text budget exceeded");
        return copy;
    }
    /** Shared by construction and wire decoding, counting text including duplicate wire entries. */
    public int textLength() {
        int total = name.length();
        for (var values : List.of(types.selected(), dimensions.selected(), lifetimes.selected(), includedTags, excludedTags))
            for (String text : values) total += text.length();
        return total;
    }
    /** Conservative UTF-8 plus five-byte length framing for every text/count. */
    public int estimatedWireBytes() {
        int bytes = 128 + wireTextBytes(name);
        for (var values : List.of(types.selected(), dimensions.selected(), lifetimes.selected(), includedTags, excludedTags))
            for (String text : values) bytes += wireTextBytes(text);
        return bytes;
    }
    public static int wireTextBytes(String text) {
        return 5 + text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
    public record Choice(boolean restricted, Set<String> selected) {
        public Choice { selected = checkedSet(selected); }
        public static Choice all() { return new Choice(false, Set.of()); }
        public boolean matches(String value) { return !restricted || value != null && selected.contains(value); }
        public boolean active() { return restricted; }
    }
    public record NumberRange(Double minimum, Double maximum) {
        public NumberRange {
            if ((minimum != null && !Double.isFinite(minimum)) || (maximum != null && !Double.isFinite(maximum))
                    || (minimum != null && maximum != null && minimum > maximum))
                throw new IllegalArgumentException("Invalid column numeric range");
        }
        public static NumberRange unbounded() { return new NumberRange(null, null); }
        public boolean active() { return minimum != null || maximum != null; }
        public boolean matches(Double value) {
            return !active() || value != null && Double.isFinite(value)
                    && (minimum == null || value >= minimum) && (maximum == null || value <= maximum);
        }
    }
    public static ColumnFilters empty() {
        return new ColumnFilters("", Choice.all(), NumberRange.unbounded(), Choice.all(), NumberRange.unbounded(),
                NumberRange.unbounded(), NumberRange.unbounded(), Choice.all(), Set.of(), Set.of());
    }
    public boolean active() {
        return !name.isEmpty() || types.active() || value.active() || dimensions.active() || x.active() || y.active()
                || z.active() || lifetimes.active() || !includedTags.isEmpty() || !excludedTags.isEmpty();
    }
    public boolean matches(String name, String type, Double value, String dimension, Double x, Double y, Double z,
                           String lifetime, Collection<String> tags) {
        return (this.name.isEmpty() || name != null && name.toLowerCase(Locale.ROOT).contains(this.name.toLowerCase(Locale.ROOT)))
                && types.matches(type) && this.value.matches(value) && dimensions.matches(dimension)
                && this.x.matches(x) && this.y.matches(y) && this.z.matches(z) && lifetimes.matches(lifetime)
                && tags.containsAll(includedTags) && Collections.disjoint(tags, excludedTags);
    }
    public ColumnFilters withName(String replacement) { return new ColumnFilters(replacement, types, value, dimensions, x, y, z, lifetimes, includedTags, excludedTags); }
    public ColumnFilters withTypes(Choice replacement) { return new ColumnFilters(name, replacement, value, dimensions, x, y, z, lifetimes, includedTags, excludedTags); }
    public ColumnFilters withValue(NumberRange replacement) { return new ColumnFilters(name, types, replacement, dimensions, x, y, z, lifetimes, includedTags, excludedTags); }
    public ColumnFilters withDimensions(Choice replacement) { return new ColumnFilters(name, types, value, replacement, x, y, z, lifetimes, includedTags, excludedTags); }
    public ColumnFilters withX(NumberRange replacement) { return new ColumnFilters(name, types, value, dimensions, replacement, y, z, lifetimes, includedTags, excludedTags); }
    public ColumnFilters withY(NumberRange replacement) { return new ColumnFilters(name, types, value, dimensions, x, replacement, z, lifetimes, includedTags, excludedTags); }
    public ColumnFilters withZ(NumberRange replacement) { return new ColumnFilters(name, types, value, dimensions, x, y, replacement, lifetimes, includedTags, excludedTags); }
    public ColumnFilters withLifetimes(Choice replacement) { return new ColumnFilters(name, types, value, dimensions, x, y, z, replacement, includedTags, excludedTags); }
    public ColumnFilters withTags(Set<String> include, Set<String> exclude) { return new ColumnFilters(name, types, value, dimensions, x, y, z, lifetimes, include, exclude); }
}
