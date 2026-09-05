package com.mine.geometry_node.core.engine.blueprint.event.dispatcher;

import com.mine.geometry_node.api.EventPayload;
import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.node.nodes.events.entity.OnEntityGainItem;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.util.SlotAccessUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Tracks net primary-storage gains only while a bound graph subscribes to the event. */
public final class EntityInventoryGainTracker {
    private final Map<MinecraftServer, Map<Entity, List<ItemStack>>> servers = new WeakHashMap<>();

    public EntityInventoryGainTracker() {
    }

    public void beginTracking(Entity entity) {
        if (entity != null && entity.level() instanceof ServerLevel level) {
            snapshots(level).computeIfAbsent(entity, SlotAccessUtils::snapshotPrimaryStorage);
        }
    }

    public void clear(Entity entity) {
        if (entity != null && entity.level() instanceof ServerLevel level) {
            clear(level, entity);
        }
    }

    public void clear(ServerLevel level, Entity entity) {
        if (level == null || entity == null) return;
        Map<Entity, List<ItemStack>> snapshots = servers.get(level.getServer());
        if (snapshots != null) snapshots.remove(entity);
    }

    public void tick(ServerLevel level, Entity entity, boolean listening) {
        if (!listening) {
            clear(entity);
            return;
        }

        List<ItemStack> current = SlotAccessUtils.snapshotPrimaryStorage(entity);
        List<ItemStack> previous = snapshots(level).put(entity, current);
        if (previous == null) {
            return;
        }

        List<ItemStack> processed = new ArrayList<>();
        for (ItemStack stack : current) {
            if (stack.isEmpty() || containsSameItem(processed, stack)) {
                continue;
            }
            processed.add(stack);

            int gained = countMatching(current, stack) - countMatching(previous, stack);
            if (gained <= 0) {
                continue;
            }

            BlueprintRuntime.INSTANCE.dispatchBoundEntityEvent(level, entity, OnEntityGainItem.TYPE_ID, EventPayload.of(
                    StandardPorts.ENTITY.getId(), entity,
                    StandardPorts.ITEM_STACK.getId(), stack.copyWithCount(gained)
            ).values());
        }
    }

    public void shutdown(MinecraftServer server) {
        servers.remove(server);
    }

    private Map<Entity, List<ItemStack>> snapshots(ServerLevel level) {
        return servers.computeIfAbsent(level.getServer(), ignored -> new WeakHashMap<>());
    }

    private static int countMatching(List<ItemStack> stacks, ItemStack template) {
        int count = 0;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean containsSameItem(List<ItemStack> stacks, ItemStack template) {
        for (ItemStack stack : stacks) {
            if (ItemStack.isSameItemSameComponents(stack, template)) {
                return true;
            }
        }
        return false;
    }
}
