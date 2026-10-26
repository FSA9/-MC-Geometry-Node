package com.mine.geometry_node.client.ui.components.integration.entity.editor.graph;

import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Host-owned graph picking and document routing for one entity graph page. */
public interface EntityGraphActions extends AutoCloseable {
    /** A validated remote graph file; the host rejects folders, local files and non-graph assets. */
    record Selection(GraphBindingKey key, String name, String type, List<String> tags) {
        public Selection {
            Objects.requireNonNull(key, "key");
            name = Objects.requireNonNullElse(name, "");
            type = Objects.requireNonNullElse(type, "");
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    enum Outcome { OPENED, NO_EDITOR, INVALID_ID, CONNECTION_CHANGED, LOAD_FAILED, DOWNLOAD_FAILED }

    @FunctionalInterface
    interface Factory {
        EntityGraphActions create(ViewGroup owner, UiTheme theme);
    }

    /** Callbacks run on the UI thread; cancellation produces no selection. */
    void chooseRemote(View anchor, String title, Consumer<Selection> selected);

    /**
     * Open in a destination owned by the page's host. Recheck host and connection before routing;
     * only the latest request may route a document or report its outcome, on the UI thread.
     */
    void openRemote(String id, Consumer<Outcome> completion);

    /** Cancel pending callbacks and release host resources when the page is disposed. */
    @Override void close();

    static EntityGraphActions unavailable() {
        return new EntityGraphActions() {
            private boolean closed;
            @Override public void chooseRemote(View anchor, String title, Consumer<Selection> selected) {}
            @Override public void openRemote(String id, Consumer<Outcome> completion) {
                if (!closed) completion.accept(Outcome.NO_EDITOR);
            }
            @Override public void close() { closed = true; }
        };
    }
}
