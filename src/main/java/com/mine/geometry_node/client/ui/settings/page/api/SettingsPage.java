package com.mine.geometry_node.client.ui.settings.page.api;

import com.mine.geometry_node.client.ui.settings.editor.ConfigEntryEditor;
import icyllis.modernui.view.View;

import java.util.List;

/** One settings page instance owned by a settings window. */
public interface SettingsPage extends AutoCloseable {
    View getView();

    List<ConfigEntryEditor<?>> editors();

    default void refresh() {
        editors().forEach(ConfigEntryEditor::refresh);
    }

    default boolean isValid() {
        return editors().stream().allMatch(ConfigEntryEditor::isValid);
    }

    /** Applies a normalized free-text query and reports whether the page has a match. */
    default boolean applySearch(String query) {
        return query == null || query.isBlank();
    }

    default void dispose() {
        editors().forEach(ConfigEntryEditor::dispose);
    }

    @Override
    default void close() {
        dispose();
    }
}
