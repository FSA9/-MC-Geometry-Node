package com.mine.geometry_node.client.ui.components.composite.table;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Shared logical column widths for a table header and its rows. UI-thread confined. */
public final class UiTableColumns {
    public enum Sizing { FILL_VIEWPORT, INDEPENDENT }
    public static final float MAX_CUSTOM_WIDTH_DP = 4096f;
    public record Column(String id, float preferredDp, float minimumDp, boolean resizable) {
        public Column {
            if (id == null || id.isBlank() || !Float.isFinite(preferredDp)
                    || !Float.isFinite(minimumDp) || minimumDp <= 0 || preferredDp < minimumDp
                    || (resizable && minimumDp > MAX_CUSTOM_WIDTH_DP))
                throw new IllegalArgumentException("Invalid table column");
        }
    }

    private final List<Column> columns;
    private final Sizing sizing;
    private final int[] widths;
    private float[] customDp;
    // Freeze automatic siblings for the live drag session without turning them into persisted overrides.
    private float[] frozenDp;
    private final List<Runnable> userStateListeners = new ArrayList<>();
    private int viewport;
    private boolean dirty = true;
    private float resolvedDensity = Float.NaN;
    private Runnable changed = () -> {};

    public UiTableColumns(List<Column> columns) {
        this(columns, Sizing.FILL_VIEWPORT);
    }

    /** Independent columns retain their preferred/user widths when adjacent panels resize. */
    public UiTableColumns(List<Column> columns, Sizing sizing) {
        this.sizing = Objects.requireNonNull(sizing);
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
    void clearOnWidthsChanged(Runnable listener) {
        if (changed == listener) changed = () -> {};
    }

    /** Detached logical-dp snapshot of explicit resizes only. The owner chooses memory or disk storage. */
    public Map<String, Float> exportCustomWidths() {
        var result = new LinkedHashMap<String, Float>();
        if (customDp != null) for (int i = 0; i < size(); i++)
            if (columns.get(i).resizable() && Float.isFinite(customDp[i]))
                result.put(columns.get(i).id(), customDp[i]);
        return result;
    }

    /** Replaces custom state in logical dp. Unknown/fixed IDs and null/nonfinite values are ignored;
     * finite values clamp to the column minimum through 4096 dp. Missing columns use automatic
     * defaults. Refreshes layout but never emits user changes. No storage is accessed. */
    public void restoreCustomWidths(Map<String, Float> state) {
        Objects.requireNonNull(state);
        float[] restored = new float[size()];
        java.util.Arrays.fill(restored, Float.NaN);
        boolean any = false;
        for (int i = 0; i < size(); i++) {
            Column column = columns.get(i);
            Float value = state.get(column.id());
            if (!column.resizable() || value == null || !Float.isFinite(value)) continue;
            restored[i] = Math.max(column.minimumDp(), Math.min(MAX_CUSTOM_WIDTH_DP, value));
            any = true;
        }
        customDp = any ? restored : null;
        frozenDp = null;
        dirty = true;
        resolve();
        changed.run();
    }

    /** Independent of the viewport layout callback; close on the UI thread to unsubscribe. */
    public AutoCloseable addUserStateListener(Runnable listener) {
        Objects.requireNonNull(listener);
        Runnable subscription = listener::run;
        userStateListeners.add(subscription);
        return () -> userStateListeners.remove(subscription);
    }

    private void notifyUserStateChanged() {
        for (Runnable listener : List.copyOf(userStateListeners)) listener.run();
    }

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
        float oldDp = frozenDp == null ? widths[index] / density : frozenDp[index];
        if (customDp != null && Float.isFinite(customDp[index])) oldDp = customDp[index];
        float next = Math.max(columns.get(index).minimumDp(), Math.min(MAX_CUSTOM_WIDTH_DP, oldDp + deltaPixels / density));
        if (next == oldDp) return;
        if (frozenDp == null) {
            frozenDp = new float[size()];
            for (int i = 0; i < size(); i++) frozenDp[i] = widths[i] / density;
        }
        if (customDp == null) {
            customDp = new float[size()];
            java.util.Arrays.fill(customDp, Float.NaN);
        }
        customDp[index] = next;
        dirty = true;
        resolve();
        changed.run();
        notifyUserStateChanged();
    }

    public void reset() {
        if (customDp == null) return;
        customDp = null;
        frozenDp = null;
        dirty = true;
        resolve();
        changed.run();
        notifyUserStateChanged();
    }

    private void resolve() {
        float density = UIUtils.dp2px(1);
        if (!dirty && density == resolvedDensity) return;
        dirty = false;
        resolvedDensity = density;
        int minimum = 0;
        float flexible = 0;
        for (int i = 0; i < size(); i++) {
            Column column = columns.get(i);
            widths[i] = Math.max(1, UIUtils.dp2pxInt(sizing == Sizing.INDEPENDENT
                    ? column.preferredDp() : column.minimumDp()));
            minimum += widths[i];
            flexible += column.preferredDp() - column.minimumDp();
        }
        int remaining = sizing == Sizing.INDEPENDENT ? 0 : Math.max(0, viewport - minimum);
        float cumulative = 0;
        int assigned = 0;
        for (int i = 0; i < size(); i++) {
            cumulative += columns.get(i).preferredDp() - columns.get(i).minimumDp();
            int end = flexible == 0 ? 0 : Math.round(remaining * cumulative / flexible);
            widths[i] += end - assigned;
            assigned = end;
        }
        for (int i = 0; i < size(); i++) {
            float dp = customDp != null ? customDp[i] : Float.NaN;
            if (!Float.isFinite(dp) && frozenDp != null) dp = frozenDp[i];
            if (Float.isFinite(dp)) widths[i] = Math.max(1, UIUtils.dp2pxInt(dp));
        }
    }
}
