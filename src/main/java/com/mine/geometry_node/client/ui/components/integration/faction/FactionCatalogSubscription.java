package com.mine.geometry_node.client.ui.components.integration.faction;

import com.mine.geometry_node.client.feature.faction.ClientFactionRepository;
import icyllis.modernui.view.View;
import java.util.Objects;

/** UI-scoped observation of the connection-owned catalog; hiding a page cancels only presentation. */
public final class FactionCatalogSubscription implements AutoCloseable {
    private final View owner;
    private final ClientFactionRepository repository;
    private final Runnable render;
    private final Runnable task = this::deliver;
    private final Runnable changed = this::enqueue;
    private volatile boolean active;

    public FactionCatalogSubscription(View owner, ClientFactionRepository repository, Runnable render) {
        this.owner = Objects.requireNonNull(owner); this.repository = Objects.requireNonNull(repository);
        this.render = Objects.requireNonNull(render);
    }
    public void open() {
        if (active) return;
        active = true; repository.addChangeListener(changed); render.run(); repository.refresh();
    }
    private void enqueue() { if (active) { owner.removeCallbacks(task); owner.post(task); } }
    private void deliver() { if (active) render.run(); }
    @Override public void close() {
        active = false; repository.removeChangeListener(changed); owner.removeCallbacks(task);
    }
}
