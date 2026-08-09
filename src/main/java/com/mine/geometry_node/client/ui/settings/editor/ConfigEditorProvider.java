package com.mine.geometry_node.client.ui.settings.editor;

import com.mine.geometry_node.client.ui.persistence.config.ConfigDraft;
import com.mine.geometry_node.client.ui.persistence.config.ConfigEntry;
import icyllis.modernui.core.Context;

@FunctionalInterface
public interface ConfigEditorProvider {
    ConfigEntryEditor<?> create(Context context, ConfigDraft draft, ConfigEntry<?> entry,
                                SettingsEditorEnvironment environment);
}
