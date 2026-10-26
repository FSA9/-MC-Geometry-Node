package com.mine.geometry_node.client.runtime.model.render.bvh;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Render-thread registration groups; no Iris pipeline objects are retained. */
final class ModelSeusProgramRegistry {
    private record Program(Scope owner, boolean primary) {}
    private final Map<Integer, Program> programs = new HashMap<>();
    private Scope current;

    Scope begin() {
        current = new Scope(current);
        return current;
    }

    void register(int program, boolean primary) {
        remove(program);
        if (current == null) return;
        programs.put(program, new Program(current, primary));
        current.programs.add(program);
        if (primary) current.primaryCount++;
        else current.directCount++;
    }

    boolean ready(int program) {
        Program registered = programs.get(program);
        return registered != null && registered.owner.committed
                && registered.owner.primaryCount > 0 && registered.owner.directCount > 0;
    }

    void remove(int program) {
        Program removed = programs.remove(program);
        if (removed == null) return;
        removed.owner.programs.remove(program);
        if (removed.primary) removed.owner.primaryCount--;
        else removed.owner.directCount--;
    }

    int size() { return programs.size(); }

    final class Scope implements AutoCloseable {
        private Scope parent;
        private final Set<Integer> programs = new HashSet<>();
        private int primaryCount, directCount;
        private boolean committed, closed;

        private Scope(Scope parent) { this.parent = parent; }

        void commit() {
            if (closed || current != this) throw new IllegalStateException("pipeline creation scope is not current");
            committed = true;
        }

        @Override public void close() {
            if (closed) return;
            if (current != this) throw new IllegalStateException("pipeline creation scopes must close in order");
            current = parent;
            parent = null;
            closed = true;
            if (!committed) {
                for (int program : Set.copyOf(programs)) remove(program);
            }
        }
    }
}
