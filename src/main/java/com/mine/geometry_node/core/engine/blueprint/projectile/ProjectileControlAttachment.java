package com.mine.geometry_node.core.engine.blueprint.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Persistent Geometry Node control state carried by a projectile entity. */
public final class ProjectileControlAttachment {
    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectileControlAttachment> SYNC_CODEC =
            StreamCodec.of(ProjectileControlAttachment::writeSync, ProjectileControlAttachment::readSync);
    private static final String POLICY_TAG = "CollisionPolicy";
    private static final String RETAINED_TAG = "Retained";
    private static final String IGNORE_AIR_RESISTANCE_TAG = "IgnoreAirResistance";

    private ProjectileCollisionPolicy collisionPolicy = ProjectileCollisionPolicy.VANILLA;
    private boolean retained;
    private boolean ignoreAirResistance;

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

    public boolean ignoreAirResistance() {
        return ignoreAirResistance;
    }

    public void setIgnoreAirResistance(boolean ignoreAirResistance) {
        this.ignoreAirResistance = ignoreAirResistance;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (collisionPolicy != ProjectileCollisionPolicy.VANILLA) {
            tag.putString(POLICY_TAG, collisionPolicy.id());
        }
        if (retained) {
            tag.putBoolean(RETAINED_TAG, true);
        }
        if (ignoreAirResistance) {
            tag.putBoolean(IGNORE_AIR_RESISTANCE_TAG, true);
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        collisionPolicy = ProjectileCollisionPolicy.parse(
                tag != null ? tag.getStringOr(POLICY_TAG, "") : "");
        retained = tag != null && tag.getBooleanOr(RETAINED_TAG, false);
        ignoreAirResistance = tag != null && tag.getBooleanOr(IGNORE_AIR_RESISTANCE_TAG, false);
    }

    private static void writeSync(RegistryFriendlyByteBuf buffer, ProjectileControlAttachment attachment) {
        buffer.writeUtf(attachment.collisionPolicy.id(), 32);
        buffer.writeBoolean(attachment.retained);
        buffer.writeBoolean(attachment.ignoreAirResistance);
    }

    private static ProjectileControlAttachment readSync(RegistryFriendlyByteBuf buffer) {
        ProjectileControlAttachment attachment = new ProjectileControlAttachment();
        attachment.collisionPolicy = ProjectileCollisionPolicy.parse(buffer.readUtf(32));
        attachment.retained = buffer.readBoolean();
        attachment.ignoreAirResistance = buffer.readBoolean();
        return attachment;
    }
}
