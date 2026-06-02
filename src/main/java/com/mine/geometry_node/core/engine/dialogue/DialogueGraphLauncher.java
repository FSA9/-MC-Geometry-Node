package com.mine.geometry_node.core.engine.dialogue;

import com.mine.geometry_node.core.engine.graph.runtime.GraphExecutionHandle;
import org.jetbrains.annotations.Nullable;

/**
 * Backend hook responsible for connecting dialogue sessions to graph execution.
 * Full execution is intentionally left for later runtime work.
 */
public interface DialogueGraphLauncher {

    LaunchResult launch(DialogueSession session);

    LaunchResult choose(DialogueSession session, DialogueChoicePayload choice);

    class LaunchResult {
        private final boolean started;
        @Nullable
        private final DialoguePagePayload page;
        @Nullable
        private final GraphExecutionHandle executionHandle;

        public LaunchResult(boolean started, @Nullable DialoguePagePayload page, @Nullable GraphExecutionHandle executionHandle) {
            this.started = started;
            this.page = page;
            this.executionHandle = executionHandle;
        }

        public static LaunchResult empty() {
            return new LaunchResult(false, null, null);
        }

        public static LaunchResult page(DialoguePagePayload page) {
            return new LaunchResult(true, page, null);
        }

        public boolean isStarted() {
            return started;
        }

        @Nullable
        public DialoguePagePayload getPage() {
            return page;
        }

        @Nullable
        public GraphExecutionHandle getExecutionHandle() {
            return executionHandle;
        }
    }
}
