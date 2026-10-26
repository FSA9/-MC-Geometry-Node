package com.mine.geometry_node.core.engine.system.entity.appearance;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.flag.FeatureFlagSet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Registered visual variants in the existing spec parameter schema, on either logical side. */
public final class EntityAppearanceVariants {
    public static final String PARAMETER = "variant";
    private static final Map<String, EntityAppearanceVariantProvider<?>> PROVIDERS = new ConcurrentHashMap<>();
    private static final AtomicLong REVISION = new AtomicLong();

    /** Registry IDs only; no translation keys, labels, holders, entities or retained world references. */
    public record Options(String targetTypeId, String registryId, String componentId, List<String> variantIds) {
        public Options { variantIds = List.copyOf(variantIds); }
    }

    /** Short-lived current-world input. Providers must never retain it; Options retain only ID strings. */
    public record Context(HolderLookup.Provider registries, FeatureFlagSet enabledFeatures) {
        public Context { Objects.requireNonNull(registries); Objects.requireNonNull(enabledFeatures); }
        public static Context of(Level level) { return new Context(level.registryAccess(), level.enabledFeatures()); }
    }

    static {
        vanilla("cat", Registries.CAT_VARIANT, () -> DataComponents.CAT_VARIANT);
        vanilla("wolf", Registries.WOLF_VARIANT, () -> DataComponents.WOLF_VARIANT);
        vanilla("frog", Registries.FROG_VARIANT, () -> DataComponents.FROG_VARIANT);
        vanilla("pig", Registries.PIG_VARIANT, () -> DataComponents.PIG_VARIANT);
        vanilla("cow", Registries.COW_VARIANT, () -> DataComponents.COW_VARIANT);
        vanilla("chicken", Registries.CHICKEN_VARIANT, () -> DataComponents.CHICKEN_VARIANT);
        vanilla("zombie_nautilus", Registries.ZOMBIE_NAUTILUS_VARIANT, () -> DataComponents.ZOMBIE_NAUTILUS_VARIANT);
        vanilla("villager", Registries.VILLAGER_TYPE, () -> DataComponents.VILLAGER_VARIANT);
        vanilla("zombie_villager", Registries.VILLAGER_TYPE, () -> DataComponents.VILLAGER_VARIANT);
    }

    private EntityAppearanceVariants() {}

    private static <T> void vanilla(String type, ResourceKey<Registry<T>> registry, Supplier<DataComponentType<Holder<T>>> component) {
        register("minecraft:" + type, new EntityAppearanceVariantProvider<>(registry, component, (level, holder) -> true));
    }

    /** Register the same binding/filter during common setup on both sides. Existing targets cannot be replaced. */
    public static void register(String targetTypeId, EntityAppearanceVariantProvider<?> provider) {
        if (EntityAppearanceSpec.of(targetTypeId).isNone()
                || PROVIDERS.putIfAbsent(targetTypeId, Objects.requireNonNull(provider)) != null)
            throw new IllegalArgumentException("Duplicate or empty appearance variant provider target");
        REVISION.incrementAndGet();
    }

    public static boolean hasProvider(String targetTypeId) { return PROVIDERS.containsKey(targetTypeId); }
    /** Read-only source metadata, independent of any configured replacement appearance. */
    public static Optional<String> currentVariantId(net.minecraft.world.entity.Entity entity) {
        Objects.requireNonNull(entity);
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        var provider = PROVIDERS.get(typeId);
        return provider == null ? Optional.empty() : provider.currentVariantId(entity);
    }
    /** Lets client resources invalidate negative sessions after explicit provider registration. */
    public static long revision() { return REVISION.get(); }

    /** Empty means no provider; an empty variant list means no currently selectable entries. */
    public static Optional<Options> options(Level level, String targetTypeId) {
        return options(Context.of(level), targetTypeId);
    }

    public static Optional<Options> options(Context context, String targetTypeId) {
        Objects.requireNonNull(context);
        EntityAppearanceSpec spec = EntityAppearanceSpec.of(targetTypeId);
        var provider = PROVIDERS.get(targetTypeId);
        if (provider == null) return Optional.empty();
        EntityAppearanceAdapters.validateTarget(spec, context.enabledFeatures());
        return Optional.of(new Options(targetTypeId, provider.registry().identifier().toString(),
                Objects.requireNonNull(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(provider.component())).toString(),
                provider.variantIds(context)));
    }

    public static List<String> variantIds(Level level, String targetTypeId) {
        return variantIds(Context.of(level), targetTypeId);
    }

    public static List<String> variantIds(Context context, String targetTypeId) {
        return options(context, targetTypeId).map(Options::variantIds).orElseGet(List::of);
    }

    /** One uniform choice from the current selectable set. The returned ID is the value to persist. */
    public static Optional<String> randomVariantId(Level level, String targetTypeId, RandomSource random) {
        return randomVariantId(Context.of(level), targetTypeId, random);
    }

    public static Optional<String> randomVariantId(Context context, String targetTypeId, RandomSource random) {
        Objects.requireNonNull(random);
        List<String> variants = variantIds(context, targetTypeId);
        return variants.isEmpty() ? Optional.empty() : Optional.of(variants.get(random.nextInt(variants.size())));
    }

    /** Immutable draft/edit helper; preserves all other parameters and validates the complete resulting spec. */
    public static EntityAppearanceSpec withVariant(Level level, EntityAppearanceSpec spec, String variantId) {
        return withVariant(Context.of(level), spec, variantId);
    }

    public static EntityAppearanceSpec withVariant(Context context, EntityAppearanceSpec spec, String variantId) {
        if (!hasProvider(spec.targetTypeId())) throw new IllegalArgumentException("No appearance variant provider");
        requireVariantId(variantId);
        Map<String, String> parameters = new HashMap<>(spec.parameters());
        parameters.put(PARAMETER, variantId);
        EntityAppearanceSpec result = new EntityAppearanceSpec(spec.version(), spec.targetTypeId(), parameters);
        EntityAppearanceAdapters.validate(result, context);
        return result;
    }

    /** Pure resolution for validation and the private client proxy path. Never applies to an entity. */
    public static Optional<TypedDataComponent<?>> resolve(EntityAppearanceSpec spec, Level level) {
        return resolve(spec, Context.of(level));
    }

    public static Optional<TypedDataComponent<?>> resolve(EntityAppearanceSpec spec, Context context) {
        var provider = PROVIDERS.get(spec.targetTypeId());
        String variant = spec.parameters().get(PARAMETER);
        // Existing bespoke adapter schemas keep ownership of their own non-provider parameters.
        return provider == null || variant == null ? Optional.empty() : Optional.of(provider.resolve(context, variant));
    }

    static boolean isVariantId(String id) {
        if (id == null || id.length() > EntityAppearanceSpec.MAX_VALUE_LENGTH || !id.contains(":")) return false;
        Identifier parsed = Identifier.tryParse(id);
        return parsed != null && parsed.toString().equals(id);
    }

    static void requireVariantId(String id) {
        if (!isVariantId(id)) throw new IllegalArgumentException("Expected namespaced appearance variant registry ID");
    }
}
