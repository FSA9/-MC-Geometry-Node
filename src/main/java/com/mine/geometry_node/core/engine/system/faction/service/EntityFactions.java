package com.mine.geometry_node.core.engine.system.faction.service;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.system.faction.model.*;
import com.mine.geometry_node.core.engine.system.faction.storage.FactionJsonCodec;
import com.mojang.serialization.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import java.io.IOException;
import java.util.Map;

/** Entity ownership adapter. No UUID index, chunk acquisition or scoreboard mutation. */
public final class EntityFactions {
    public static final MapCodec<EntityFactionProfile> CODEC = Codec.STRING.comapFlatMap(source -> {
        try { return DataResult.success(FactionJsonCodec.decodeProfile(source)); }
        catch (IllegalArgumentException invalid) { return DataResult.error(invalid::getMessage); }
    }, FactionJsonCodec::encodeProfile).fieldOf("profile");
    private EntityFactions() {}

    public static EntityFactionProfile read(Entity entity) {
        requireOwner(entity);
        return entity.hasData(GeometryNode.ENTITY_FACTIONS) ? entity.getData(GeometryNode.ENTITY_FACTIONS) : EntityFactionProfile.EMPTY;
    }
    public static Map<String, Object> snapshot(Entity entity) { return FactionJsonCodec.profileValues(read(entity)); }
    public static EntityFactionProfile validate(Entity entity, Object replacement) {
        EntityFactionProfile next = FactionJsonCodec.profile(replacement);
        try { next.validateReplacement(read(entity), ServerFactionService.INSTANCE.snapshot(entity.level().getServer())); }
        catch (IOException unavailable) { throw new IllegalStateException("Faction catalogue unavailable", unavailable); }
        return next;
    }
    public static void replace(Entity entity, EntityFactionProfile replacement) {
        requireOwner(entity);
        entity.setData(GeometryNode.ENTITY_FACTIONS, replacement);
    }
    private static void requireOwner(Entity entity) {
        if (entity == null || entity.isRemoved() || !(entity.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || level.getServer().isStopped())
            throw new IllegalStateException("Entity faction owner is unavailable");
    }
}
