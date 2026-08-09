package com.mine.geometry_node.client.ui.settings.page.api;

import com.mine.geometry_node.client.ui.persistence.config.ConfigDraft;
import com.mine.geometry_node.client.ui.persistence.config.ConfigRegistry;
import com.mine.geometry_node.client.ui.settings.editor.SettingsEditorEnvironment;
import com.mine.geometry_node.client.ui.settings.editor.SettingsEditorFactory;
import icyllis.modernui.core.Context;

import java.util.Objects;

/** Dependencies shared by every page instance in one settings window. */
public record SettingsPageContext(
        Context uiContext,
        ConfigDraft draft,
        ConfigRegistry configRegistry,
        SettingsEditorFactory editorFactory,
        SettingsEditorEnvironment editorEnvironment,
        Runnable onStateChanged
) {
    public SettingsPageContext {
        uiContext = Objects.requireNonNull(uiContext, "uiContext");
        draft = Objects.requireNonNull(draft, "draft");
        configRegistry = Objects.requireNonNull(configRegistry, "configRegistry");
        editorFactory = Objects.requireNonNull(editorFactory, "editorFactory");
        editorEnvironment = editorEnvironment != null ? editorEnvironment : SettingsEditorEnvironment.NONE;
        onStateChanged = onStateChanged != null ? onStateChanged : () -> { };
    }
}
