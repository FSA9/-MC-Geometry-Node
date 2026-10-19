package com.mine.geometry_node.client.ui.editor.database;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Immutable inspector presentation; mutations remain with the owning repository. */
public record DatabaseSelection(String title, String subtitle, String iconPath, int accent,
                                List<Field> fields, List<Action> actions, List<Section> sections) {
    public DatabaseSelection {
        fields = List.copyOf(fields); actions = List.copyOf(actions); sections = List.copyOf(sections);
    }
    /** Rendering equality excludes callback identity; callers must dispatch actions against the latest model. */
    public boolean hasSamePresentation(DatabaseSelection other) {
        return other != null && Objects.equals(title, other.title) && Objects.equals(subtitle, other.subtitle)
                && Objects.equals(iconPath, other.iconPath) && accent == other.accent
                && fields.equals(other.fields) && sameSections(other.sections)
                && actions.stream().map(Action::label).toList().equals(other.actions.stream().map(Action::label).toList());
    }
    private boolean sameSections(List<Section> other) {
        if (sections.size() != other.size()) return false;
        for (int i = 0; i < sections.size(); i++) if (!sections.get(i).hasSamePresentation(other.get(i))) return false;
        return true;
    }
    public enum Kind { METADATA, POSITION, TAGS, VALUE, SOURCE, REFERENCE }
    public record Section(String title, String subtitle, Kind kind, List<Field> fields, TagEditor tagEditor) {
        public Section { fields = List.copyOf(fields); }
        public Section(String title, String subtitle, Kind kind, List<Field> fields) {
            this(title, subtitle, kind, fields, null);
        }
        boolean hasSamePresentation(Section other) {
            return Objects.equals(title, other.title) && Objects.equals(subtitle, other.subtitle)
                    && kind == other.kind && fields.equals(other.fields)
                    && (tagEditor == null ? other.tagEditor == null : tagEditor.hasSamePresentation(other.tagEditor));
        }
    }
    /** Commands are supplied by the repository owner; renderers dispatch against the latest selection. */
    public record TagEditor(String target, boolean enabled, String message,
                            Consumer<String> add, Consumer<String> remove) {
        public TagEditor {
            Objects.requireNonNull(target); Objects.requireNonNull(message);
            Objects.requireNonNull(add); Objects.requireNonNull(remove);
        }
        boolean hasSamePresentation(TagEditor other) {
            return other != null && target.equals(other.target) && enabled == other.enabled && message.equals(other.message);
        }
    }
    public record Field(String label, String value) {}
    public record Action(String label, Runnable run) {}
}
