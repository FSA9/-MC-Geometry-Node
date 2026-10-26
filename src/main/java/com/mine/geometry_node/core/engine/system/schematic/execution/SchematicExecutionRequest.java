package com.mine.geometry_node.core.engine.system.schematic.execution;

import com.mine.geometry_node.core.engine.blueprint.runtime.wait.BlueprintExternalWaitRequest;
import com.mine.geometry_node.core.engine.system.schematic.SchematicData;

import java.util.Objects;

/** Immutable load parameters plus a server-thread task factory for one schematic action. */
public record SchematicExecutionRequest(
        String relativePath,
        boolean includeAir,
        TaskFactory taskFactory
) implements BlueprintExternalWaitRequest {
    public SchematicExecutionRequest {
        relativePath = Objects.requireNonNull(relativePath, "relativePath");
        taskFactory = Objects.requireNonNull(taskFactory, "taskFactory");
    }

    @FunctionalInterface
    public interface TaskFactory {
        TickTask create(SchematicData data) throws Exception;
    }

    public interface TickTask {
        /** Performs at most {@code operationBudget} units of server-thread work. */
        TickResult tick(int operationBudget) throws Exception;

        default void cancel() {
        }

        default void complete(BlueprintResultSink resultSink) {
        }
    }

    public record TickResult(int operations, boolean complete) {
        public TickResult {
            if (operations < 0) throw new IllegalArgumentException("operations cannot be negative");
        }
    }

    @FunctionalInterface
    public interface BlueprintResultSink {
        void set(String portName, Object value);
    }
}
