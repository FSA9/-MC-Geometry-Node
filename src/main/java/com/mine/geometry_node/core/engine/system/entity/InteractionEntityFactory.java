package com.mine.geometry_node.core.engine.system.entity;

import com.mine.geometry_node.core.utils.nbt.EntityNbtCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.phys.Vec3;

/** Native entity creation shared by graph nodes and viewport tools. */
public final class InteractionEntityFactory {
    private InteractionEntityFactory() {}

    public static Interaction spawn(ServerLevel level, Vec3 position, float width, float height, boolean responsive) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0)
            throw new IllegalArgumentException("Invalid interaction size");
        Interaction entity = EntityType.INTERACTION.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) return null;
        entity.setPos(position.x, position.y, position.z);
        var data = EntityNbtCompat.saveWithoutId(entity);
        data.putFloat("width", width);
        data.putFloat("height", height);
        data.putBoolean("response", responsive);
        EntityNbtCompat.load(entity, data);
        return level.addFreshEntity(entity) ? entity : null;
    }
}
