package com.mine.geometry_node.client.ui.settings.editor;

import icyllis.modernui.view.View;
import java.util.List;
import java.util.function.Consumer;

/** Optional services supplied later by the global settings window. */
public interface SettingsEditorEnvironment {
    SettingsEditorEnvironment NONE = new SettingsEditorEnvironment() {};

    default boolean showChoices(View anchor, List<SettingsChoice> values, String selected, Consumer<String> onSelect) {
        return false;
    }

    default boolean requestDirectory(View anchor, Consumer<String> onSelect) {
        return false;
    }
}
