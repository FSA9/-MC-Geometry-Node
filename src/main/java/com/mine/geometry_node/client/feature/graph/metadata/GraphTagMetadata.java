package com.mine.geometry_node.client.feature.graph.metadata;

import com.mine.geometry_node.client.feature.graph.persistence.GraphTagIO;
import com.mine.geometry_node.client.feature.graph.persistence.file.GraphFileReference;
import com.mine.geometry_node.client.history.HistoryState;
import com.mine.geometry_node.client.feature.graph.document.GraphSession;
import com.mine.geometry_node.core.metadata.MetadataCapabilities;
import com.mine.geometry_node.core.metadata.MetadataStore;
import com.mine.geometry_node.core.metadata.TagAccess;
import com.mine.geometry_node.core.metadata.TagPolicy;
import com.mine.geometry_node.core.metadata.TagSet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Tag-only owner bindings. Session bindings are created and used on the editor's owning thread. */
public final class GraphTagMetadata {
    public static final String SESSION = "geometry_node:graph_asset_session";
    public static final String FILE = "geometry_node:graph_asset_file";

    private GraphTagMetadata() {}

    public static void register(MetadataCapabilities registry) {
        registry.register(SESSION, SessionTarget.class, Set.of(MetadataCapabilities.Capability.TAG),
                target -> new MetadataCapabilities.Access(target.tags(), null));
        registry.register(FILE, FileTarget.class, Set.of(MetadataCapabilities.Capability.TAG),
                target -> new MetadataCapabilities.Access(target.tags(), null));
    }

    /**
     * An open document and its history. Construct on the actual editor owner thread: this binding
     * records the caller's thread and enforces confinement, rather than discovering editor ownership.
     */
    public static final class SessionTarget implements MetadataStore<List<String>> {
        private final GraphSession session;
        private final Thread ownerThread;

        public SessionTarget(GraphSession session) {
            this.session = Objects.requireNonNull(session, "session");
            ownerThread = Thread.currentThread();
        }

        public TagAccess tags() { return new TagAccess(this, TagPolicy.GRAPH_LEGACY); }

        private void requireOwner() {
            if (Thread.currentThread() != ownerThread) {
                throw new IllegalStateException("Graph tags must be accessed on their editor owner thread");
            }
            session.fileReference().requireActivePath();
            if (session.editorContext.getCommandManager().historyState().phase() == HistoryState.Phase.CLOSED) {
                throw new IllegalStateException("Graph session is closed");
            }
        }

        @Override public List<String> read() {
            requireOwner();
            return TagSet.normalize(session.editorContext.getGraph().tags, TagPolicy.GRAPH_LEGACY);
        }

        @Override public boolean update(UnaryOperator<List<String>> transformation) {
            Objects.requireNonNull(transformation, "transformation");
            requireOwner();
            var commands = session.editorContext.getCommandManager();
            if (!commands.canSave()) throw new IllegalStateException("Graph owner is busy");
            List<String> before = read();
            List<String> after = commands.prepareEdit(() -> TagSet.normalize(
                    Objects.requireNonNull(transformation.apply(before), "tags"), TagPolicy.GRAPH_LEGACY));
            // Preserve raw legacy lists on semantic no-ops, with no history operation or notification.
            if (before.equals(after)) return false;
            return commands.execute(() -> {
                requireOwner();
                session.editorContext.getGraph().tags = new ArrayList<>(after);
                session.editorContext.notifyGraphMetadataChanged();
            });
        }
    }

    /** Closed-file owner. Open files must instead use a SessionTarget; claims are checked under lock. */
    public record FileTarget(GraphFileReference reference) implements MetadataStore<List<String>> {
        public FileTarget {
            Objects.requireNonNull(reference, "reference");
        }

        public TagAccess tags() { return new TagAccess(this, TagPolicy.GRAPH_LEGACY); }

        @Override public List<String> read() {
            try { return GraphTagIO.readUnclaimedTags(reference); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }

        @Override public boolean update(UnaryOperator<List<String>> transformation) {
            try { return GraphTagIO.updateTags(reference, transformation); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }
}
