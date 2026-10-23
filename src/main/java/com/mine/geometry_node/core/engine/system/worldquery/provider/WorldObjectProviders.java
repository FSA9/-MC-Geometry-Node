package com.mine.geometry_node.core.engine.system.worldquery.provider;

import java.util.*;

/** Register once during common initialization. Providers retain ownership of every queried service. */
public final class WorldObjectProviders {
    private static final Map<String, WorldObjectProvider> PROVIDERS = new LinkedHashMap<>();
    private WorldObjectProviders() {}
    public static synchronized void register(WorldObjectProvider provider) {
        if (PROVIDERS.size() >= 32) throw new IllegalStateException("World object provider limit exceeded");
        if (provider.id().isBlank() || provider.id().length() > 128) throw new IllegalArgumentException("Invalid provider ID");
        if (provider.coverage().length() > 128) throw new IllegalArgumentException("Provider coverage ID too long");
        if (PROVIDERS.putIfAbsent(provider.id(), provider) != null) throw new IllegalArgumentException("Duplicate world object provider");
    }
    public static synchronized List<WorldObjectProvider> all() { return List.copyOf(PROVIDERS.values()); }
}
