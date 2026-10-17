package com.mine.geometry_node.client.runtime.game.selection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Shared selection algebra for scene, entity and future block/plugin identities. */
public final class ViewportSelection {
    private ViewportSelection() {}
    public static Set<ViewportTarget.Key> apply(Collection<ViewportTarget.Key> previous,
            Collection<ViewportTarget.Key> hits, boolean additive, boolean subtractive, boolean click) {
        Set<ViewportTarget.Key> result = new LinkedHashSet<>();
        if (additive || subtractive) result.addAll(previous);
        for (var hit : hits) {
            if (subtractive || (click && additive && result.contains(hit))) result.remove(hit);
            else result.add(hit);
        }
        return Set.copyOf(result);
    }
}
