package com.mine.geometry_node.client.ui.components.composite.table;

import icyllis.modernui.view.View;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A semantic column declaration. Geometry is keyed by the same stable ID in UiTableColumns. */
public record UiTableColumn(String id, String title, Runnable sort, Consumer<View> filter,
                            Supplier<View> customHeader) {
    public UiTableColumn {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("A column needs an ID");
        title = Objects.requireNonNullElse(title, "");
        if (customHeader != null && (sort != null || filter != null))
            throw new IllegalArgumentException("A custom header cannot replace sort/filter controls");
    }

    public static UiTableColumn label(String id, String title) {
        return new UiTableColumn(id, title, null, null, null);
    }

    public static UiTableColumn interactive(String id, String title, Runnable sort, Consumer<View> filter) {
        return new UiTableColumn(id, title, sort, filter, null);
    }

    public static UiTableColumn custom(String id, Supplier<View> header) {
        return new UiTableColumn(id, "", null, null, Objects.requireNonNull(header));
    }
}
