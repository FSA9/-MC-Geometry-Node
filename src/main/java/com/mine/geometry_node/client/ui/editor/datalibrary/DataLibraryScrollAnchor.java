package com.mine.geometry_node.client.ui.editor.datalibrary;

import icyllis.modernui.view.View;
import icyllis.modernui.widget.ScrollView;

import java.util.Map;

/** Keeps the first visible stable row at its pixel offset when preceding rows change. */
record DataLibraryScrollAnchor(Object key, int offset, int fallbackY) {
    static DataLibraryScrollAnchor capture(ScrollView scroll, Map<?, ? extends View> rows) {
        Object key = null;
        int chosenTop = Integer.MAX_VALUE;
        int y = scroll.getScrollY();
        for (var row : rows.entrySet()) {
            int top = top(row.getValue(), scroll);
            if (top == Integer.MIN_VALUE || top + row.getValue().getHeight() <= y) continue;
            if (top < chosenTop) {
                chosenTop = top;
                key = row.getKey();
            }
        }
        return new DataLibraryScrollAnchor(key, key == null ? 0 : chosenTop - y, y);
    }

    void restore(ScrollView scroll, Map<?, ? extends View> rows) {
        View row = rows.get(key);
        int top = row == null ? Integer.MIN_VALUE : top(row, scroll);
        scroll.scrollTo(0, Math.max(0, top == Integer.MIN_VALUE ? fallbackY : top - offset));
    }

    private static int top(View row, ScrollView scroll) {
        int y = 0;
        for (View current = row; current != null;) {
            y += current.getTop();
            if (current.getParent() == scroll) return y;
            current = current.getParent() instanceof View parent ? parent : null;
        }
        return Integer.MIN_VALUE;
    }
}
