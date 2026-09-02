package com.mine.geometry_node.core.engine.blueprint.projectile;

import net.minecraft.nbt.CompoundTag;

/** Persistent Geometry Node control state carried by a projectile entity. */
public final class ProjectileControlAttachment {
    private static final String POLICY_TAG = "CollisionPolicy";
    private static final String RETAINED_TAG = "Retained";

    private ProjectileCollisionPolicy collisionPolicy = ProjectileCollisionPolicy.VANILLA;
    private boolean retained;

    public ProjectileCollisionPolicy collisionPolicy() {
        return collisionPolicy;
    }

    public void setCollisionPolicy(ProjectileCollisionPolicy collisionPolicy) {
        this.collisionPolicy = collisionPolicy != null
                ? collisionPolicy
                : ProjectileCollisionPolicy.VANILLA;
    }

    public boolean retained() {
        return retained;
    }

    public void setRetained(boolean retained) {
        this.retained = retained;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (collisionPolicy != ProjectileCollisionPolicy.VANILLA) {
            tag.putString(POLICY_TAG, collisionPolicy.id());
        }
        if (retained) {
            tag.putBoolean(RETAINED_TAG, true);
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        collisionPolicy = ProjectileCollisionPolicy.parse(
                tag != null ? tag.getStringOr(POLICY_TAG, "") : "");
        retained = tag != null && tag.getBooleanOr(RETAINED_TAG, false);
    }
}
