package com.mine.geometry_node.client.ui.editor.datalibrary.library;

import com.mine.geometry_node.client.feature.datalibrary.DataLibraryRepository;
import com.mine.geometry_node.client.ui.components.integration.metadata.RepositoryDataDictionaryBindings;
import com.mine.geometry_node.client.ui.editor.datalibrary.inspector.DatabaseSelection;
import com.mine.geometry_node.client.ui.workspace.state.UiStateScopes;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import static com.mine.geometry_node.client.ui.editor.datalibrary.presentation.DatabasePresentation.tr;

/** Builds library inspector snapshots; the Area supplies only its current command availability. */
public final class DataLibraryInspector {
    private DataLibraryInspector() {}
    public static DatabaseSelection selection(DataLibraryRepository repository, DataLibraryView library,
            List<DataLibraryRepository.Entry> entries, String stateScope, BooleanSupplier commandsActive) {
        if (entries.isEmpty()) return null;
        if (entries.size() > 1) return new DatabaseSelection(tr("selected", entries.size()), tr("library"), "", 0,
                List.of(new DatabaseSelection.Field(tr("folder"), String.join("\n", entries.stream()
                        .map(entry -> repository.folderPath(entry.parentId())).distinct().toList()))), List.of(), List.of());
        var entry = entries.getFirst();
        String selectionScope = stateScope;
        String type = Component.translatable(entry.type().getTranslationKey()).getString();
        boolean registryValue = switch (entry.type()) {
            case ENTITY, ENTITY_TEMPLATE, ITEM, ITEM_STACK, BLOCK_STATE -> true;
            default -> false;
        };
        var metadata = List.of(new DatabaseSelection.Field(tr("type"), registryValue ? library.registryType(entry) : type),
                new DatabaseSelection.Field(tr("folder"), repository.folderPath(entry.parentId())),
                new DatabaseSelection.Field(tr("id"), entry.id().toString()));
        var sections = List.of(
                new DatabaseSelection.Section(tr("stored_value"), type, DatabaseSelection.Kind.VALUE,
                        List.of(new DatabaseSelection.Field("", DataLibraryValuePresentation.valueDetails(entry)))),
                new DatabaseSelection.Section(tr("entry_information"), "", DatabaseSelection.Kind.METADATA, metadata),
                new DatabaseSelection.Section(tr("tags"), "", DatabaseSelection.Kind.TAGS,
                        entry.tags().stream().map(tag -> new DatabaseSelection.Field("", tag)).toList(),
                        repository.canEditTags(entry) ? new DatabaseSelection.TagEditor(entry.id().toString(),
                                !repository.isUpdatePending(entry.id()),
                                repository.isUpdatePending(entry.id()) ? tr("tag_update_pending")
                                        : repository.tagUpdateMessage(entry.id()).isEmpty() ? ""
                                        : Component.translatable("geometry_node." + repository.tagUpdateMessage(entry.id())).getString(),
                                tag -> submitTagEdit(repository, library, commandsActive, entry, selectionScope, tag, true),
                                tag -> submitTagEdit(repository, library, commandsActive, entry, selectionScope, tag, false)) : null),
                new DatabaseSelection.Section(tr("node_reference"), tr("reference_hint"), DatabaseSelection.Kind.REFERENCE,
                        List.of(new DatabaseSelection.Field(tr("id"), entry.id().toString()))));
        return new DatabaseSelection(entry.key(), type + " · " + tr("shared_entry"),
                "", entry.type().getColor(), List.of(), List.of(), sections, preview(entry),
                new DatabaseSelection.DataEditor(selectionScope + ":library:" + entry.id(), entry.data().size(),
                        RepositoryDataDictionaryBindings.library(
                                repository, entry.id(), () -> Objects.equals(selectionScope, UiStateScopes.current()))));
    }
    private static void submitTagEdit(DataLibraryRepository repository, DataLibraryView library,
            BooleanSupplier active, DataLibraryRepository.Entry expected, String scope, String tag, boolean add) {
        if (!active.getAsBoolean() || !Objects.equals(scope, UiStateScopes.current()))
            throw new IllegalStateException("Inactive library tag editor");
        var selected = library.selectedEntries();
        if (selected.size() != 1 || !selected.getFirst().id().equals(expected.id()))
            throw new IllegalStateException("Stale library tag selection");
        if (add) repository.addTag(expected, tag); else repository.removeTag(expected, tag);
    }
    private static DatabaseSelection.Preview preview(DataLibraryRepository.Entry entry) {
        return switch (entry.type()) {
            case ENTITY -> entry.value() instanceof java.util.UUID id
                    ? new DatabaseSelection.EntityPreview(id, "", null) : null;
            case ENTITY_TEMPLATE -> {
                var template = EntityTemplateValue.from(entry.value());
                yield template.isEmpty() ? null : new DatabaseSelection.EntityPreview(null, "", template);
            }
            case ITEM, ITEM_STACK, BLOCK_STATE -> {
                var stack = DataLibraryValuePresentation.previewStack(entry);
                yield stack.isEmpty() ? null : new DatabaseSelection.ItemPreview(stack);
            }
            default -> null;
        };
    }
}
