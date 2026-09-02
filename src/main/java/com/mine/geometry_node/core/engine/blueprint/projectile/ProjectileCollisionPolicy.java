package com.mine.geometry_node.core.engine.blueprint.projectile;

import java.util.Locale;

/** Stable lifecycle policy stored on a projectile and exposed by node select controls. */
public enum ProjectileCollisionPolicy {
    VANILLA("vanilla", "geometry_node.projectile.collision_policy.vanilla"),
    DISCARD_ON_HIT("discard_on_hit", "geometry_node.projectile.collision_policy.discard_on_hit"),
    RETAIN_ON_HIT("retain_on_hit", "geometry_node.projectile.collision_policy.retain_on_hit");

    public static final String[] OPTION_IDS = valuesArray(ProjectileCollisionPolicy::id);
    public static final String[] OPTION_LABEL_KEYS = valuesArray(ProjectileCollisionPolicy::translationKey);

    private final String id;
    private final String translationKey;

    ProjectileCollisionPolicy(String id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return translationKey;
    }

    public static ProjectileCollisionPolicy parse(String value) {
        if (value != null) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (ProjectileCollisionPolicy policy : values()) {
                if (policy.id.equals(normalized)) {
                    return policy;
                }
            }
        }
        return VANILLA;
    }

    private static String[] valuesArray(java.util.function.Function<ProjectileCollisionPolicy, String> mapper) {
        ProjectileCollisionPolicy[] values = values();
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = mapper.apply(values[i]);
        }
        return result;
    }
}
