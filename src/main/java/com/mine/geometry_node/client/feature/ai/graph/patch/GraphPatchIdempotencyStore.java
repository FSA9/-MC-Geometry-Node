package com.mine.geometry_node.client.feature.ai.graph.patch;

import com.mine.geometry_node.client.feature.ai.command.CommandResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Run-scoped GraphPatch idempotency state shared by every dynamically resolved viewport target. */
public final class GraphPatchIdempotencyStore {
    public static final int MAX_KEYS = 1_024;

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public synchronized Reservation reserve(String key, String patchHash) {
        Entry existing = entries.get(key);
        if (existing != null) {
            return new Reservation(existing, false, existing.patchHash.equals(patchHash), false);
        }
        if (entries.size() >= MAX_KEYS) {
            return new Reservation(null, false, false, true);
        }
        Entry created = new Entry(patchHash);
        entries.put(key, created);
        return new Reservation(created, true, true, false);
    }

    public void complete(Entry entry, CommandResult result) {
        entry.complete(result);
    }

    public record Reservation(Entry entry, boolean owner, boolean hashMatches, boolean limitReached) {}

    public enum State {
        PENDING,
        PLANNING,
        COMMITTING,
        UNKNOWN,
        COMPLETED
    }

    public static final class Entry {
        private final String patchHash;
        private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
        private final CompletableFuture<CommandResult> result = new CompletableFuture<>();

        private Entry(String patchHash) {
            this.patchHash = patchHash;
        }

        public String patchHash() {
            return patchHash;
        }

        public State state() {
            return state.get();
        }

        public CompletableFuture<CommandResult> result() {
            return result;
        }

        public void beginPlanning() {
            if (!state.compareAndSet(State.PENDING, State.PLANNING)) {
                throw new IllegalStateException("GraphPatch transaction is already owned");
            }
        }

        public void beginCommit() {
            if (!state.compareAndSet(State.PLANNING, State.COMMITTING)) {
                throw new IllegalStateException("GraphPatch transaction cannot enter commit from " + state.get());
            }
        }

        public void markUnknown() {
            state.compareAndSet(State.COMMITTING, State.UNKNOWN);
        }

        private void complete(CommandResult value) {
            if (result.complete(value)) state.set(State.COMPLETED);
        }
    }
}
