package com.mine.geometry_node.client.ui.components.integration.metadata;

import com.mine.geometry_node.client.feature.scopeddata.ClientScopedDataRepository;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Borrows one scoped-data session; the category owns it, embedded editors only borrow its events. */
public final class ScopedDataDictionaryBinding implements DataDictionaryBinding {
    private final ClientScopedDataRepository.Session session;
    private final String target;
    private final BooleanSupplier ownerCurrent, editable;

    public ScopedDataDictionaryBinding(ClientScopedDataRepository.Session session, String target,
                                       BooleanSupplier ownerCurrent, BooleanSupplier editable) {
        this.session = Objects.requireNonNull(session);
        this.target = Objects.requireNonNull(target);
        this.ownerCurrent = Objects.requireNonNull(ownerCurrent);
        this.editable = Objects.requireNonNull(editable);
    }
    private boolean current() { return session.current() && ownerCurrent.getAsBoolean(); }
    @Override public State snapshot() {
        if (!current()) return new State(Map.of(), false, false, false, false,
                message(ClientScopedDataRepository.PREFIX + "unavailable"));
        var data = session.data(target);
        String error = data.message().isEmpty() ? session.snapshot().message() : data.message();
        return new State(data.values(), data.loading(), data.available(), data.editable() && editable.getAsBoolean(),
                data.pending(), message(error));
    }
    @Override public void refresh() { if (current()) session.read(target, false); }
    @Override public void reload() { if (current()) session.read(target, true); }
    @Override public void apply(Map<String, Object> expected, Map<String, Object> replacement, Consumer<String> completion) {
        State state = snapshot();
        if (!state.available() || !state.editable() || state.loading() || state.saving()) {
            completion.accept(state.message().isEmpty() ? message(ClientScopedDataRepository.PREFIX + "unavailable") : state.message());
            return;
        }
        session.replace(target, expected, replacement, error -> completion.accept(message(error)));
    }
    @Override public void addChangeListener(Runnable listener) { session.addChangeListener(listener); }
    @Override public void removeChangeListener(Runnable listener) { session.removeChangeListener(listener); }
    public static String message(String key) {
        return key == null || key.isEmpty() ? "" : Component.translatable(key).getString();
    }
}
