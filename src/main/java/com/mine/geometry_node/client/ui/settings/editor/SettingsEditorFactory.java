package com.mine.geometry_node.client.ui.settings.editor;

import com.mine.geometry_node.client.ui.persistence.config.ConfigDraft;
import com.mine.geometry_node.client.ui.persistence.config.ConfigEntry;
import icyllis.modernui.core.Context;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Extensible factory with no dependency on the settings window or MainUI root. */
public final class SettingsEditorFactory {
    public static final SettingsEditorFactory INSTANCE = new SettingsEditorFactory();
    private final Map<ConfigEntry.EditorType, ConfigEditorProvider> mProviders = new EnumMap<>(ConfigEntry.EditorType.class);
    private final Map<String, ConfigEditorProvider> mEntryProviders = new HashMap<>();

    private SettingsEditorFactory() {
        register(ConfigEntry.EditorType.BOOLEAN, BooleanConfigEntryEditor::create);
        register(ConfigEntry.EditorType.INTEGER, NumberConfigEntryEditor::create);
        register(ConfigEntry.EditorType.FLOAT, NumberConfigEntryEditor::create);
        register(ConfigEntry.EditorType.CHOICE, ChoiceConfigEntryEditor::create);
        register(ConfigEntry.EditorType.PATH, PathConfigEntryEditor::create);
        register(ConfigEntry.EditorType.PATH_LIST, PathListConfigEntryEditor::create);
        register(ConfigEntry.EditorType.KEY_BINDING, ShortcutConfigEntryEditor::create);
        register(ConfigEntry.EditorType.SHORTCUT, ShortcutConfigEntryEditor::create);
    }

    public synchronized void register(ConfigEntry.EditorType type, ConfigEditorProvider provider) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(provider, "provider");
        if (mProviders.putIfAbsent(type, provider) != null) {
            throw new IllegalArgumentException("Config editor provider already registered: " + type);
        }
    }

    public synchronized void register(String entryId, ConfigEditorProvider provider) {
        String normalizedId = Objects.requireNonNullElse(entryId, "").trim();
        if (normalizedId.isEmpty()) throw new IllegalArgumentException("Config entry id cannot be blank");
        Objects.requireNonNull(provider, "provider");
        if (mEntryProviders.putIfAbsent(normalizedId, provider) != null) {
            throw new IllegalArgumentException("Config editor provider already registered for entry: " + normalizedId);
        }
    }

    public synchronized ConfigEntryEditor<?> create(Context context, ConfigDraft draft, ConfigEntry<?> entry,
                                                     SettingsEditorEnvironment environment) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(entry, "entry");
        ConfigEditorProvider provider = mEntryProviders.getOrDefault(entry.id(), mProviders.get(entry.editorType()));
        if (provider == null) throw new IllegalStateException("No config editor provider for " + entry.editorType());
        ConfigEntryEditor<?> editor = provider.create(context, draft, entry,
                environment != null ? environment : SettingsEditorEnvironment.NONE);
        if (editor == null || editor.getView() == null) {
            throw new IllegalStateException("Config editor provider returned no view for " + entry.id());
        }
        editor.refresh();
        return editor;
    }
}
