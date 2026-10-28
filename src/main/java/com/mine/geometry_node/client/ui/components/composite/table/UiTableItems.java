package com.mine.geometry_node.client.ui.components.composite.table;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/** Stable-key row reconciliation shared by ordinary tables. View creation stays on the UI thread. */
public final class UiTableItems<K, T> implements AutoCloseable {
    public interface Row<T> {
        View view();
        void bind(T item, int index);
        default void dispose() {}
    }
    private final UiTable table;
    private final LinearLayout body;
    private final Function<T, K> key;
    private final Function<T, Row<T>> factory;
    private final Map<K, Row<T>> rows = new LinkedHashMap<>();
    private BooleanSupplier reorderBlocked = () -> false;
    private boolean disposed;
    private long generation;

    public static LinearLayout body(Context context) {
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        return body;
    }

    public UiTableItems(UiTable table, Function<T, K> key, Function<T, Row<T>> factory) {
        this.table = Objects.requireNonNull(table);
        this.key = Objects.requireNonNull(key);
        this.factory = Objects.requireNonNull(factory);
        if (!(table.content() instanceof LinearLayout content))
            throw new IllegalArgumentException("Managed table rows need a linear body");
        body = content;
    }

    public void setReorderBlocked(BooleanSupplier blocked) { reorderBlocked = Objects.requireNonNull(blocked); }

    public void replace(List<T> items) {
        replace(items, item -> true);
    }

    /** A filtered-out row keeps its editor/draft; only deleted identities are disposed. */
    public void replace(List<T> items, Predicate<T> visible) {
        if (disposed) return;
        long token = ++generation;
        LinkedHashMap<K, T> incoming = new LinkedHashMap<>();
        for (T item : items) {
            K id = Objects.requireNonNull(key.apply(item), "Null table key");
            if (incoming.putIfAbsent(id, Objects.requireNonNull(item)) != null)
                throw new IllegalArgumentException("Duplicate table key: " + id);
        }
        for (K id : List.copyOf(rows.keySet())) if (!incoming.containsKey(id)) {
            remove(id);
            if (token != generation || disposed) return;
        }
        boolean blocked = reorderBlocked.getAsBoolean();
        int index = 0;
        List<K> visibleKeys = new ArrayList<>();
        for (var entry : incoming.entrySet()) {
            Row<T> row = rows.get(entry.getKey());
            if (row == null) {
                row = Objects.requireNonNull(factory.apply(entry.getValue()));
                if (token != generation || disposed) { row.dispose(); return; }
                rows.put(entry.getKey(), row);
                body.addView(row.view(), new LinearLayout.LayoutParams(-1, -2));
            }
            boolean shown = visible.test(entry.getValue());
            row.bind(entry.getValue(), shown ? index : -1);
            if (token != generation || disposed) return;
            row.view().setVisibility(shown ? View.VISIBLE : View.GONE);
            if (!shown) continue;
            visibleKeys.add(entry.getKey());
            table.styleRow(row.view(), index, row.view().isSelected());
            // A focused inline editor must retain its parent throughout snapshot updates.
            index++;
        }
        if (!blocked) {
            // bringChildToFront preserves focus; applying all keys commits the final order.
            List<View> expected = visibleKeys.stream().map(id -> rows.get(id).view()).toList();
            List<View> current = new ArrayList<>();
            for (int i = 0; i < body.getChildCount(); i++)
                if (body.getChildAt(i).getVisibility() != View.GONE) current.add(body.getChildAt(i));
            if (!current.equals(expected)) for (View view : expected) body.bringChildToFront(view);
        }
    }

    private void remove(K id) {
        Row<T> row = rows.remove(id);
        row.dispose();
        body.removeView(row.view());
    }
    public void clear() {
        long token = ++generation;
        for (K id : List.copyOf(rows.keySet())) {
            remove(id);
            if (token != generation) return;
        }
    }
    public void dispose() { if (!disposed) { disposed = true; clear(); } }
    @Override public void close() { dispose(); }
}
