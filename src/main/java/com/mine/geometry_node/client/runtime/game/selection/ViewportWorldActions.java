package com.mine.geometry_node.client.runtime.game.selection;

import com.mine.geometry_node.client.runtime.game.render.PresentedViewportFrame;
import com.mine.geometry_node.client.runtime.game.scene.ViewportSceneMath;
import com.mine.geometry_node.client.scene.ClientSceneService;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.viewport.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** UI-owned feedback with a bounded, generation-checked handoff to the Minecraft/network thread. */
public final class ViewportWorldActions {
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private volatile UUID request;
    private volatile PacketViewportEntityMoveResult result;
    private volatile long generation = -1;
    private volatile String localMessage = "";
    private volatile long sentAt;
    public synchronized void clear() {
        UUID old = request; request = null;
        if (old != null) PENDING.remove(old);
        result = null; localMessage = "";
    }
    public synchronized void moveEntities(List<UUID> ids, PresentedViewportFrame frame, ViewportSceneMath.Ray ray) {
        clear();
        if (ray == null || ids.isEmpty()) return;
        if (!frame.scene().canEdit()) { localMessage = "permission"; return; }
        if (ids.size() > PacketViewportEntityMove.MAX_ENTITIES) { localMessage = "too_many"; return; }
        generation = frame.scene().worldGeneration();
        var c = frame.camera();
        if (new Vec3(c.x(),c.y(),c.z()).distanceToSqr(ray.origin()) > PacketViewportEntityMove.MAX_RAY * PacketViewportEntityMove.MAX_RAY) {
            localMessage = "too_far"; return;
        }
        UUID id = UUID.randomUUID();
        final PacketViewportEntityMove packet;
        try {
            packet = new PacketViewportEntityMove(id, Identifier.parse(frame.scene().dimension()), ids,
                    new Vec3(c.x(), c.y(), c.z()),ray.origin(),ray.direction(),Math.min(ray.length(),PacketViewportEntityMove.MAX_RAY));
        } catch (IllegalArgumentException invalidRay) { localMessage = "too_far"; return; }
        request = id; sentAt = System.nanoTime(); localMessage = "waiting";
        PENDING.entrySet().removeIf(e -> e.getValue().expired());
        PENDING.put(id,new Pending(new WeakReference<>(this),generation,sentAt));
        Minecraft.getInstance().execute(() -> {
            var mc = Minecraft.getInstance();
            if (!id.equals(request) || generation != ClientSceneService.INSTANCE.snapshot().worldGeneration()
                    || mc.level == null || !mc.level.dimension().identifier().equals(packet.dimension()) || mc.getConnection() == null) {
                PENDING.remove(id); return;
            }
            NetworkHandler.sendToServer(packet);
        });
    }
    public static void accept(PacketViewportEntityMoveResult packet) {
        Pending pending = PENDING.remove(packet.requestId());
        if (pending == null || pending.expired()) return;
        var owner = pending.owner.get();
        if (owner == null) return;
        synchronized (owner) {
            if (!packet.requestId().equals(owner.request)
                    || pending.generation != ClientSceneService.INSTANCE.snapshot().worldGeneration()) return;
            owner.result = packet; owner.localMessage = "";
        }
    }
    public synchronized BlockPos target() { return result != null && result.accepted() > 0 ? result.target() : null; }
    public synchronized String message() {
        var reply = result;
        if (reply != null) return Component.translatable("geometry_node.game_view.entities." + reply.reason(),
                reply.accepted(),reply.requested()).getString();
        if (request != null && System.nanoTime() - sentAt > 10_000_000_000L && localMessage.equals("waiting")) {
            PENDING.remove(request); localMessage = "timeout";
        }
        return localMessage.isEmpty() ? "" : Component.translatable("geometry_node.game_view.entities." + localMessage).getString();
    }
    private record Pending(WeakReference<ViewportWorldActions> owner,long generation,long sentAt) {
        boolean expired() { return owner.get() == null || System.nanoTime() - sentAt > 10_000_000_000L; }
    }
}
