package com.mine.geometry_node.core.engine.system.entity.appearance;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.core.component.DataComponentGetter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/** Explicit registry/holder-component binding; never constructs or mutates an entity. */
public final class EntityAppearanceVariantProvider<T> {
    private final ResourceKey<Registry<T>> registry;
    private final Supplier<DataComponentType<Holder<T>>> component;
    private final BiPredicate<EntityAppearanceVariants.Context, Holder.Reference<T>> available;

    public EntityAppearanceVariantProvider(ResourceKey<Registry<T>> registry, DataComponentType<Holder<T>> component) {
        this(registry, component, (level, holder) -> true);
    }

    /** The filter must be deterministic and side-effect-free, using only the supplied world's current data. */
    public EntityAppearanceVariantProvider(ResourceKey<Registry<T>> registry, DataComponentType<Holder<T>> component,
                                           BiPredicate<EntityAppearanceVariants.Context, Holder.Reference<T>> available) {
        this(registry, () -> Objects.requireNonNull(component), available);
        Objects.requireNonNull(component);
    }

    // Deferred vanilla components keep schema-only validation independent of Minecraft bootstrap.
    EntityAppearanceVariantProvider(ResourceKey<Registry<T>> registry, Supplier<DataComponentType<Holder<T>>> component,
                                    BiPredicate<EntityAppearanceVariants.Context, Holder.Reference<T>> available) {
        this.registry = Objects.requireNonNull(registry);
        this.component = Objects.requireNonNull(component);
        this.available = Objects.requireNonNull(available);
    }

    public ResourceKey<Registry<T>> registry() { return registry; }
    public DataComponentType<Holder<T>> component() { return Objects.requireNonNull(component.get()); }

    /** Read the actual component, without constructing a default entity or applying an appearance. */
    public Optional<String> currentVariantId(DataComponentGetter source) {
        Holder<T> value = source.get(component());
        return value == null ? Optional.empty() : value.unwrapKey()
                .filter(key -> key.isFor(registry)).map(key -> key.identifier().toString())
                .filter(EntityAppearanceVariants::isVariantId);
    }

    /** No biome/spawn weighting: every explicitly selectable, spec-representable entry in this world. */
    public List<String> variantIds(Level level) {
        return variantIds(EntityAppearanceVariants.Context.of(level));
    }

    public List<String> variantIds(EntityAppearanceVariants.Context context) {
        return context.registries().lookup(registry).stream().flatMap(values -> values.listElements())
                .filter(holder -> available.test(context, holder)).map(holder -> holder.key().identifier().toString())
                .filter(EntityAppearanceVariants::isVariantId).sorted().toList();
    }

    /** Exact holder lookup deliberately avoids defaulted-registry fallback. */
    public TypedDataComponent<Holder<T>> resolve(Level level, String variantId) {
        return resolve(EntityAppearanceVariants.Context.of(level), variantId);
    }

    public TypedDataComponent<Holder<T>> resolve(EntityAppearanceVariants.Context context, String variantId) {
        EntityAppearanceVariants.requireVariantId(variantId);
        var holder = context.registries().lookup(registry)
                .flatMap(values -> values.get(ResourceKey.create(registry, net.minecraft.resources.Identifier.parse(variantId))))
                .filter(value -> available.test(context, value))
                .orElseThrow(() -> new IllegalArgumentException("Unavailable appearance variant: " + variantId));
        return new TypedDataComponent<>(component(), holder);
    }
}
