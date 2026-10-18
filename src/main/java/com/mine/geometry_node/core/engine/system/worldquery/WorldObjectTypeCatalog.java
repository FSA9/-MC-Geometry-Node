package com.mine.geometry_node.core.engine.system.worldquery;

import java.util.*;

/** Bounded display metadata, independent of object collection and its completeness. */
final class WorldObjectTypeCatalog {
    record Result(List<WorldObjectType> types, boolean partial, List<String> failures) {}
    private WorldObjectTypeCatalog() {}
    static Result collect(List<WorldObjectProvider> providers, WorldObjectQuery query) {
        Map<String, List<WorldObjectType>> catalogs = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        for (var provider : providers) {
            try { catalogs.put(provider.id(), Objects.requireNonNull(provider.types())); }
            catch (RuntimeException failure) { failures.add(provider.id() + ":type_metadata_failed"); }
        }
        class Budget {
            final Map<String, WorldObjectType> types = new LinkedHashMap<>();
            int text;
            boolean partial = !failures.isEmpty();
            boolean add(String provider, WorldObjectType type) {
                if (type == null || !provider.equals(type.provider())) { partial = true; return true; }
                String key = WorldObjectEntry.key(provider, "", type.id());
                if (types.containsKey(key)) return true;
                int size = type.provider().length() + type.id().length() + type.labelKey().length();
                if (types.size() >= WorldObjectPage.MAX_METADATA || text + size > 32_768) {
                    partial = true; return false;
                }
                types.put(key, type); text += size; return true;
            }
        }
        Budget budget = new Budget();
        // At most 32 providers: one real declared type each fits both catalog budgets.
        for (var catalog : catalogs.entrySet()) {
            for (var type : catalog.getValue()) {
                budget.add(catalog.getKey(), type);
                if (type != null && catalog.getKey().equals(type.provider())) break;
            }
        }
        var prioritized = new ArrayList<>(providers);
        prioritized.sort(Comparator.comparing((WorldObjectProvider p) ->
                !query.providers().isEmpty() && !query.providers().contains(p.id()))
                .thenComparing(p -> p.id().equals("native")));
        for (var provider : prioritized) {
            for (var type : catalogs.getOrDefault(provider.id(), List.of()))
                if (!budget.add(provider.id(), type)) break;
        }
        return new Result(List.copyOf(budget.types.values()), budget.partial, List.copyOf(failures));
    }
}
