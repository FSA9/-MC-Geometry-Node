package com.mine.geometry_node.client.ui.components.integration.metadata;

import com.mine.geometry_node.client.feature.datalibrary.DataLibraryRepository;
import com.mine.geometry_node.client.feature.worldquery.ClientWorldObjectRepository;
import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataValuePolicy;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import com.mine.geometry_node.core.metadata.DataDictionary;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Bridges owner repositories to reusable asynchronous metadata views; never edits vanilla data. */
public final class RepositoryDataDictionaryBindings {
    private RepositoryDataDictionaryBindings() {}

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
        return worldBinding(session, expected, active, false);
    }

    private static DataDictionaryBinding worldBinding(ClientWorldObjectRepository.Session session,
            WorldObjectEntry expected, BooleanSupplier active, boolean owned) {
        return new DataDictionaryBinding() {
            private ClientWorldObjectRepository.Session editorSession;
            private boolean closed;
            private ClientWorldObjectRepository.Session source() {
                if (closed || !active.getAsBoolean()) return null;
                if (!owned) return session;
                if (editorSession == null) {
                    try { editorSession = session.forkDataSession(expected); }
                    catch (IllegalStateException stale) { return null; }
                }
                return editorSession;
            }
            @Override public DataDictionaryBinding openSession() {
                return worldBinding(session, expected, active, true);
            }
            @Override public State snapshot() {
                var source = source();
                if (source == null) return new State(Map.of(), false, false, false, false, "");
                var state = source.dataSnapshot(expected);
                boolean unread = state.available() && !state.fetched() && !state.loading() && state.message().isEmpty();
                return new State(state.values(), state.loading() || unread, state.available(), state.editable(),
                        state.pending(), localized(state.message()));
            }
            @Override public void refresh() {
                var source = source();
                if (source != null) source.requestData(expected);
            }
            @Override public void reload() {
                var source = source();
                if (source != null) source.refreshData(expected);
            }
            @Override public void apply(Map<String, Object> baseline, Map<String, Object> replacement,
                                        Consumer<String> completion) {
                var source = source();
                if (source == null) { completion.accept(message("unavailable")); return; }
                if (!source.replaceData(expected, baseline, replacement, result -> {
                    // The passive inspector has a separate query lifetime. Refresh its cached DATA
                    // after an edit without replacing its table query or coupling session ownership.
                    try {
                        if (owned && (result == null || result.isEmpty())) session.refreshData(expected);
                    } catch (RuntimeException refreshFailure) {
                        System.getLogger(RepositoryDataDictionaryBindings.class.getName()).log(System.Logger.Level.WARNING,
                                "Cannot refresh data preview after a completed edit", refreshFailure);
                    }
                    completion.accept(localized(result));
                }))
                    completion.accept(message("conflict"));
            }
            @Override public void addChangeListener(Runnable listener) {
                var source = source();
                if (source != null) source.addChangeListener(listener);
            }
            @Override public void removeChangeListener(Runnable listener) {
                if (!owned) session.removeChangeListener(listener);
                else if (editorSession != null) editorSession.removeChangeListener(listener);
            }
            @Override public void close() {
                if (!owned || closed) return;
                closed = true;
                if (editorSession != null) editorSession.close();
                editorSession = null;
            }
        };
    }

    private static String message(String suffix) { return localized("database.data_update_" + suffix); }
    private static String localized(String key) {
        return key == null || key.isEmpty() ? "" : Component.translatable("geometry_node." + key).getString();
    }
}
