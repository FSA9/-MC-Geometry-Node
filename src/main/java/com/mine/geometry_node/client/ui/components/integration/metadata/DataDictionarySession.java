package com.mine.geometry_node.client.ui.components.integration.metadata;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** One logical opening, independent of View attachment. Completed writes outlive window closure. */
final class DataDictionarySession implements AutoCloseable {
    record TypedFormState(String keyText, DataDictionaryDraft.Type type, String valueText,
                          boolean dirty, boolean preview, int keyStart, int keyEnd,
                          int valueStart, int valueEnd) {}

    private final DataDictionaryBinding binding;
    private final boolean ownsBinding;
    private final Consumer<Runnable> dispatch;
    private final Consumer<String> reportError;
    private final Runnable changed = () -> dispatchChange();
    // null identifies the single unfinished Add form. These are never included in a write.
    private final Map<String, TypedFormState> forms = new LinkedHashMap<>();
    private Runnable observer = () -> {};
    private DataDictionaryBinding.State state;
    private DataDictionaryDraft draft;
    private boolean started, closed, released, submitting, applying, recoverOnRead;
    private String error = "";

    DataDictionarySession(DataDictionaryBinding binding, boolean ownsBinding, Consumer<Runnable> dispatch,
                          Consumer<String> reportError) {
        this.binding = Objects.requireNonNull(binding);
        this.ownsBinding = ownsBinding;
        this.dispatch = Objects.requireNonNull(dispatch);
        this.reportError = Objects.requireNonNull(reportError);
        receive();
    }
    void observe(Runnable observer) { this.observer = Objects.requireNonNull(observer); }
    void start() {
        if (started || closed) return;
        started = true;
        binding.addChangeListener(changed);
        refreshCommitted();
    }
    private void dispatchChange() {
        dispatch.accept(() -> {
            if (released || applying) return;
            receive();
            if (!closed) observer.run();
        });
    }
    DataDictionaryBinding.State state() { return state; }
    DataDictionaryDraft draft() { return draft; }
    String error() { return error; }
    boolean submitting() { return submitting; }
    boolean pendingCompleted() { return submitting || draft != null && draft.dirty(); }
    boolean editable() {
        return !closed && !submitting && state.available() && state.editable() && !state.loading() && !state.saving();
    }
    TypedFormState form(String key) { return forms.get(key); }
    void remember(String key, TypedFormState form) { if (!closed) forms.put(key, form); }
    void forget(String key) { forms.remove(key); }
    TypedFormState orphanForm(String key) {
        TypedFormState form = forms.get(key);
        return key != null && form != null && form.dirty()
                && draft != null && !draft.values().containsKey(key) ? form : null;
    }
    /** Deleted server entries can still have a local unfinished form; keep it reachable, not committed. */
    List<String> entryKeys(String search, DataDictionaryDraft.Type filter) {
        if (draft == null) return List.of();
        List<String> keys = new ArrayList<>(DataDictionaryDraft.matching(draft.values(), search, filter));
        String needle = search.trim().toLowerCase(Locale.ROOT);
        forms.forEach((key, form) -> {
            if (orphanForm(key) != null && (filter == null || filter == form.type())
                    && (needle.isEmpty() || key.toLowerCase(Locale.ROOT).contains(needle)
                    || form.keyText().toLowerCase(Locale.ROOT).contains(needle)
                    || form.valueText().toLowerCase(Locale.ROOT).contains(needle))) keys.add(key);
        });
        return List.copyOf(keys);
    }
    boolean formDirty() { return forms.values().stream().anyMatch(TypedFormState::dirty); }
    void refreshCommitted() {
        if (closed) return;
        receive();
        observer.run();
    }
    boolean canReload() { return !closed && !submitting && (!pendingCompleted() || !error.isEmpty()); }
    void reload() {
        if (!canReload()) return;
        recoverOnRead = !error.isEmpty();
        try { binding.reload(); refreshCommitted(); }
        catch (RuntimeException failure) { recoverOnRead = false; fail(failure.toString()); observer.run(); }
    }
    void refresh() {
        if (closed) return;
        try { binding.refresh(); refreshCommitted(); }
        catch (RuntimeException failure) { fail(failure.toString()); observer.run(); }
    }
    private void receive() {
        state = binding.snapshot();
        if (state.available() && !state.loading() && !submitting
                && (!recoverOnRead || state.editable() && !state.saving() && state.message().isEmpty())
                && (recoverOnRead || !pendingCompleted())
                && (recoverOnRead || draft == null || !draft.values().equals(state.values()))) {
            forms.entrySet().removeIf(entry -> !entry.getValue().dirty()
                    && entry.getKey() != null && (draft == null
                    || !Objects.equals(draft.values().get(entry.getKey()), state.values().get(entry.getKey()))));
            draft = new DataDictionaryDraft(state.values());
            if (recoverOnRead) { error = ""; recoverOnRead = false; }
        }
    }
    /** Submit only completed operations. A true result means accepted/in flight or successful. */
    boolean submit() {
        if (closed || submitting) return false;
        if (draft == null || !draft.dirty()) return true;
        if (!editable()) return false;
        Map<String, Object> expected = draft.baseline();
        Map<String, Object> replacement = new LinkedHashMap<>(draft.values());
        submitting = true;
        error = "";
        boolean[] finished = {false};
        Consumer<String> completion = failure -> dispatch.accept(() -> {
            if (finished[0]) return;
            finished[0] = true;
            submitting = false;
            try {
                if (failure == null || failure.isEmpty()) {
                    draft = new DataDictionaryDraft(replacement);
                    error = "";
                    try { receive(); }
                    catch (RuntimeException snapshotFailure) { fail(snapshotFailure.toString()); }
                } else fail(failure);
            } finally {
                if (closed) release(); else observer.run();
            }
        });
        applying = true;
        try { binding.apply(expected, replacement, completion); }
        catch (RuntimeException failure) {
            if (finished[0]) throw failure;
            completion.accept(failure.toString());
        } finally { applying = false; }
        if (submitting && !closed) observer.run();
        return submitting || error.isEmpty();
    }
    private void fail(String failure) {
        error = failure;
        // Includes failures received after the last View is gone; no silent close-time loss.
        reportError.accept(failure);
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        forms.clear();
        observer = () -> {};
        if (!submitting) release();
    }
    private void release() {
        if (released) return;
        released = true;
        if (started) binding.removeChangeListener(changed);
        if (ownsBinding) binding.close();
        draft = null;
    }
}
