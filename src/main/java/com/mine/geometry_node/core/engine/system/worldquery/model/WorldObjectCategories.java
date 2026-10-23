package com.mine.geometry_node.core.engine.system.worldquery.model;

import java.util.*;
import java.util.function.BiPredicate;

/** Presentation categories combine authored and runtime providers without replacing their identities. */
public final class WorldObjectCategories {
    public record Category(String id, String labelKey, Set<String> providers, BiPredicate<String, String> accepts) {
        public Category { providers = Set.copyOf(providers); Objects.requireNonNull(accepts); }
        public boolean matches(WorldObjectEntry entry) { return accepts.test(entry.provider(), entry.type()); }
    }
    private static final Map<String, Category> CATEGORIES = new LinkedHashMap<>();
    static {
        builtIn("geometry", Set.of("scene", "geometry"), (p,t) -> t.startsWith("geometry/"));
        builtIn("collision", Set.of("scene", "collision"), (p,t) -> t.startsWith("collision/") || t.equals("geometry_collision"));
        builtIn("area", Set.of("scene", "area"), (p,t) -> t.startsWith("area/"));
        builtIn("light", Set.of("scene", "light"), (p,t) -> t.contains("light"));
        builtIn("model", Set.of("collision"), (p,t) -> t.equals("model_collision"));
        builtIn("projection", Set.of("projection"), (p,t) -> p.equals("projection"));
        builtIn("interaction", Set.of("native"), (p,t) -> t.equals("minecraft:interaction"));
        builtIn("entity", Set.of("native"), (p,t) -> !t.equals("minecraft:interaction"));
        builtIn("schematic", Set.of("schematic"), (p,t) -> p.equals("schematic"));
        builtIn("force_field", Set.of("force_field"), (p,t) -> p.equals("force_field"));
    }
    private WorldObjectCategories() {}
    private static void builtIn(String id, Set<String> providers, BiPredicate<String,String> accepts) {
        register(new Category(id, "geometry_node.database.category." + id, providers,
                (p,t) -> providers.contains(p) && accepts.test(p,t)));
    }
    public static synchronized void register(Category category) {
        if (CATEGORIES.putIfAbsent(category.id(), category) != null)
            throw new IllegalArgumentException("Duplicate database category: " + category.id());
    }
    public static synchronized List<Category> all() { return List.copyOf(CATEGORIES.values()); }
    public static synchronized Category forEntry(WorldObjectEntry entry) {
        for (Category category : CATEGORIES.values()) if (category.matches(entry)) return category;
        return null;
    }
}
