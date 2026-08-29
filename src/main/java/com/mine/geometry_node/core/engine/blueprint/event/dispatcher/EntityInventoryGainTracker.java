package com.mine.geometry_node.core.engine.blueprint.event.dispatcher;

import com.mine.geometry_node.core.engine.blueprint.event.GraphEventData;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintEngine;
import com.mine.geometry_node.core.node.nodes.events.entity.OnEntityGainItem;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.util.SlotAccessUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Tracks net primary-storage gains only while a bound graph subscribes to the event. */
public final class EntityInventoryGainTracker {
    private static final Map<Entity, List<ItemStack>> SNAPSHOTS = new WeakHashMap<>();

    private EntityInventoryGainTracker() {
    }

    public static void beginTracking(Entity entity) {
        if (entity != null) {
            SNAPSHOTS.computeIfAbsent(entity, SlotAccessUtils::snapshotPrimaryStorage);
        }
    }

    public static void clear(Entity entity) {
        SNAPSHOTS.remove(entity);
    }

    static void tick(ServerLevel level, Entity entity, boolean listening) {
        if (!listening) {
            clear(entity);
            return;
        }

        List<ItemStack> current = SlotAccessUtils.snapshotPrimaryStorage(entity);
        List<ItemStack> previous = SNAPSHOTS.put(entity, current);
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

            BlueprintEngine.dispatchBoundEntityEvent(level, entity, OnEntityGainItem.TYPE_ID, GraphEventData.of(
                    StandardPorts.ENTITY.getId(), entity,
                    StandardPorts.ITEM_STACK.getId(), stack.copyWithCount(gained)
            ));
        }
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
