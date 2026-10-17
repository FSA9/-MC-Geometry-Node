package com.mine.geometry_node.core.engine.system.viewport.entity;

import com.mine.geometry_node.core.engine.behavior.runtime.ExternalMovementLease;
import com.mine.geometry_node.core.engine.behavior.runtime.navigation.BehaviorNavigationService;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetPermissions;
import com.mine.geometry_node.core.engine.system.viewport.EditCameraChunkService;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.viewport.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

/** Server-authoritative transient orders; no saved objects, teleportation, or client AI. */
public final class ViewportEntityOrders {
    private static final Map<Mob, Order> ORDERS = new LinkedHashMap<>();
    private static final Deque<Batch> QUEUED = new ArrayDeque<>();
    private static boolean initialized;
    private ViewportEntityOrders() {}
    public static void init() {
        if (initialized) return; initialized = true;
        dev.architectury.event.events.common.TickEvent.SERVER_LEVEL_PRE.register(level -> {
            for (var mob : List.copyOf(ORDERS.keySet())) {
                Order order = ORDERS.get(mob);
                if (order.level != level) continue;
                long tick = level.getGameTime();
                if (mob.isRemoved() || !mob.isAlive() || mob.level() != level || mob.isNoAi() || mob.isPassenger()
                        || order.player.hasDisconnected() || !order.player.isAlive() || order.player.level() != level
                        || !RemoteAssetPermissions.canManageAssets(order.player)
                        || tick >= order.expires || (order.started && (mob.position().distanceToSqr(order.target) <= .8 * .8
                        || mob.getNavigation().isDone()))) { stop(mob); continue; }
                if (!order.started) continue;
                if (mob.position().distanceToSqr(order.lastPosition) > .25) {
                    order.lastPosition = mob.position(); order.lastProgress = tick;
                } else if (tick - order.lastProgress > 100) stop(mob);
            }
            processQueued(level);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                clear(o -> o.player == p); QUEUED.removeIf(b -> b.player == p);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                UUID id = player.getUUID();
                clear(o -> o.player.getUUID().equals(id));
                QUEUED.removeIf(b -> b.player.getUUID().equals(id));
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload e) -> {
            clear(o -> o.level == e.getLevel()); QUEUED.removeIf(b -> b.level == e.getLevel());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            clear(o -> o.level.getServer() == e.getServer());
            QUEUED.removeIf(b -> b.level.getServer() == e.getServer());
        });
    }
    public static void receive(ServerPlayer player, PacketViewportEntityMove packet) {
        var level = player.level();
        if (QUEUED.size() >= 8 && QUEUED.stream().noneMatch(b -> b.player == player)) {
            reply(player,packet,0,BlockPos.ZERO,"rate_limit"); return;
        }
        if (!player.isAlive() || !RemoteAssetPermissions.canManageAssets(player)
                || !level.dimension().identifier().equals(packet.dimension())
                || EditCameraChunkService.positions(player).stream().noneMatch(p -> p.distanceToSqr(packet.camera()) <= 64 * 64)) {
            reply(player,packet,0,BlockPos.ZERO,"permission"); return;
        }
        var hit = ViewportBlockRaycast.clip(level,packet.origin(),packet.origin().add(packet.direction().scale(packet.length())));
        var chunk = level.getChunkSource().getChunkNow(hit.getBlockPos().getX() >> 4, hit.getBlockPos().getZ() >> 4);
        if (hit.getType() != HitResult.Type.BLOCK || chunk == null
                || !level.getWorldBorder().isWithinBounds(hit.getBlockPos())) {
            reply(player,packet,0,BlockPos.ZERO,"no_target"); return;
        }
        BlockPos block = hit.getBlockPos();
        var shape = chunk.getBlockState(block).getCollisionShape(level,block);
        if (shape.isEmpty()) { reply(player,packet,0,block,"no_target"); return; }
        Vec3 destination = new Vec3(block.getX()+.5,block.getY()+shape.bounds().maxY,block.getZ()+.5);
        // A new click supersedes this player's unstarted batch. Ownership takeover is immediate;
        // only expensive path searches wait for the per-tick budget.
        for (var old : List.copyOf(QUEUED)) if (old.player == player) {
            QUEUED.remove(old); cancelPending(old);
            reply(player,old.packet,old.accepted,old.block,old.accepted>0?"accepted":"unavailable");
        }
        Batch batch = new Batch(player,level,packet,block);
        int reserved=0;
        for (UUID id : packet.entities()) if (reserve(batch,id,destination)) reserved++;
        if (reserved==0) { reply(player,packet,0,block,"unavailable"); return; }
        QUEUED.addLast(batch);
    }
    /** Fair round-robin queue; at most eight entity attempts per world tick. */
    private static void processQueued(net.minecraft.server.level.ServerLevel level) {
        int budget = 8, skipped = 0;
        while (budget > 0 && !QUEUED.isEmpty() && skipped < QUEUED.size()) {
            Batch batch = QUEUED.removeFirst();
            if (batch.level != level) { QUEUED.addLast(batch); skipped++; continue; }
            skipped = 0;
            var chunk = level.getChunkSource().getChunkNow(batch.block.getX() >> 4, batch.block.getZ() >> 4);
            if (batch.player.hasDisconnected() || !batch.player.isAlive() || batch.player.level()!=level
                    || !RemoteAssetPermissions.canManageAssets(batch.player)
                    || chunk == null || chunk.getBlockState(batch.block).isAir()) {
                cancelPending(batch);
                if (!batch.player.hasDisconnected()) reply(batch.player,batch.packet,batch.accepted,batch.block,
                        batch.accepted > 0 ? "accepted" : "unavailable");
                continue;
            }
            if (start(batch,batch.packet.entities().get(batch.index++))) batch.accepted++;
            budget--;
            if (batch.index < batch.packet.entities().size()) QUEUED.addLast(batch);
            else reply(batch.player,batch.packet,batch.accepted,batch.block,batch.accepted>0?"accepted":"unavailable");
        }
    }
    private static boolean reserve(Batch batch, UUID id, Vec3 destination) {
        var level=batch.level;
        if (!(level.getEntity(id) instanceof Mob mob) || !mob.isAlive() || mob.isRemoved() || mob.isNoAi()
                || mob.isPassenger() || mob.isVehicle()
                || mob.position().distanceToSqr(destination)>128*128
                || mob.position().distanceToSqr(batch.packet.camera())>512*512) return false;
        if (!ORDERS.containsKey(mob) && ORDERS.size()>=512) return false;
        // Any authorized editor may supersede an older editor's order as well.
        stop(mob);
        ExternalMovementLease lease=null;
        try {
            lease=ExternalMovementLease.takeOver(mob);
            if(lease==null)return false;
            ORDERS.put(mob,new Order(batch,destination,lease,level.getGameTime(),mob.position()));
            return true;
        } catch(RuntimeException failure) {
            if(lease!=null)release(lease);
            com.mine.geometry_node.GeometryNode.LOGGER.warn("Unable to take over viewport entity {}",id,failure);
            return false;
        }
    }
    private static boolean start(Batch batch, UUID id) {
        if (!(batch.level.getEntity(id) instanceof Mob mob)) return false;
        Order order=ORDERS.get(mob);
        // Superseded batches must never reacquire control, even when sent by a different player.
        if(!batch.owns(order))return false;
        boolean retained=false;
        try {
            if(mob.isRemoved()||!mob.isAlive()||mob.isNoAi()||mob.isPassenger()||mob.isVehicle())return false;
            if(mob.position().distanceToSqr(order.target)<=.8*.8)return true;
            if(!BehaviorNavigationService.INSTANCE.moveTo(mob,order.target,1.0)
                    ||mob.getNavigation().getPath()==null||!mob.getNavigation().getPath().canReach())return false;
            com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager.recordRequestedPathTarget(mob,order.target);
            order.started=true;order.lastProgress=batch.level.getGameTime();order.lastPosition=mob.position();
            retained=true;
            return true;
        } catch(RuntimeException failure) {
            com.mine.geometry_node.GeometryNode.LOGGER.warn("Viewport move failed for {}",mob.getUUID(),failure);
            return false;
        } finally {if(!retained)stop(mob);}
    }
    private static void cancelPending(Batch batch) {
        clear(o -> !o.started && batch.owns(o));
    }
    private static final class Batch {
        final ServerPlayer player; final net.minecraft.server.level.ServerLevel level;
        final PacketViewportEntityMove packet; final BlockPos block;
        int index, accepted;
        Batch(ServerPlayer player, net.minecraft.server.level.ServerLevel level,
              PacketViewportEntityMove packet, BlockPos block) {
            this.player=player;this.level=level;this.packet=packet;this.block=block;
        }
        boolean owns(Order order) { return order != null && order.batch == this; }
    }
    private static void reply(ServerPlayer p, PacketViewportEntityMove request, int count, BlockPos target, String reason) {
        NetworkHandler.sendToPlayer(p,new PacketViewportEntityMoveResult(request.requestId(),count,request.entities().size(),target,reason));
    }
    private static void clear(java.util.function.Predicate<Order> filter) {
        for (var mob : List.copyOf(ORDERS.keySet())) if (filter.test(ORDERS.get(mob))) stop(mob);
    }
    private static void stop(Mob mob) {
        Order order = ORDERS.remove(mob);
        if (order != null) try { release(order.lease); }
        finally { com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager.clearRequestedPathTarget(mob); }
    }
    private static void release(ExternalMovementLease lease) {
        try { lease.close(); }
        catch (RuntimeException failure) {
            com.mine.geometry_node.GeometryNode.LOGGER.error("Unable to fully release viewport movement control", failure);
        }
    }
    private static final class Order {
        final ServerPlayer player; final net.minecraft.server.level.ServerLevel level;
        final Batch batch;
        final Vec3 target; final ExternalMovementLease lease; final long expires;
        Vec3 lastPosition; long lastProgress; boolean started;
        Order(Batch batch, Vec3 target,
              ExternalMovementLease lease, long tick, Vec3 position) {
            this.batch=batch;this.player=batch.player;this.level=batch.level;this.target=target;this.lease=lease;
            expires=tick+600;lastProgress=tick;lastPosition=position;
        }
    }
}
