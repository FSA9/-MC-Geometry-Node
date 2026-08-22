package com.mine.geometry_node.client.ai.command;

import java.util.Objects;

/** Runtime-neutral invocation context. Concrete adapters own the target implementation. */
public record CommandInvocationContext(CommandOrigin origin, CommandTarget target, CancellationToken cancellation) {
    public enum CommandOrigin { CLI, AGENT }

    public interface CommandTarget {
        boolean hasGraph();
    }

    @FunctionalInterface
    public interface CancellationToken {
        CancellationToken NONE = () -> false;
        boolean isCancelled();
    }

    public CommandInvocationContext {
        origin = Objects.requireNonNull(origin, "origin");
        cancellation = cancellation == null ? CancellationToken.NONE : cancellation;
    }

    public boolean hasGraph() {
        return target != null && target.hasGraph();
    }
}
