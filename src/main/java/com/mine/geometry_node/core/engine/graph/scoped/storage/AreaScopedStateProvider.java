package com.mine.geometry_node.core.engine.graph.scoped.storage;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaRef;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResource;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResourceStore;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateAccessException;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateEntry;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateProvider;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateServerConfig;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/** AREA provider backed by one live Area incarnation. */
public final class AreaScopedStateProvider implements ScopedStateProvider {
    private final ServerLevel contextLevel;
    private final AreaRef area;
    private final ScopedStateNamespace namespace;
    private final int maxEntries;

    public AreaScopedStateProvider(ServerLevel contextLevel, AreaRef area,
                                   ScopedStateNamespace namespace) {
        this.contextLevel = Objects.requireNonNull(contextLevel, "contextLevel");
        this.area = Objects.requireNonNull(area, "area");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.maxEntries = ScopedStateServerConfig.maxEntries(namespace);
    }

    @Override public ScopedStateScope scope() { return ScopedStateScope.AREA; }
    @Override public String identity() {
        return area.address().dimension().identifier() + "/" + area.address().id()
                + "@" + area.incarnation();
    }
    @Override public boolean available() { return resourceOrNull() != null; }

    @Override
    public @Nullable ScopedStateEntry get(String name) {
        return resource().scopedState().get(namespace, name, contextLevel.registryAccess(), location(name));
    }

    @Override
    public void put(String name, Object value) {
        resource().scopedState().put(namespace, name, value, maxEntries,
                contextLevel.registryAccess(), location(""), this::notifyLimit);
    }

    @Override public boolean remove(String name) {
        return resource().scopedState().remove(namespace, name);
    }
    @Override public boolean hasRecord(String name) {
        return get(name) != null;
    }
    @Override public long revision() { return resource().scopedState().revision(namespace); }
    @Override public int size() { return resource().scopedState().size(namespace); }
    @Override public Map<String, ScopedStateEntry> entries(int limit) {
        return resource().scopedState().entries(namespace, contextLevel.registryAccess(),
                location(""), limit);
    }

    private String location(String name) {
        return "area/" + identity() + "/" + namespace.serializedName() + "/" + name;
    }

    private @Nullable AreaResource resourceOrNull() {
        return AreaResourceStore.INSTANCE.get(contextLevel.getServer(), area);
    }

    private AreaResource resource() {
        AreaResource resource = resourceOrNull();
        if (resource == null) {
            throw new ScopedStateAccessException("Area scoped-state target is no longer available: "
                    + identity());
        }
        return resource;
    }

    private void notifyLimit() {
        ScopedStateLimitNotifier.notifyLimit(contextLevel, namespace, ScopedStateScope.AREA,
                identity(), maxEntries);
    }
}
