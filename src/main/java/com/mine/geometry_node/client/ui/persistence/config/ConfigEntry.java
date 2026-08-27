package com.mine.geometry_node.client.ui.persistence.config;

import com.mine.geometry_node.client.ui.shortcut.KeyScope;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Type-safe contract shared by config persistence, settings UI and runtime consumers.
 */
public final class ConfigEntry<T> {
    public enum EditorType {
        BOOLEAN,
        INTEGER,
        FLOAT,
        CHOICE,
        PATH,
        PATH_LIST,
        KEY_BINDING,
        SHORTCUT
    }

    public enum SettingsVisibility {
        VISIBLE,
        HIDDEN
    }

    private final String mId;
    private final ConfigCategory mCategory;
    private final String mLabelTranslationKey;
    private final String mDescriptionTranslationKey;
    private final int mOrder;
    private final EditorType mEditorType;
    private final SettingsVisibility mSettingsVisibility;
    private final double mMin;
    private final double mMax;
    private final double mStep;
    private final List<String> mChoices;
    private final Map<String, String> mChoiceTranslationKeys;
    private final KeyScope mKeyScope;
    private final Function<AppConfig, T> mGetter;
    private final BiConsumer<AppConfig, T> mSetter;
    private final UnaryOperator<T> mNormalizer;

    private ConfigEntry(Builder<T> builder) {
        mId = requireId(builder.mId);
        mCategory = Objects.requireNonNull(builder.mCategory, "category");
        mLabelTranslationKey = requireText(builder.mLabelTranslationKey, "labelTranslationKey");
        mDescriptionTranslationKey = requireText(builder.mDescriptionTranslationKey, "descriptionTranslationKey");
        mOrder = builder.mOrder;
        mEditorType = Objects.requireNonNull(builder.mEditorType, "editorType");
        mSettingsVisibility = Objects.requireNonNull(builder.mSettingsVisibility, "settingsVisibility");
        mMin = builder.mMin;
        mMax = builder.mMax;
        mStep = builder.mStep;
        mChoices = List.copyOf(builder.mChoices);
        mChoiceTranslationKeys = Map.copyOf(builder.mChoiceTranslationKeys);
        mKeyScope = builder.mKeyScope;
        mGetter = Objects.requireNonNull(builder.mGetter, "getter");
        mSetter = Objects.requireNonNull(builder.mSetter, "setter");
        mNormalizer = Objects.requireNonNull(builder.mNormalizer, "normalizer");

        if (mMax < mMin) throw new IllegalArgumentException("Config entry max cannot be less than min: " + mId);
        if ((mEditorType == EditorType.CHOICE) && mChoices.isEmpty()) {
            throw new IllegalArgumentException("Choice config entry requires options: " + mId);
        }
        if ((mEditorType == EditorType.KEY_BINDING || mEditorType == EditorType.SHORTCUT) && mKeyScope == null) {
            throw new IllegalArgumentException("Shortcut config entry requires a input scope: " + mId);
        }
    }

    public static <T> Builder<T> builder(String id, ConfigCategory category, EditorType editorType,
                                         Function<AppConfig, T> getter, BiConsumer<AppConfig, T> setter) {
        return new Builder<>(id, category, editorType, getter, setter);
    }

    public String id() { return mId; }
    public ConfigCategory category() { return mCategory; }
    public String labelTranslationKey() { return mLabelTranslationKey; }
    public String descriptionTranslationKey() { return mDescriptionTranslationKey; }
    public int order() { return mOrder; }
    public EditorType editorType() { return mEditorType; }
    public SettingsVisibility settingsVisibility() { return mSettingsVisibility; }
    public boolean isVisibleInSettings() { return mSettingsVisibility == SettingsVisibility.VISIBLE; }
    public double min() { return mMin; }
    public double max() { return mMax; }
    public double step() { return mStep; }
    public List<String> choices() { return mChoices; }
    public String choiceTranslationKey(String value) { return mChoiceTranslationKeys.getOrDefault(value, ""); }
    public KeyScope keyScope() { return mKeyScope; }

    public T get(AppConfig config) {
        return copy(mGetter.apply(Objects.requireNonNull(config, "config")));
    }

    public void set(AppConfig config, T value) {
        mSetter.accept(Objects.requireNonNull(config, "config"), normalize(value));
    }

    public T defaultValue() {
        return get(AppConfig.defaults());
    }

    public boolean accepts(T value) {
        return mNormalizer.apply(value) != null;
    }

    public T normalize(T value) {
        T normalized = mNormalizer.apply(value);
        return normalized != null ? copy(normalized) : defaultValue();
    }

    @SuppressWarnings("unchecked")
    private T copy(T value) {
        if (value instanceof List<?> list) return (T) List.copyOf(list);
        return value;
    }

    private static String requireId(String value) {
        String id = requireText(value, "id");
        if (id.startsWith(".") || id.endsWith(".") || id.contains("..")) {
            throw new IllegalArgumentException("Invalid config entry id: " + id);
        }
        return id;
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Config entry " + name + " cannot be blank");
        return normalized;
    }

    public static final class Builder<T> {
        private final String mId;
        private final ConfigCategory mCategory;
        private final EditorType mEditorType;
        private final Function<AppConfig, T> mGetter;
        private final BiConsumer<AppConfig, T> mSetter;
        private SettingsVisibility mSettingsVisibility = SettingsVisibility.VISIBLE;
        private String mLabelTranslationKey;
        private String mDescriptionTranslationKey;
        private int mOrder;
        private double mMin;
        private double mMax;
        private double mStep;
        private List<String> mChoices = List.of();
        private Map<String, String> mChoiceTranslationKeys = Map.of();
        private KeyScope mKeyScope;
        private UnaryOperator<T> mNormalizer = UnaryOperator.identity();

        private Builder(String id, ConfigCategory category, EditorType editorType,
                        Function<AppConfig, T> getter, BiConsumer<AppConfig, T> setter) {
            mId = id;
            mCategory = category;
            mEditorType = editorType;
            mGetter = getter;
            mSetter = setter;
        }

        public Builder<T> label(String translationKey) { mLabelTranslationKey = translationKey; return this; }
        public Builder<T> description(String translationKey) { mDescriptionTranslationKey = translationKey; return this; }
        public Builder<T> order(int order) { mOrder = order; return this; }
        public Builder<T> range(double min, double max, double step) {
            mMin = min; mMax = max; mStep = step; return this;
        }
        public Builder<T> choices(List<String> choices) { mChoices = choices != null ? choices : List.of(); return this; }
        public Builder<T> choiceTranslationKeys(Map<String, String> translationKeys) {
            mChoiceTranslationKeys = translationKeys != null ? translationKeys : Map.of(); return this;
        }
        public Builder<T> keyScope(KeyScope scope) { mKeyScope = scope; return this; }
        public Builder<T> settingsVisibility(SettingsVisibility visibility) {
            mSettingsVisibility = visibility != null ? visibility : SettingsVisibility.VISIBLE;
            return this;
        }
        public Builder<T> normalize(UnaryOperator<T> normalizer) {
            mNormalizer = normalizer != null ? normalizer : UnaryOperator.identity(); return this;
        }
        public ConfigEntry<T> build() { return new ConfigEntry<>(this); }
    }
}
