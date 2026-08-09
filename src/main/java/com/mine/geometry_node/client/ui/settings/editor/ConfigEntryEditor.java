package com.mine.geometry_node.client.ui.settings.editor;

import com.mine.geometry_node.client.ui.persistence.config.ConfigEntry;
import icyllis.modernui.view.View;

public interface ConfigEntryEditor<T> {
    ConfigEntry<T> entry();
    View getView();
    void refresh();
    void reset();
    boolean isValid();
    String validationMessage();
    void setOnStateChangedListener(Runnable listener);
    default void revalidate() {}
    default void dispose() {}
}
