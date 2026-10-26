package com.mine.geometry_node.client.ui.components.integration.entity.appearance;

import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceSpec;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceVariants;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Picker draft only: inspecting a candidate never changes the caller's spec. */
final class AppearanceSelection {
    record Option(String id, String name) {
        String namespace() { return id.substring(0, id.indexOf(':')); }
        boolean matches(String query, String namespace) {
            return (namespace.isEmpty() || namespace().equals(namespace))
                    && (id.toLowerCase(Locale.ROOT).contains(query) || name.toLowerCase(Locale.ROOT).contains(query));
        }
    }

    private final String source;
    private final EntityAppearanceSpec current;
    private final String currentId;
    private final String sourceVariant;
    private List<Option> options = List.of();
    private Map<String, List<String>> variants = Map.of();
    private final Map<String, EntityAppearanceSpec> drafts = new LinkedHashMap<>();
    private Option selected;

    AppearanceSelection(String source, EntityAppearanceSpec current) {
        this(source, current, "");
    }

    AppearanceSelection(String source, EntityAppearanceSpec current, String sourceVariant) {
        this.source = Objects.requireNonNull(source);
        this.sourceVariant = Objects.requireNonNull(sourceVariant);
        this.current = current == null ? EntityAppearanceSpec.NONE : current;
        currentId = this.current.isNone() ? source : this.current.targetTypeId();
    }

    void load(List<Option> catalog) {
        load(catalog, Map.of());
    }

    void load(List<Option> catalog, Map<String, List<String>> variants) {
        this.variants = variants.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        drafts.clear();
        LinkedHashMap<String, Option> indexed = new LinkedHashMap<>();
        catalog.forEach(option -> indexed.putIfAbsent(option.id(), option));
        // Retain an unavailable current target so opening/confirming cannot silently replace it.
        if (!currentId.isEmpty()) indexed.putIfAbsent(currentId, new Option(currentId, currentId));
        if (!source.isEmpty() && canChooseSource()) indexed.putIfAbsent(source, new Option(source, source));
        options = indexed.values().stream().sorted(java.util.Comparator.comparing(Option::id)).toList();
        selected = options.stream().filter(option -> option.id().equals(currentId)).findFirst().orElse(null);
    }

    private boolean canChooseSource() {
        if (current.isNone()) return true;
        try { EntityAppearanceSpec.of(source); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }

    List<Option> filter(String query, String namespace) {
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        List<Option> visible = options.stream().filter(option -> option.matches(normalized, namespace)).toList();
        if (!visible.contains(selected)) selected = visible.isEmpty() ? null : visible.getFirst();
        return visible;
    }

    List<String> namespaces() { return options.stream().map(Option::namespace).distinct().sorted().toList(); }
    int size() { return options.size(); }
    Option selected() { return selected; }
    void select(Option option) { if (options.contains(option)) selected = option; }
    boolean isCurrent(Option option) { return option.id().equals(currentId); }
    boolean isDefault(Option option) { return option.id().equals(source); }

    List<String> variantIds() { return selected == null ? List.of() : variants.getOrDefault(selected.id(), List.of()); }
    String variantId() {
        if (selected == null) return "";
        EntityAppearanceSpec spec = spec();
        return spec.isNone() ? sourceVariant : spec.parameters().getOrDefault(EntityAppearanceVariants.PARAMETER, "");
    }
    boolean canConfirm() {
        return selected != null && (spec().isNone() || variantIds().isEmpty() || variantIds().contains(variantId()));
    }

    void chooseVariant(String id) {
        if (!variantIds().contains(id)) throw new IllegalArgumentException("Unavailable appearance variant");
        EntityAppearanceSpec previous = spec();
        Map<String, String> parameters = new LinkedHashMap<>(previous.parameters());
        parameters.put(EntityAppearanceVariants.PARAMETER, id);
        drafts.put(selected.id(), EntityAppearanceSpec.of(selected.id(), parameters));
    }

    EntityAppearanceSpec spec() {
        if (selected == null) throw new IllegalStateException("No appearance candidate");
        return drafts.computeIfAbsent(selected.id(), id -> {
            EntityAppearanceSpec initial = isCurrent(selected) ? current : EntityAppearanceSpec.of(id);
            // NONE means the real entity, not the first registry variant. Merely inspecting it is not an edit.
            if (initial.isNone()) return initial;
            List<String> available = variants.getOrDefault(id, List.of());
            if (available.isEmpty() || initial.parameters().containsKey(EntityAppearanceVariants.PARAMETER)) return initial;
            Map<String, String> parameters = new LinkedHashMap<>(initial.parameters());
            parameters.put(EntityAppearanceVariants.PARAMETER, available.getFirst());
            return EntityAppearanceSpec.of(id, parameters);
        });
    }
}
