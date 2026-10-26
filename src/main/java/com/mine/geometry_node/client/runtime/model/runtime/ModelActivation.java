package com.mine.geometry_node.client.runtime.model.runtime;

/** One owner incarnation's activation sequence. Invalidated tickets can never become current again. */
final class ModelActivation {
    private Ticket current;

    Ticket begin() {
        invalidate();
        return current = new Ticket();
    }

    void invalidate() {
        if (current != null) current.valid = false;
        current = null;
    }

    boolean current(Ticket ticket) { return current == ticket && ticket.valid(); }

    static final class Ticket {
        private volatile boolean valid = true;
        boolean valid() { return valid; }
    }
}
