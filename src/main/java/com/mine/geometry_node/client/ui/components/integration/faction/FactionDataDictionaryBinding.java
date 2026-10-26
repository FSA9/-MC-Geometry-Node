package com.mine.geometry_node.client.ui.components.integration.faction;

import com.mine.geometry_node.client.feature.faction.ClientFactionRepository;
import com.mine.geometry_node.client.ui.components.integration.metadata.DataDictionaryBinding;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Borrowed faction capability. Presentation lifetime never cancels an accepted command. */
public final class FactionDataDictionaryBinding implements DataDictionaryBinding {
    private final ClientFactionRepository repository;
    private final UUID id;
    private final long generation;
    private final BooleanSupplier ownerCurrent, editable;
    private final Supplier<HolderLookup.Provider> registries;

    public FactionDataDictionaryBinding(ClientFactionRepository repository, UUID id,
                                        BooleanSupplier ownerCurrent, BooleanSupplier editable) {
        this(repository, id, ownerCurrent, editable, () -> {
            var minecraft = Minecraft.getInstance();
            return minecraft == null || minecraft.level == null ? null : minecraft.level.registryAccess();
        });
    }

    public FactionDataDictionaryBinding(ClientFactionRepository repository, UUID id,
                                        BooleanSupplier ownerCurrent, BooleanSupplier editable,
                                        Supplier<HolderLookup.Provider> registries) {
        this.repository = Objects.requireNonNull(repository);
        this.id = Objects.requireNonNull(id);
        this.ownerCurrent = Objects.requireNonNull(ownerCurrent);
        this.editable = Objects.requireNonNull(editable);
        this.registries = Objects.requireNonNull(registries);
        generation = repository.generation();
    }

    private boolean current() { return repository.generation() == generation && ownerCurrent.getAsBoolean(); }

    @Override public State snapshot() {
        var faction = current() && repository.loaded() ? repository.snapshot().find(id).orElse(null) : null;
        boolean available = faction != null;
        Map<String, Object> values;
        try { values = available ? faction.metadata().data(registries.get()) : Map.of(); }
        catch (RuntimeException unavailable) {
            // Encoded owner metadata remains authoritative. Never offer an empty replacement for
            // a dictionary that this client cannot decode (missing codec or registry, for example).
            return new State(Map.of(), false, false, false, false, FactionUi.tr("error.data_unavailable"));
        }
        return new State(values,
                current() && !repository.loaded() && repository.loading(), available,
                available && editable.getAsBoolean() && !repository.loading(),
                available && repository.loading(), current() ? FactionUi.errorMessage(repository.error()) : "");
    }

    @Override public void refresh() {
        if (current() && !repository.loaded() && !repository.loading()) repository.refresh();
    }

    @Override public void reload() {
        if (current() && !repository.loading()) repository.refresh();
    }

    @Override public void apply(Map<String, Object> expected, Map<String, Object> replacement,
                                Consumer<String> completion) {
        State state = snapshot();
        if (!state.available() || !state.editable() || state.loading() || state.saving()) {
            completion.accept(state.message().isEmpty() ? FactionUi.tr("error.faction_unavailable") : state.message());
            return;
        }
        CompletableFuture<Void> command;
        try { command = repository.replaceData(id, expected, replacement); }
        catch (RuntimeException failure) { completion.accept(FactionUi.errorMessage(failure)); return; }
        command.whenComplete((ignored, failure) -> completion.accept(FactionUi.errorMessage(failure)));
    }

    @Override public void addChangeListener(Runnable listener) { repository.addChangeListener(listener); }
    @Override public void removeChangeListener(Runnable listener) { repository.removeChangeListener(listener); }
}
