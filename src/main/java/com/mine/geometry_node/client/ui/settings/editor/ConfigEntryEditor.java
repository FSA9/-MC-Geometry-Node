package com.mine.geometry_node.client.ui.settings.editor;

import com.mine.geometry_node.client.config.ConfigEntry;
import icyllis.modernui.view.View;

public interface ConfigEntryEditor<T> {
    ConfigEntry<T> entry();
    View getView();
    void refresh();
    void reset();
    boolean isValid();
    String validationMessage();
    void setOnStateChangedListener(Runnable listener);
    /** Focuses the editable control after its page has revealed the row. */
    default void focus() { getView().requestFocus(); }
    /** Local input that could not yet be written to the typed draft. */
    default boolean hasUncommittedChanges() { return false; }
    default void revalidate() {}
    default void dispose() {}
}
