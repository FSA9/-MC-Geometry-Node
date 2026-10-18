package com.mine.geometry_node.client.ui.components.list;

import java.util.List;
import java.util.Set;

/** Stable-key list selection rules, independent of widgets, sorting, and data repositories. */
public final class UiListSelection {
    private UiListSelection() {}

    /** Applies a row click and returns the range anchor. Checkboxes use independent set membership. */
    public static <K> K click(Set<K> selected, List<K> ordered, K anchor, K target,
                             boolean additive, boolean extend) {
        int end = ordered.indexOf(target);
        if (end < 0) return anchor;
        if (!extend) {
            if (!additive) selected.clear();
            if (!additive || !selected.remove(target)) selected.add(target);
            return target;
        }
        int start = anchor == null ? -1 : ordered.indexOf(anchor);
        if (start < 0) { start = end; anchor = target; }
        if (!additive) selected.clear();
        selected.addAll(ordered.subList(Math.min(start, end), Math.max(start, end) + 1));
        return anchor;
    }
}
