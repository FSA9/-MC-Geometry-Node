package com.mine.geometry_node.core.engine.system.entity.appearance;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.flag.FeatureFlagSet;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Common-side parameter schemas. Client render adapters must explicitly implement the same schema. */
public final class EntityAppearanceAdapters {
    private static final Map<String, Predicate<Map<String, String>>> VALIDATORS = new ConcurrentHashMap<>();
    private EntityAppearanceAdapters() {}

    /** Register during common setup. Validator must be deterministic, side-effect-free, and check the complete map. */
    public static void register(String targetTypeId, Predicate<Map<String, String>> validator) {
        EntityAppearanceSpec.of(targetTypeId);
        if (targetTypeId.isEmpty() || VALIDATORS.putIfAbsent(targetTypeId, Objects.requireNonNull(validator)) != null)
            throw new IllegalArgumentException("Duplicate or empty appearance adapter target");
    }

    /** Target capability, complete schema and current-world variant membership; never invokes an entity factory. */
    public static void validate(EntityAppearanceSpec spec, Level level) {
        Objects.requireNonNull(spec); Objects.requireNonNull(level);
        validate(spec, EntityAppearanceVariants.Context.of(level));
    }

    /** Equivalent world-registry input for callers that already own the current lookup and feature flags. */
    public static void validate(EntityAppearanceSpec spec, EntityAppearanceVariants.Context context) {
        Objects.requireNonNull(spec); Objects.requireNonNull(context);
        if (spec.isNone()) return;
        validateTarget(spec, context.enabledFeatures());
        validateSchema(spec);
        EntityAppearanceVariants.resolve(spec, context);
    }

    static void validateTarget(EntityAppearanceSpec spec, FeatureFlagSet enabledFeatures) {
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(spec.targetTypeId())).orElseThrow(
                () -> new IllegalArgumentException("Unknown appearance target"));
        if (!type.canSerialize() || !type.canSummon() || !type.isEnabled(enabledFeatures))
            throw new IllegalArgumentException("Unavailable appearance target");
        // NeoForge's attribute map is keyed by EntityType<? extends LivingEntity> and includes MOD declarations.
        // An explicit common adapter can declare compatibility for exceptional MOD types lacking that declaration.
        if (!DefaultAttributes.hasSupplier(type) && (Identifier.parse(spec.targetTypeId()).getNamespace().equals("minecraft")
                || !VALIDATORS.containsKey(spec.targetTypeId())))
            throw new IllegalArgumentException("Appearance target has no declared living capability");
    }

    /** Complete parameter syntax only; world registry membership requires validate(spec, level/context). */
    public static void validateSchema(EntityAppearanceSpec spec) {
        if (spec.parameters().containsKey("baby") && !Set.of("true", "false").contains(spec.parameters().get("baby")))
            throw new IllegalArgumentException("Invalid baby appearance parameter");
        boolean registeredVariant = EntityAppearanceVariants.hasProvider(spec.targetTypeId());
        if (registeredVariant && spec.parameters().containsKey(EntityAppearanceVariants.PARAMETER))
            EntityAppearanceVariants.requireVariantId(spec.parameters().get(EntityAppearanceVariants.PARAMETER));
        Predicate<Map<String, String>> adapter = VALIDATORS.get(spec.targetTypeId());
        if (adapter != null) {
            if (!adapter.test(spec.parameters())) throw new IllegalArgumentException("Unsupported appearance parameters");
            return;
        }
        if (spec.parameters().isEmpty() || babyParameters(spec.parameters())
                || registeredVariant && Set.of("baby", EntityAppearanceVariants.PARAMETER).containsAll(spec.parameters().keySet())) return;
        throw new IllegalArgumentException("Unsupported appearance parameters");
    }

    /** Client runtime contract check against an already constructed, unspawned candidate. */
    public static void validateParameters(EntityAppearanceSpec spec, Entity candidate) {
        if (!(candidate instanceof LivingEntity) || candidate instanceof Player || spec.isNone()
                || !BuiltInRegistries.ENTITY_TYPE.getKey(candidate.getType()).toString().equals(spec.targetTypeId()))
            throw new IllegalArgumentException("Mismatched appearance candidate");
        validate(spec, candidate.level());
        if (spec.parameters().containsKey("baby") && !(candidate instanceof AgeableMob || candidate instanceof Zombie))
            throw new IllegalArgumentException("Appearance target does not support baby");
    }
    private static boolean babyParameters(Map<String, String> parameters) {
        return parameters.keySet().equals(Set.of("baby")) && Set.of("true", "false").contains(parameters.get("baby"));
    }
}
