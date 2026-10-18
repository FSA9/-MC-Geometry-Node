package com.mine.geometry_node.client.ui.components.table;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import java.util.HashSet;
import java.util.List;

/** Shared logical column widths for a table header and its rows. UI-thread confined. */
public final class UiTableColumns {
    public record Column(String id, float preferredDp, float minimumDp, boolean resizable) {
        public Column {
            if (id == null || id.isBlank() || !Float.isFinite(preferredDp)
                    || !Float.isFinite(minimumDp) || minimumDp <= 0 || preferredDp < minimumDp)
                throw new IllegalArgumentException("Invalid table column");
        }
    }

    private final List<Column> columns;
    private final int[] widths;
    private float[] customDp;
    private int viewport;
    private boolean dirty = true;
    private float resolvedDensity = Float.NaN;
    private Runnable changed = () -> {};

    public UiTableColumns(List<Column> columns) {
        this.columns = List.copyOf(columns);
        if (columns.isEmpty()) throw new IllegalArgumentException("A table needs columns");
        var ids = new HashSet<String>();
        for (Column column : columns) if (!ids.add(column.id()))
            throw new IllegalArgumentException("Duplicate table column: " + column.id());
        widths = new int[columns.size()];
        resolve();
    }

    public int size() { return columns.size(); }
    public Column column(int index) { return columns.get(index); }
    public int width(int index) { resolve(); return widths[index]; }
    public int contentWidth() {
        resolve();
        int total = 0;
        for (int width : widths) total += width;
        return Math.max(viewport, total);
    }
    public void setOnWidthsChanged(Runnable changed) { this.changed = changed == null ? () -> {} : changed; }
    public void setViewportWidth(int pixels) {
        int value = Math.max(0, pixels);
        if (viewport == value && resolvedDensity == UIUtils.dp2px(1)) return;
        viewport = value;
        dirty = true;
        resolve();
        changed.run();
    }

    /** Resize just this column; other columns retain their widths, with horizontal overflow if needed. */
    public void resize(int index, float deltaPixels) {
        if (!columns.get(index).resizable() || !Float.isFinite(deltaPixels) || deltaPixels == 0) return;
        resolve();
        float density = Math.max(.001f, UIUtils.dp2px(1));
        float oldDp = customDp == null ? widths[index] / density : customDp[index];
        float next = Math.max(columns.get(index).minimumDp(), Math.min(4096, oldDp + deltaPixels / density));
        if (next == oldDp) return;
        if (customDp == null) {
            customDp = new float[size()];
            for (int i = 0; i < size(); i++) customDp[i] = widths[i] / density;
        }
        customDp[index] = next;
        dirty = true;
        resolve();
        changed.run();
    }

    public void reset() {
        if (customDp == null) return;
        customDp = null;
        dirty = true;
        resolve();
        changed.run();
    }

    private void resolve() {
        float density = UIUtils.dp2px(1);
        if (!dirty && density == resolvedDensity) return;
        dirty = false;
        resolvedDensity = density;
        if (customDp != null) {
            for (int i = 0; i < size(); i++) widths[i] = Math.max(1, UIUtils.dp2pxInt(customDp[i]));
            return;
        }
        int minimum = 0;
        float flexible = 0;
        for (int i = 0; i < size(); i++) {
            Column column = columns.get(i);
            widths[i] = Math.max(1, UIUtils.dp2pxInt(column.minimumDp()));
            minimum += widths[i];
            flexible += column.preferredDp() - column.minimumDp();
        }
        int remaining = Math.max(0, viewport - minimum);
        float cumulative = 0;
        int assigned = 0;
        for (int i = 0; i < size(); i++) {
            cumulative += columns.get(i).preferredDp() - columns.get(i).minimumDp();
            int end = flexible == 0 ? 0 : Math.round(remaining * cumulative / flexible);
            widths[i] += end - assigned;
            assigned = end;
        }
    }
}
