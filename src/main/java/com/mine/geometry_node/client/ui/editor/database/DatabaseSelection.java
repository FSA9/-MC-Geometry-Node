package com.mine.geometry_node.client.ui.editor.database;

import java.util.List;
import java.util.Objects;

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
                && fields.equals(other.fields) && sections.equals(other.sections)
                && actions.stream().map(Action::label).toList().equals(other.actions.stream().map(Action::label).toList());
    }
    public enum Kind { METADATA, POSITION, TAGS, VALUE, SOURCE, REFERENCE }
    public record Section(String title, String subtitle, Kind kind, List<Field> fields) {
        public Section { fields = List.copyOf(fields); }
    }
    public record Field(String label, String value) {}
    public record Action(String label, Runnable run) {}
}
