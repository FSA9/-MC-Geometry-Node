package com.mine.geometry_node.core.node.util;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.value.SlotRef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SlotAccessUtils {
    public static final String CLEAR_SCOPE_INVENTORY = "inventory";
    public static final String CLEAR_SCOPE_EQUIPMENT = "equipment";
    public static final String CLEAR_SCOPE_ALL = "all";
    public static final String[] CLEAR_SCOPE_OPTIONS = {CLEAR_SCOPE_INVENTORY, CLEAR_SCOPE_EQUIPMENT, CLEAR_SCOPE_ALL};

    private static final EquipmentSlot[] PLAYER_EQUIPMENT_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.OFFHAND
    };
    private static final EquipmentSlot[] LIVING_EQUIPMENT_SLOTS = {
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private SlotAccessUtils() {
    }

    public static ItemStack getItem(Entity target, SlotRef ref) {
        SlotAccess access = resolve(target, ref);
        return access != null ? access.get().copy() : ItemStack.EMPTY;
    }

    public static boolean setItem(Entity target, SlotRef ref, ItemStack stack) {
        SlotAccess access = resolve(target, ref);
        return access != null && access.set(stack != null ? stack.copy() : ItemStack.EMPTY);
    }

    public static ItemStack clearItem(Entity target, SlotRef ref) {
        SlotAccess access = resolve(target, ref);
        if (access == null) {
            return ItemStack.EMPTY;
        }
        ItemStack current = access.get();
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = current.copy();
        return access.set(ItemStack.EMPTY) ? removed : ItemStack.EMPTY;
    }

    public static ItemStack extractItem(Entity target, SlotRef ref, int requestedCount) {
        SlotAccess access = resolve(target, ref);
        if (access == null) {
            return ItemStack.EMPTY;
        }
        return access.extract(requestedCount);
    }

    public static boolean dropItem(Entity target, ItemStack stack) {
        if (target == null || stack == null || stack.isEmpty()) {
            return false;
        }

        ItemStack copy = stack.copy();
        if (target instanceof Player player) {
            player.drop(copy, false, true);
            return true;
        }
        if (target.level() instanceof ServerLevel level) {
            target.spawnAtLocation(level, copy);
            return true;
        }
        return false;
    }

    public static int countMatching(Entity target, ItemStack template, String tag, String matchMode, GraphDataContext context) {
        if (target == null) {
            return 0;
        }
        Counter counter = new Counter(template, tag, matchMode, context);
        visitPrimaryStorage(target, counter);
        return counter.count;
    }

    public static int removeMatching(Entity target, ItemStack template, String tag, int requestedCount, String matchMode, ExecutionContext context) {
        if (target == null) {
            return 0;
        }
        Remover remover = new Remover(template, tag, requestedCount, matchMode, context);
        visitPrimaryStorage(target, remover);
        return remover.removed;
    }

    public static ItemStack insertIntoPrimaryStorage(Entity target, ItemStack stack) {
        if (target == null || stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remaining = stack.copy();
        if (target instanceof Player player) {
            Container inventory = player.getInventory();
            return insertIntoContainer(inventory, remaining, Math.min(36, inventory.getContainerSize()));
        }
        if (target instanceof Container container) {
            return insertIntoContainer(container, remaining, container.getContainerSize());
        }

        ResourceHandler<ItemResource> handler = target.getCapability(Capabilities.Item.ENTITY);
        if (handler != null) {
            return insertIntoHandler(handler, remaining);
        }
        return remaining;
    }

    public static List<ItemStack> snapshotPrimaryStorage(Entity target) {
        List<ItemStack> snapshot = new ArrayList<>();
        if (target == null) {
            return snapshot;
        }
        visitPrimaryStorage(target, access -> {
            ItemStack stack = access.get();
            if (stack != null && !stack.isEmpty()) {
                snapshot.add(stack.copy());
            }
        });
        return snapshot;
    }

    public static List<SlotRef> findMatchingSlots(Entity target, String scope, ItemStack template, String tag, String matchMode, boolean includeEmpty, int limit, GraphDataContext context) {
        List<SlotRef> result = new ArrayList<>();
        if (target == null) {
            return result;
        }

        int max = Math.max(0, limit);
        SlotRefVisitor collector = new SlotRefVisitor() {
            @Override
            public void visit(SlotRef ref, SlotAccess access) {
                if (!shouldContinue()) {
                    return;
                }
                if (matchesQuery(access.get(), template, tag, matchMode, includeEmpty, context)) {
                    result.add(ref);
                }
            }

            @Override
            public boolean shouldContinue() {
                return max == 0 || result.size() < max;
            }
        };

        switch (normalizeClearScope(scope)) {
            case CLEAR_SCOPE_INVENTORY -> visitPrimaryStorageSlots(target, collector);
            case CLEAR_SCOPE_EQUIPMENT -> visitEquipmentSlots(target, collector);
            default -> {
                visitPrimaryStorageSlots(target, collector);
                if (collector.shouldContinue()) {
                    visitEquipmentSlots(target, collector);
                }
            }
        }
        return result;
    }

    public static int clearSlots(Entity target, String scope) {
        if (target == null) {
            return 0;
        }
        String normalized = normalizeClearScope(scope);
        int removed = 0;
        if (CLEAR_SCOPE_INVENTORY.equals(normalized) || CLEAR_SCOPE_ALL.equals(normalized)) {
            removed += clearPrimaryStorage(target);
        }
        if (CLEAR_SCOPE_EQUIPMENT.equals(normalized) || CLEAR_SCOPE_ALL.equals(normalized)) {
            removed += clearEquipment(target);
        }
        return removed;
    }

    public static String normalizeClearScope(String scope) {
        if (scope == null) {
            return CLEAR_SCOPE_ALL;
        }
        return switch (scope.trim().toLowerCase(java.util.Locale.ROOT)) {
            case CLEAR_SCOPE_INVENTORY -> CLEAR_SCOPE_INVENTORY;
            case CLEAR_SCOPE_EQUIPMENT -> CLEAR_SCOPE_EQUIPMENT;
            default -> CLEAR_SCOPE_ALL;
        };
    }

    public static SlotRef getSlotRef(ExecutionContext context, String portId) {
        if (context == null || portId == null) {
            return SlotRef.DEFAULT;
        }
        Object value = context.getInputValue(portId);
        if (value == null) {
            value = context.getStaticInput(portId);
        }
        SlotRef slotRef = SlotRef.from(value);
        return slotRef != null ? slotRef : SlotRef.DEFAULT;
    }

    private static SlotAccess resolve(Entity target, SlotRef ref) {
        if (target == null || ref == null) {
            return null;
        }
        return switch (ref.space()) {
            case SlotRef.PLAYER_INVENTORY -> playerInventory(target, ref.key());
            case SlotRef.EQUIPMENT -> equipment(target, ref.key());
            case SlotRef.CONTAINER -> container(target, ref.key());
            case SlotRef.ENTITY_ITEM_HANDLER -> entityItemHandler(target, ref.key());
            default -> null;
        };
    }

    private static SlotAccess playerInventory(Entity target, String key) {
        if (!(target instanceof Player player)) {
            return null;
        }
        int index = playerInventoryIndex(key, player.getInventory().getContainerSize());
        if (index < 0) {
            return null;
        }
        return new ContainerSlotAccess(player.getInventory(), index);
    }

    private static SlotAccess equipment(Entity target, String key) {
        if (!(target instanceof LivingEntity livingEntity)) {
            return null;
        }
        EquipmentSlot slot = equipmentSlot(key);
        if (slot == null) {
            return null;
        }
        return new SlotAccess() {
            @Override
            public ItemStack get() {
                return livingEntity.getItemBySlot(slot);
            }

            @Override
            public boolean set(ItemStack stack) {
                livingEntity.setItemSlot(slot, stack != null ? stack.copy() : ItemStack.EMPTY);
                return true;
            }
        };
    }

    private static SlotAccess container(Entity target, String key) {
        if (!(target instanceof Container container)) {
            return null;
        }
        int index = parseSlotIndex(key);
        if (index < 0 || index >= container.getContainerSize()) {
            return null;
        }
        return new ContainerSlotAccess(container, index);
    }

    private static SlotAccess entityItemHandler(Entity target, String key) {
        ResourceHandler<ItemResource> handler = target.getCapability(Capabilities.Item.ENTITY);
        if (handler == null) {
            return null;
        }
        int index = parseSlotIndex(key);
        if (index < 0 || index >= handler.size()) {
            return null;
        }
        return new ResourceHandlerSlotAccess(handler, index);
    }

    private static int playerInventoryIndex(String key, int size) {
        String safeKey = key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT);
        int index;
        if (safeKey.startsWith("hotbar.")) {
            index = parseSuffix(safeKey, "hotbar.");
            return index >= 0 && index <= 8 && index < size ? index : -1;
        }
        if (safeKey.startsWith("main.")) {
            int main = parseSuffix(safeKey, "main.");
            index = main >= 0 ? 9 + main : -1;
            return main >= 0 && main <= 26 && index < size ? index : -1;
        }
        if (safeKey.startsWith("inventory.")) {
            index = parseSuffix(safeKey, "inventory.");
            return index >= 0 && index < size ? index : -1;
        }
        if (safeKey.startsWith("raw.")) {
            index = parseSuffix(safeKey, "raw.");
            return index >= 0 && index < size ? index : -1;
        }
        index = parseSlotIndex(safeKey);
        return index >= 0 && index < size ? index : -1;
    }

    private static EquipmentSlot equipmentSlot(String key) {
        String compact = SlotRef.compact(key);
        compact = switch (compact) {
            case "helmet" -> "head";
            case "chestplate" -> "chest";
            case "leggings" -> "legs";
            case "boots" -> "feet";
            default -> compact;
        };
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (SlotRef.compact(slot.name()).equals(compact)) {
                return slot;
            }
        }
        return null;
    }

    private static int parseSlotIndex(String key) {
        String safeKey = key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT);
        if (safeKey.startsWith("slot.")) {
            safeKey = safeKey.substring("slot.".length());
        } else if (safeKey.startsWith("index.")) {
            safeKey = safeKey.substring("index.".length());
        }
        try {
            return Integer.parseInt(safeKey);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static int parseSuffix(String key, String prefix) {
        try {
            return Integer.parseInt(key.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void visitPrimaryStorage(Entity target, StorageVisitor visitor) {
        if (target instanceof Player player) {
            Container inventory = player.getInventory();
            int size = Math.min(36, inventory.getContainerSize());
            for (int i = 0; i < size && visitor.shouldContinue(); i++) {
                visitor.visit(new ContainerSlotAccess(inventory, i));
            }
            return;
        }
        if (target instanceof Container container) {
            for (int i = 0; i < container.getContainerSize() && visitor.shouldContinue(); i++) {
                visitor.visit(new ContainerSlotAccess(container, i));
            }
            return;
        }

        ResourceHandler<ItemResource> handler = target.getCapability(Capabilities.Item.ENTITY);
        if (handler != null) {
            for (int i = 0; i < handler.size() && visitor.shouldContinue(); i++) {
                visitor.visit(new ResourceHandlerSlotAccess(handler, i));
            }
        }
    }

    private static void visitPrimaryStorageSlots(Entity target, SlotRefVisitor visitor) {
        if (target instanceof Player player) {
            Container inventory = player.getInventory();
            int size = Math.min(36, inventory.getContainerSize());
            for (int i = 0; i < size && visitor.shouldContinue(); i++) {
                visitor.visit(new SlotRef(SlotRef.PLAYER_INVENTORY, "inventory." + i), new ContainerSlotAccess(inventory, i));
            }
            return;
        }
        if (target instanceof Container container) {
            for (int i = 0; i < container.getContainerSize() && visitor.shouldContinue(); i++) {
                visitor.visit(new SlotRef(SlotRef.CONTAINER, "slot." + i), new ContainerSlotAccess(container, i));
            }
            return;
        }

        ResourceHandler<ItemResource> handler = target.getCapability(Capabilities.Item.ENTITY);
        if (handler != null) {
            for (int i = 0; i < handler.size() && visitor.shouldContinue(); i++) {
                visitor.visit(new SlotRef(SlotRef.ENTITY_ITEM_HANDLER, "slot." + i), new ResourceHandlerSlotAccess(handler, i));
            }
        }
    }

    private static void visitEquipmentSlots(Entity target, SlotRefVisitor visitor) {
        if (!(target instanceof LivingEntity livingEntity)) {
            return;
        }
        EquipmentSlot[] slots = target instanceof Player ? PLAYER_EQUIPMENT_SLOTS : LIVING_EQUIPMENT_SLOTS;
        for (EquipmentSlot slot : slots) {
            if (!visitor.shouldContinue()) {
                return;
            }
            visitor.visit(new SlotRef(SlotRef.EQUIPMENT, slot.name().toLowerCase(Locale.ROOT)), new SlotAccess() {
                @Override
                public ItemStack get() {
                    return livingEntity.getItemBySlot(slot);
                }

                @Override
                public boolean set(ItemStack stack) {
                    livingEntity.setItemSlot(slot, stack != null ? stack.copy() : ItemStack.EMPTY);
                    return true;
                }
            });
        }
    }

    private static ItemStack insertIntoContainer(Container container, ItemStack remaining, int size) {
        if (container == null || remaining == null || remaining.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int slotCount = Math.max(0, Math.min(size, container.getContainerSize()));
        boolean changed = false;

        for (int i = 0; i < slotCount && !remaining.isEmpty(); i++) {
            ItemStack current = container.getItem(i);
            if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, remaining)) {
                continue;
            }
            int limit = Math.min(container.getMaxStackSize(remaining), current.getMaxStackSize());
            int movable = Math.min(remaining.getCount(), Math.max(0, limit - current.getCount()));
            if (movable <= 0) {
                continue;
            }
            current.grow(movable);
            remaining.shrink(movable);
            container.setItem(i, current);
            changed = true;
        }

        for (int i = 0; i < slotCount && !remaining.isEmpty(); i++) {
            if (!container.getItem(i).isEmpty()) {
                continue;
            }
            int movable = Math.min(remaining.getCount(), Math.min(container.getMaxStackSize(remaining), remaining.getMaxStackSize()));
            if (movable <= 0) {
                continue;
            }
            container.setItem(i, remaining.copyWithCount(movable));
            remaining.shrink(movable);
            changed = true;
        }

        if (changed) {
            container.setChanged();
        }
        return remaining.isEmpty() ? ItemStack.EMPTY : remaining.copy();
    }

    private static ItemStack insertIntoHandler(ResourceHandler<ItemResource> handler, ItemStack remaining) {
        if (handler == null || remaining == null || remaining.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < handler.size() && !remaining.isEmpty(); i++) {
            try (Transaction transaction = Transaction.openRoot()) {
                int inserted = handler.insert(i, ItemResource.of(remaining), remaining.getCount(), transaction);
                if (inserted <= 0) {
                    continue;
                }
                transaction.commit();
                remaining.shrink(inserted);
            }
        }
        return remaining.isEmpty() ? ItemStack.EMPTY : remaining.copy();
    }

    private static int clearPrimaryStorage(Entity target) {
        int removed = 0;
        if (target instanceof Player player) {
            Container inventory = player.getInventory();
            int size = Math.min(36, inventory.getContainerSize());
            for (int i = 0; i < size; i++) {
                removed += clearSlot(new ContainerSlotAccess(inventory, i));
            }
            return removed;
        }
        if (target instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                removed += clearSlot(new ContainerSlotAccess(container, i));
            }
            return removed;
        }

        ResourceHandler<ItemResource> handler = target.getCapability(Capabilities.Item.ENTITY);
        if (handler != null) {
            for (int i = 0; i < handler.size(); i++) {
                removed += clearSlot(new ResourceHandlerSlotAccess(handler, i));
            }
        }
        return removed;
    }

    private static int clearEquipment(Entity target) {
        if (!(target instanceof LivingEntity livingEntity)) {
            return 0;
        }
        int removed = 0;
        EquipmentSlot[] slots = target instanceof Player ? PLAYER_EQUIPMENT_SLOTS : LIVING_EQUIPMENT_SLOTS;
        for (EquipmentSlot slot : slots) {
            ItemStack current = livingEntity.getItemBySlot(slot);
            if (current.isEmpty()) {
                continue;
            }
            removed += current.getCount();
            livingEntity.setItemSlot(slot, ItemStack.EMPTY);
        }
        return removed;
    }

    private static int clearSlot(SlotAccess access) {
        ItemStack current = access.get();
        if (current.isEmpty()) {
            return 0;
        }
        int removed = current.getCount();
        return access.set(ItemStack.EMPTY) ? removed : 0;
    }

    private interface StorageVisitor {
        void visit(SlotAccess access);

        default boolean shouldContinue() {
            return true;
        }
    }

    private static final class Counter implements StorageVisitor {
        private final ItemStack template;
        private final String tag;
        private final String matchMode;
        private final GraphDataContext context;
        private int count;

        private Counter(ItemStack template, String tag, String matchMode, GraphDataContext context) {
            this.template = template;
            this.tag = tag;
            this.matchMode = matchMode;
            this.context = context;
        }

        @Override
        public void visit(SlotAccess access) {
            ItemStack current = access.get();
            if (matches(current, template, tag, matchMode, context)) {
                count += current.getCount();
            }
        }
    }

    private static final class Remover implements StorageVisitor {
        private final ItemStack template;
        private final String tag;
        private final int requestedCount;
        private final String matchMode;
        private final ExecutionContext context;
        private int removed;

        private Remover(ItemStack template, String tag, int requestedCount, String matchMode, ExecutionContext context) {
            this.template = template;
            this.tag = tag;
            this.requestedCount = Math.max(0, requestedCount);
            this.matchMode = matchMode;
            this.context = context;
        }

        @Override
        public void visit(SlotAccess access) {
            if (!shouldContinue()) {
                return;
            }
            ItemStack current = access.get();
            if (!matches(current, template, tag, matchMode, context)) {
                return;
            }
            int remaining = requestedCount == 0 ? current.getCount() : requestedCount - removed;
            ItemStack extracted = access.extract(remaining);
            removed += extracted.getCount();
        }

        @Override
        public boolean shouldContinue() {
            return requestedCount == 0 || removed < requestedCount;
        }
    }

    private static boolean matches(ItemStack current, ItemStack template, String tag, String matchMode, GraphDataContext context) {
        if (current == null || current.isEmpty()) {
            return false;
        }
        if (tag != null && !tag.isBlank()) {
            return ValueTagUtils.hasTag(current, tag, context);
        }
        return ValueMatchUtils.itemStackMatches(current, template, matchMode, context);
    }

    private static boolean matchesQuery(ItemStack current, ItemStack template, String tag, String matchMode, boolean includeEmpty, GraphDataContext context) {
        if (current == null || current.isEmpty()) {
            return includeEmpty;
        }
        if (tag != null && !tag.isBlank()) {
            return ValueTagUtils.hasTag(current, tag, context);
        }
        if (template != null && !template.isEmpty()) {
            return ValueMatchUtils.itemStackMatches(current, template, matchMode, context);
        }
        return true;
    }

    private interface SlotRefVisitor {
        void visit(SlotRef ref, SlotAccess access);

        default boolean shouldContinue() {
            return true;
        }
    }

    private interface SlotAccess {
        ItemStack get();

        boolean set(ItemStack stack);

        default ItemStack extract(int requestedCount) {
            ItemStack current = get();
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int amount = requestedCount <= 0 ? current.getCount() : Math.min(requestedCount, current.getCount());
            ItemStack remaining = current.copy();
            ItemStack extracted = remaining.split(amount);
            return set(remaining) ? extracted : ItemStack.EMPTY;
        }
    }

    private static final class ContainerSlotAccess implements SlotAccess {
        private final Container container;
        private final int index;

        private ContainerSlotAccess(Container container, int index) {
            this.container = container;
            this.index = index;
        }

        @Override
        public ItemStack get() {
            return container.getItem(index);
        }

        @Override
        public boolean set(ItemStack stack) {
            container.setItem(index, stack != null ? stack.copy() : ItemStack.EMPTY);
            container.setChanged();
            return true;
        }

        @Override
        public ItemStack extract(int requestedCount) {
            ItemStack current = container.getItem(index);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int amount = requestedCount <= 0 ? current.getCount() : Math.min(requestedCount, current.getCount());
            ItemStack extracted = container.removeItem(index, amount);
            container.setChanged();
            return extracted;
        }
    }

    private static final class ResourceHandlerSlotAccess implements SlotAccess {
        private final ResourceHandler<ItemResource> handler;
        private final int index;

        private ResourceHandlerSlotAccess(ResourceHandler<ItemResource> handler, int index) {
            this.handler = handler;
            this.index = index;
        }

        @Override
        public ItemStack get() {
            return ItemUtil.getStack(handler, index);
        }

        @Override
        public boolean set(ItemStack stack) {
            ItemStack replacement = stack != null ? stack.copy() : ItemStack.EMPTY;
            try (Transaction transaction = Transaction.openRoot()) {
                ItemStack current = ItemUtil.getStack(handler, index);
                if (!current.isEmpty()) {
                    int extracted = handler.extract(index, ItemResource.of(current), current.getCount(), transaction);
                    if (extracted != current.getCount()) {
                        return false;
                    }
                }
                if (!replacement.isEmpty()) {
                    int inserted = handler.insert(index, ItemResource.of(replacement), replacement.getCount(), transaction);
                    if (inserted != replacement.getCount()) {
                        return false;
                    }
                }
                transaction.commit();
                return true;
            }
        }

        @Override
        public ItemStack extract(int requestedCount) {
            ItemStack current = ItemUtil.getStack(handler, index);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int amount = requestedCount <= 0 ? current.getCount() : Math.min(requestedCount, current.getCount());
            try (Transaction transaction = Transaction.openRoot()) {
                int extracted = handler.extract(index, ItemResource.of(current), amount, transaction);
                if (extracted <= 0) {
                    return ItemStack.EMPTY;
                }
                transaction.commit();
                return current.copyWithCount(extracted);
            }
        }
    }
}
