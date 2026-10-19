package com.mine.geometry_node.core.metadata;

import java.util.*;

/** Shared ordered-set operations. Returned collections are immutable. */
public final class TagSet {
    public static final int MAX_TAGS = 1024;
    public static final int MAX_TAG_LENGTH = 256;
    private TagSet() {}

    public static List<String> normalize(Collection<String> tags, TagPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (tags == null) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String raw : tags) {
            String tag = policy.normalize(raw);
            if (!tag.isEmpty()) result.add(tag);
            if (policy == TagPolicy.EXACT && result.size() > MAX_TAGS) throw new IllegalArgumentException("Too many tags");
        }
        return List.copyOf(result);
    }

    public static List<String> add(Collection<String> tags, Collection<String> additions, TagPolicy policy) {
        List<String> result = new ArrayList<>(normalize(tags, policy));
        result.addAll(normalize(additions, policy));
        return normalize(result, policy);
    }

    public static List<String> remove(Collection<String> tags, Collection<String> removals, TagPolicy policy) {
        Set<String> removed = Set.copyOf(normalize(removals, policy));
        return normalize(tags, policy).stream().filter(tag -> !removed.contains(tag)).toList();
    }

    public static boolean contains(Collection<String> tags, String tag, TagPolicy policy) {
        return normalize(tags, policy).contains(policy.normalize(tag));
    }

    public static boolean containsAll(Collection<String> tags, Collection<String> required, TagPolicy policy) {
        return new HashSet<>(normalize(tags, policy)).containsAll(normalize(required, policy));
    }
}
