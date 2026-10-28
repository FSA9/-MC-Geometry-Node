package com.mine.geometry_node.client.ui.components.composite.table;

import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import java.util.*;
import java.util.function.Function;

/**
 * Complete table presentation: one schema owns headers, cell order, scroll geometry and row style.
 * Queries, permissions and writes belong to the data owner. Specialized bodies can preserve their
 * drag/expansion/virtualization semantics without assembling another table shell.
 */
public final class UiTable extends LinearLayout {
    public enum Layout { FILL, CONTENT }
    public record Sort(String column, int direction) {
        public Sort {
            column = Objects.requireNonNullElse(column, "");
            if (direction < 0 || direction > 2) throw new IllegalArgumentException("Invalid sort direction");
        }
    }
    private final UiTheme theme;
    private final UiTableColumns columns;
    private final List<UiTableColumn> definitions;
    private final Map<String, UiTableFilterHeader> headers = new LinkedHashMap<>();
    private final UiTableRow header;
    private final UiScrollView scroll;
    private final View content;
    private final Layout layout;
    private Runnable scrolled = () -> {};
    private final List<Runnable> dismissals = new ArrayList<>();
    private boolean disposed;
    private Sort sort = new Sort("", 0);

    public UiTable(Context context, UiTheme theme, UiTableColumns columns,
                   List<UiTableColumn> definitions, View content) {
        this(context, theme, columns, definitions, content, Layout.FILL);
    }

    public UiTable(Context context, UiTheme theme, UiTableColumns columns,
                   List<UiTableColumn> definitions, View content, Layout layout) {
        super(context);
        this.theme = Objects.requireNonNull(theme);
        this.columns = Objects.requireNonNull(columns);
        this.definitions = List.copyOf(definitions);
        this.content = Objects.requireNonNull(content);
        this.layout = Objects.requireNonNull(layout);
        if (definitions.size() != columns.size()) throw new IllegalArgumentException("Table schema size mismatch");
        for (int i = 0; i < columns.size(); i++)
            if (!columns.column(i).id().equals(definitions.get(i).id()))
                throw new IllegalArgumentException("Table schema order mismatch: " + definitions.get(i).id());
        setOrientation(VERTICAL);
        LinearLayout surface = new LinearLayout(context);
        surface.setOrientation(VERTICAL);
        header = UiTableStyle.header(context, columns, theme);
        for (UiTableColumn definition : this.definitions) {
            View cell;
            if (definition.customHeader() != null) cell = definition.customHeader().get();
            else if (definition.sort() != null || definition.filter() != null) {
                UiTableFilterHeader control = new UiTableFilterHeader(context, definition.title(),
                        definition.sort(), definition.filter() == null ? null
                        : () -> definition.filter().accept(headers.get(definition.id()).filterButton()), theme);
                headers.put(definition.id(), control);
                cell = control;
            } else cell = UiTableStyle.cell(context, definition.title(), theme);
            header.addView(Objects.requireNonNull(cell, definition.id()));
        }
        surface.addView(header, new LayoutParams(-1, px(UiTableStyle.HEADER_HEIGHT)));
        scroll = new UiScrollView(context, theme.scroll());
        scroll.setFillViewport(layout == Layout.FILL);
        scroll.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
            dismiss();
            scrolled.run();
        });
        surface.addView(scroll, layout == Layout.FILL ? new LayoutParams(-1, 0, 1) : new LayoutParams(-1, -2));
        UiTableViewport viewport = new UiTableViewport(context, columns, surface, theme);
        viewport.setOnScrollChangeListener((view, x, y, oldX, oldY) -> dismiss());
        if (layout == Layout.CONTENT) surface.getLayoutParams().height = -2;
        addView(viewport, new LayoutParams(-1, layout == Layout.FILL ? -1 : -2));
    }

    public UiTableColumns columns() { return columns; }
    public UiTheme theme() { return theme; }
    public View content() { return content; }
    public UiScrollView scroll() { return scroll; }
    public UiTableRow headerRow() { return header; }
    public UiTableFilterHeader header(String id) {
        UiTableFilterHeader result = headers.get(id);
        if (result == null) throw new IllegalArgumentException("No interactive header: " + id);
        return result;
    }

    public void setFilterActive(String id, boolean active) { header(id).setFilterActive(active); }

    /** 0 = natural order, 1 = ascending, 2 = descending. State is supplied by the query owner. */
    public void setSort(String id, int direction) {
        if (direction < 0 || direction > 2) throw new IllegalArgumentException("Invalid sort direction");
        if (direction != 0 && !headers.containsKey(id))
            throw new IllegalArgumentException("No sortable header: " + id);
        sort = new Sort(id, direction);
        headers.forEach((key, control) -> control.setSortDirection(key.equals(id) ? direction : 0));
    }

    public Sort sort() { return sort; }
    /** Natural -> ascending -> descending -> natural, shared by tables with an unsorted state. */
    public Sort cycleSort(String id) {
        setSort(id, sort.column().equals(id) ? (sort.direction() + 1) % 3 : 1);
        return sort;
    }
    /** A table with a required order alternates ascending/descending. */
    public Sort toggleSort(String id) {
        setSort(id, sort.column().equals(id) && sort.direction() == 1 ? 2 : 1);
        return sort;
    }

    public void setOnScrollChanged(Runnable callback) { scrolled = callback == null ? () -> {} : callback; }

    /** Popovers are dismissed on scroll, hide, focus loss, detach and explicit suspension. */
    public void onDismiss(Runnable callback) { dismissals.add(Objects.requireNonNull(callback)); }
    public void dismiss() { for (Runnable callback : List.copyOf(dismissals)) callback.run(); }
    public void dispose() { if (!disposed) { dismiss(); disposed = true; dismissals.clear(); scrolled = () -> {}; } }

    public UiTableRow createRow(Function<String, View> cells) {
        UiTableRow row = new UiTableRow(getContext(), columns, theme);
        for (UiTableColumn definition : definitions)
            row.addView(Objects.requireNonNull(cells.apply(definition.id()), "Missing cell: " + definition.id()));
        styleRow(row, 0);
        return row;
    }

    public UiTableRow createRow(Map<String, ? extends View> cells) {
        if (cells.size() != definitions.size()) throw new IllegalArgumentException("Table row schema mismatch");
        return createRow(cells::get);
    }

    public void styleRow(UiTableRow row, int index) { styleRow(row, index, false); }
    public void styleRow(UiTableRow row, int index, boolean selected) {
        row.setMinimumHeight(px(UiTableStyle.ROW_HEIGHT));
        row.setDataStyle(index % 2 != 0);
        row.setSelected(selected);
    }

    public void styleRow(View row, int index, boolean selected) {
        if (row instanceof UiTableRow cells) styleRow(cells, index, selected);
        else UiTableRowAppearance.apply(row, index, selected, theme);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        if (!focused) dismiss();
        super.onWindowFocusChanged(focused);
    }
    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (visibility != VISIBLE && dismissals != null) dismiss();
    }
    @Override protected void onDetachedFromWindow() { dismiss(); super.onDetachedFromWindow(); }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        dismiss();
    }
    private static int px(float dp) { return UIUtils.dp2pxInt(dp); }
}
