package com.mine.geometry_node.client.ui.editor.database.metadata;

import com.mine.geometry_node.client.datalibrary.DataLibraryRepository;
import com.mine.geometry_node.client.worldquery.ClientWorldObjectRepository;
import com.mine.geometry_node.client.ui.components.integration.metadata.DataDictionaryBinding;
import com.mine.geometry_node.core.engine.metadata.GraphMetadataValuePolicy;
import com.mine.geometry_node.core.engine.system.worldquery.WorldObjectDataEdits;
import com.mine.geometry_node.core.engine.system.worldquery.WorldObjectEntry;
import com.mine.geometry_node.core.metadata.DataDictionary;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Bridges owner repositories to reusable asynchronous metadata views; never edits vanilla data. */
public final class DatabaseDataBindings {
    private DatabaseDataBindings() {}

    public static DataDictionaryBinding library(DataLibraryRepository repository, UUID id, BooleanSupplier active) {
        Objects.requireNonNull(repository); Objects.requireNonNull(id); Objects.requireNonNull(active);
        return new DataDictionaryBinding() {
            @Override public State snapshot() {
                var entry = active.getAsBoolean() ? repository.findEntry(id) : null;
                boolean available = entry != null && repository.hasLoadedSnapshot();
                return new State(available ? entry.data() : Map.of(),
                        active.getAsBoolean() && !repository.hasLoadedSnapshot(), available,
                        available && repository.canEditData(entry), repository.isUpdatePending(id),
                        localized(repository.dataUpdateMessage(id)));
            }
            @Override public void refresh() {
                if (active.getAsBoolean() && !repository.hasLoadedSnapshot()) repository.refresh(null);
            }
            @Override public void reload() {
                if (active.getAsBoolean() && !repository.isUpdatePending(id)) repository.refresh(null);
            }
            @Override public void apply(Map<String, Object> expected, Map<String, Object> replacement,
                                        Consumer<String> completion) {
                if (!active.getAsBoolean()) { completion.accept(message("unavailable")); return; }
                var live = repository.findEntry(id);
                if (live == null || !repository.canEditData(live)) { completion.accept(message("unavailable")); return; }
                try {
                    if (!DataDictionary.equivalent(live.data(), expected, GraphMetadataValuePolicy.of(null))) {
                        completion.accept(message("conflict")); return;
                    }
                    repository.replaceData(live, replacement, result -> completion.accept(localized(result)));
                } catch (IllegalArgumentException invalid) { completion.accept(message("invalid")); }
                catch (IllegalStateException stale) { completion.accept(message("conflict")); }
            }
            @Override public void addChangeListener(Runnable listener) { repository.addChangeListener(listener); }
            @Override public void removeChangeListener(Runnable listener) { repository.removeChangeListener(listener); }
        };
    }

    public static DataDictionaryBinding world(ClientWorldObjectRepository.Session session, WorldObjectEntry expected,
                                               BooleanSupplier active) {
        Objects.requireNonNull(session); Objects.requireNonNull(expected); Objects.requireNonNull(active);
        return new DataDictionaryBinding() {
            private WorldObjectEntry current() {
                if (!active.getAsBoolean()) return null;
                String token = expected.references().get(WorldObjectDataEdits.TARGET_REFERENCE);
                return session.snapshot().entries().stream().filter(row -> row.key().equals(expected.key())
                        && token != null && token.equals(row.references().get(WorldObjectDataEdits.TARGET_REFERENCE)))
                        .findFirst().orElse(null);
            }
            @Override public State snapshot() {
                var row = current();
                if (row == null) return new State(Map.of(), false, false, false, false, "");
                var state = session.dataSnapshot(row);
                // A capability can be advertised before its first on-demand read has been sent.
                boolean unread = state.available() && !state.editable() && !state.loading() && state.message().isEmpty();
                return new State(state.values(), state.loading() || unread, state.available(), state.editable(),
                        state.pending(), localized(state.message()));
            }
            @Override public void refresh() {
                var row = current();
                if (row != null) session.requestData(row);
            }
            @Override public void reload() {
                var row = current();
                if (row != null) session.refreshData(row);
            }
            @Override public void apply(Map<String, Object> baseline, Map<String, Object> replacement,
                                        Consumer<String> completion) {
                var row = current();
                if (row == null) { completion.accept(message("unavailable")); return; }
                if (!session.replaceData(row, baseline, replacement, result -> completion.accept(localized(result))))
                    completion.accept(message("conflict"));
            }
            @Override public void addChangeListener(Runnable listener) { session.addChangeListener(listener); }
            @Override public void removeChangeListener(Runnable listener) { session.removeChangeListener(listener); }
        };
    }

    private static String message(String suffix) { return localized("database.data_update_" + suffix); }
    private static String localized(String key) {
        return key == null || key.isEmpty() ? "" : Component.translatable("geometry_node." + key).getString();
    }
}
