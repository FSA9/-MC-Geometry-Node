package com.mine.geometry_node.client.ui.editor.database;

import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import com.mine.geometry_node.client.ui.components.integration.metadata.DataDictionaryBinding;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Immutable inspector presentation; mutations remain with the owning repository. */
public record DatabaseSelection(String title, String subtitle, String iconPath, int accent,
                                List<Field> fields, List<Action> actions, List<Section> sections,
                                Preview preview, DataEditor dataEditor) {
    public DatabaseSelection {
        fields = List.copyOf(fields); actions = List.copyOf(actions); sections = List.copyOf(sections);
    }
    public DatabaseSelection(String title, String subtitle, String iconPath, int accent,
                             List<Field> fields, List<Action> actions, List<Section> sections) {
        this(title, subtitle, iconPath, accent, fields, actions, sections, null, null);
    }
    public DatabaseSelection(String title, String subtitle, String iconPath, int accent,
                             List<Field> fields, List<Action> actions, List<Section> sections, Preview preview) {
        this(title, subtitle, iconPath, accent, fields, actions, sections, preview, null);
    }
    /** Rendering equality excludes callback identity; callers must dispatch actions against the latest model. */
    public boolean hasSamePresentation(DatabaseSelection other) {
        return other != null && Objects.equals(title, other.title) && Objects.equals(subtitle, other.subtitle)
                && Objects.equals(iconPath, other.iconPath) && accent == other.accent
                && Objects.equals(preview, other.preview)
                && (dataEditor == null ? other.dataEditor == null : dataEditor.hasSamePresentation(other.dataEditor))
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
        public boolean hasSamePresentation(Section other) {
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

    /** Async owner capability; the inspector never stores a second authoritative dictionary. */
    public record DataEditor(String target, int count, DataDictionaryBinding binding, boolean editable) {
        public DataEditor {
            Objects.requireNonNull(target);
            Objects.requireNonNull(binding);
            if (count < 0) throw new IllegalArgumentException("Negative data count");
        }
        public DataEditor(String target, int count, DataDictionaryBinding binding) {
            this(target, count, binding, true);
        }
        boolean hasSamePresentation(DataEditor other) {
            return other != null && target.equals(other.target) && count == other.count && editable == other.editable;
        }
    }

    /** Immutable preview source; rendering and live-world resolution stay in the shared preview component. */
    public sealed interface Preview permits EntityPreview, ItemPreview {}

    public record EntityPreview(UUID entityId, String dimension, EntityTemplateValue template) implements Preview {
        public EntityPreview {
            dimension = Objects.requireNonNullElse(dimension, "");
            if ((entityId == null) == (template == null) || template != null && template.isEmpty())
                throw new IllegalArgumentException("An entity preview requires one reference or nonempty template");
        }
    }

    /** Isolates mutable stacks; semantic equality avoids rebuilding unchanged native previews. */
    public record ItemPreview(ItemStack stack) implements Preview {
        public ItemPreview {
            Objects.requireNonNull(stack);
            if (stack.isEmpty()) throw new IllegalArgumentException("An item preview requires a nonempty stack");
            stack = stack.copy();
        }
        @Override public ItemStack stack() { return stack.copy(); }
        @Override public boolean equals(Object other) {
            return other instanceof ItemPreview item && ItemStack.matches(stack, item.stack);
        }
        @Override public int hashCode() { return 31 * stack.getItem().hashCode() + stack.getCount(); }
    }

}
