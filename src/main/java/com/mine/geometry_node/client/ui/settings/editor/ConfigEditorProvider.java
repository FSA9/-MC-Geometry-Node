package com.mine.geometry_node.client.ui.settings.editor;

import com.mine.geometry_node.client.config.ConfigDraft;
import com.mine.geometry_node.client.config.ConfigEntry;
import icyllis.modernui.core.Context;

@FunctionalInterface
public interface ConfigEditorProvider {
    ConfigEntryEditor<?> create(Context context, ConfigDraft draft, ConfigEntry<?> entry,
                                SettingsEditorEnvironment environment);
}
