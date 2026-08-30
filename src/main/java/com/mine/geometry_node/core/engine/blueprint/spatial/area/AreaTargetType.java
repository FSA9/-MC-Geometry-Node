package com.mine.geometry_node.core.engine.blueprint.spatial.area;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.jetbrains.annotations.Nullable;

public enum AreaTargetType {
    ALL("all", Entity.class),
    LIVING("living", LivingEntity.class),
    PLAYER("player", Player.class),
    PROJECTILE("projectile", Projectile.class),
    ITEM("item", ItemEntity.class);

    public static final String[] OPTIONS = {ALL.id, LIVING.id, PLAYER.id, PROJECTILE.id, ITEM.id};

    private final String id;
    private final Class<? extends Entity> entityClass;

    AreaTargetType(String id, Class<? extends Entity> entityClass) {
        this.id = id;
        this.entityClass = entityClass;
    }

    public String id() {
        return id;
    }

    public Class<? extends Entity> entityClass() {
        return entityClass;
    }

    public boolean matches(Entity entity) {
        return switch (this) {
            case ALL -> true;
            case LIVING -> entity instanceof LivingEntity;
            case PLAYER -> entity instanceof Player;
            case PROJECTILE -> entity instanceof Projectile;
            case ITEM -> entity instanceof ItemEntity;
        };
    }

    public static AreaTargetType fromId(@Nullable String id) {
        if (id != null) {
            for (AreaTargetType targetType : values()) {
                if (targetType.id.equalsIgnoreCase(id)) {
                    return targetType;
                }
            }
        }
        return ALL;
    }
}
