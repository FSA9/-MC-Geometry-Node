package com.mine.geometry_node.client.ui.components.integration.equipment;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** UI-thread detached transaction. Never reads or changes a live entity or player inventory. */
public final class EntityEquipmentDraft {
    public static final List<String> EQUIPMENT_SLOTS =
            List.of("head", "chest", "legs", "feet", "mainhand", "offhand");
    public static final int INVENTORY_SIZE = 36;
    public static final int MAX_DROPPED_STACKS = 128;

    public enum Result { CHANGED, UNCHANGED, INCOMPATIBLE, FULL, UNSUPPORTED }

    /** Exactly one of equipment (named slot) and inventory (native player index) is selected. */
    public record Slot(String equipment, int inventory) {
        public Slot {
            if (equipment == null ? inventory < 0 || inventory >= INVENTORY_SIZE
                    : inventory != -1 || !EQUIPMENT_SLOTS.contains(equipment)) {
                throw new IllegalArgumentException("Invalid equipment/inventory slot");
            }
        }
        public static Slot equipment(String name) { return new Slot(Objects.requireNonNull(name), -1); }
        public static Slot inventory(int index) { return new Slot(null, index); }
        public boolean isArmor() { return equipment != null && !equipment.endsWith("hand"); }
    }

    /** Detached ghost values for the admitted drag slots, in visit order. Accessors copy stacks too. */
    public record Distribution(Map<Slot, ItemStack> slots, ItemStack carried) {
        public Distribution {
            slots = copySlots(slots);
            carried = copy(carried);
        }
        @Override public Map<Slot, ItemStack> slots() { return copySlots(slots); }
        @Override public ItemStack carried() { return copy(carried); }
        private static Map<Slot, ItemStack> copySlots(Map<Slot, ItemStack> slots) {
            Map<Slot, ItemStack> result = new LinkedHashMap<>();
            slots.forEach((slot, stack) -> result.put(slot, copy(stack)));
            return Collections.unmodifiableMap(result);
        }
    }

    private Map<String, ItemStack> equipment;
    private List<ItemStack> inventory;
    private ItemStack carried = ItemStack.EMPTY;
    private Slot origin;
    private boolean creative;
    private List<ItemStack> dropped = new ArrayList<>();

    /** Inventory uses native indices: hotbar 0..8, main inventory 9..35.
     * Equipment keys are the server's supported-slot capability, including explicit empty stacks. */
    public EntityEquipmentDraft(Map<String, ItemStack> equipment, List<ItemStack> inventory) {
        Objects.requireNonNull(equipment, "equipment");
        Objects.requireNonNull(inventory, "inventory");
        if (inventory.size() != INVENTORY_SIZE) throw new IllegalArgumentException("Expected 36 inventory slots");
        if (!EQUIPMENT_SLOTS.containsAll(equipment.keySet()))
            throw new IllegalArgumentException("Unsupported equipment slot");
        this.equipment = new LinkedHashMap<>();
        for (String name : EQUIPMENT_SLOTS)
            if (equipment.containsKey(name)) this.equipment.put(name, copy(equipment.get(name)));
        this.inventory = new ArrayList<>(INVENTORY_SIZE);
        for (ItemStack stack : inventory) this.inventory.add(copy(stack));
    }

    public Map<String, ItemStack> equipment() {
        Map<String, ItemStack> result = new LinkedHashMap<>();
        equipment.forEach((name, stack) -> result.put(name, copy(stack)));
        return result;
    }

    public List<ItemStack> inventory() { return inventory.stream().map(EntityEquipmentDraft::copy).toList(); }
    public ItemStack carried() { return copy(carried); }
    public void setCreative(boolean creative) { this.creative = creative; }
    public boolean creative() { return creative; }
    public List<ItemStack> dropped() { return dropped.stream().map(EntityEquipmentDraft::copy).toList(); }

    /** Confirm a completed operation without replacing the hosting editor or its preview views. */
    public void acceptCommitted(Map<String, ItemStack> equipment, List<ItemStack> inventory, boolean creative) {
        if (hasCarried()) throw new IllegalStateException("Cannot replace an unfinished cursor operation");
        EntityEquipmentDraft confirmed = new EntityEquipmentDraft(equipment, inventory);
        confirmed.setCreative(creative);
        adopt(confirmed);
    }
    public boolean hasCarried() { return !carried.isEmpty(); }
    public boolean supports(Slot slot) { return slot.equipment() == null || equipment.containsKey(slot.equipment()); }
    public ItemStack stack(Slot slot) { return copy(read(Objects.requireNonNull(slot))); }

    public static boolean accepts(Slot slot, ItemStack stack) {
        if (stack.isEmpty() || !slot.isArmor()) return true;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.byName(slot.equipment());
    }

    public Result click(Slot slot, boolean rightClick) {
        Objects.requireNonNull(slot);
        if (!supports(slot)) return Result.UNSUPPORTED;
        ItemStack existing = read(slot);
        if (carried.isEmpty()) {
            if (existing.isEmpty()) return Result.UNCHANGED;
            int amount = rightClick ? (existing.getCount() / 2 + existing.getCount() % 2) : existing.getCount();
            carried = existing.copyWithCount(amount);
            write(slot, existing.copyWithCount(existing.getCount() - amount));
            origin = slot;
            return Result.CHANGED;
        }
        if (!accepts(slot, carried)) return Result.INCOMPATIBLE;
        int limit = limit(slot, carried);
        if (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, carried)) {
            int amount = Math.min(rightClick ? 1 : carried.getCount(), limit - existing.getCount());
            if (amount <= 0) return Result.FULL;
            write(slot, carried.copyWithCount(existing.getCount() + amount));
            consume(amount);
            return Result.CHANGED;
        }
        if (carried.getCount() > limit) return Result.FULL;
        write(slot, carried);
        carried = existing;
        return Result.CHANGED;
    }

    /** Entity equipment goes to reversed inventory order (8..0, 35..9).
     * Inventory first equips its matching empty armor/offhand slot, then crosses main/hotbar.
     * Plain items never auto-equip into mainhand. */
    public Result quickMove(Slot source) {
        Objects.requireNonNull(source);
        if (!supports(source)) return Result.UNSUPPORTED;
        ItemStack moving = stack(source);
        if (moving.isEmpty()) return Result.UNCHANGED;
        int before = moving.getCount();
        if (source.equipment() != null) {
            insert(moving, inventoryOrder(true));
        } else {
            Equippable equippable = moving.get(DataComponents.EQUIPPABLE);
            if (equippable != null) {
                String name = equippable.slot().getName();
                if (EQUIPMENT_SLOTS.contains(name) && !name.equals("mainhand")) {
                    Slot target = Slot.equipment(name);
                    if (supports(target) && read(target).isEmpty()) insert(moving, List.of(target));
                }
            }
            // A vanilla QUICK_MOVE repeats after filling the equipment slot, so move the remainder too.
            if (!moving.isEmpty()) insert(moving, inventoryRegion(source.inventory() < 9));
        }
        if (moving.getCount() == before) return Result.FULL;
        write(source, moving);
        return Result.CHANGED;
    }

    public Result quickMoveMatching(Slot source) { return quickMoveMatching(source, stack(source)); }

    /** Shift-double-click uses the pre-pickup prototype even when source is now empty.
     * Like AbstractContainerScreen, scan the entire owning container once in canonical order:
     * all 36 inventory slots, or supported entity equipment. Recheck each slot when visited,
     * including items moved into later slots during this same scan. */
    public Result quickMoveMatching(Slot source, ItemStack prototype) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(prototype);
        if (!supports(source)) return Result.UNSUPPORTED;
        if (prototype.isEmpty()) return Result.UNCHANGED;
        boolean changed = false;
        boolean matched = false;
        for (Slot slot : canonicalOrder()) {
            if (sameOwner(source, slot) && same(read(slot), prototype)) {
                matched = true;
                changed |= quickMove(slot) == Result.CHANGED;
            }
        }
        return changed ? Result.CHANGED : matched ? Result.FULL : Result.UNCHANGED;
    }

    /** Source is the hotbar/F stack and target is the hovered slot. Oversized sources split at
     * target's cap; displaced items return to inventory, then the bounded drop queue. All or nothing. */
    public Result swap(Slot source, Slot target) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(target);
        if (!supports(source) || !supports(target)) return Result.UNSUPPORTED;
        if (source.equals(target)) return Result.UNCHANGED;
        ItemStack from = read(source), to = read(target);
        if (from.isEmpty() && to.isEmpty()) return Result.UNCHANGED;
        if (!accepts(target, from)) return Result.INCOMPATIBLE;
        if (from.isEmpty() || from.getCount() <= limit(target, from)) {
            if (!accepts(source, to)) return Result.INCOMPATIBLE;
            if (!to.isEmpty() && to.getCount() > limit(source, to)) return Result.FULL;
            write(source, to);
            write(target, from);
            return Result.CHANGED;
        }
        EntityEquipmentDraft staged = detached();
        int amount = limit(target, from);
        staged.write(target, from.copyWithCount(amount));
        staged.write(source, from.copyWithCount(from.getCount() - amount));
        ItemStack displaced = copy(to);
        // Inventory.add has native hotbar-first order; never put displaced items back into target.
        List<Slot> destinations = new ArrayList<>();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            Slot slot = Slot.inventory(i);
            if (!slot.equals(target)) destinations.add(slot);
        }
        staged.insert(displaced, destinations);
        if (!displaced.isEmpty()) {
            if (staged.dropped.size() == MAX_DROPPED_STACKS) return Result.FULL;
            staged.dropped.add(copy(displaced));
        }
        adopt(staged);
        return Result.CHANGED;
    }

    /** Clone only the exact source prototype, with all components and its component stack maximum. */
    public Result cloneStack(Slot source) {
        Objects.requireNonNull(source);
        if (!supports(source)) return Result.UNSUPPORTED;
        if (!creative || hasCarried() || read(source).isEmpty()) return Result.UNCHANGED;
        carried = read(source).copyWithCount(read(source).getMaxStackSize());
        origin = source;
        return Result.CHANGED;
    }

    /** Null means outside-click dropping from the cursor; Q on a slot requires an empty cursor. */
    public Result drop(Slot slot, boolean all) {
        if (slot != null && !supports(slot)) return Result.UNSUPPORTED;
        if (slot != null && hasCarried()) return Result.UNCHANGED;
        ItemStack source = slot == null ? carried : read(slot);
        if (source.isEmpty()) return Result.UNCHANGED;
        if (dropped.size() == MAX_DROPPED_STACKS) return Result.FULL;
        int count = all ? source.getCount() : 1;
        dropped.add(source.copyWithCount(count));
        if (slot == null) consume(count);
        else write(slot, source.copyWithCount(source.getCount() - count));
        return Result.CHANGED;
    }

    /** Vanilla PICKUP_ALL: partial item stacks first, then full stacks, in canonical slot order. */
    public Result collect(boolean reverse) {
        if (!hasCarried()) return Result.UNCHANGED;
        List<Slot> slots = canonicalOrder();
        if (reverse) Collections.reverse(slots);
        int before = carried.getCount();
        for (int pass = 0; pass < 2; pass++) {
            for (Slot slot : slots) {
                ItemStack existing = read(slot);
                if (!same(existing, carried) || pass == 0 && existing.getCount() == existing.getMaxStackSize()) continue;
                int amount = Math.min(existing.getCount(), carried.getMaxStackSize() - carried.getCount());
                if (amount <= 0) continue;
                write(slot, existing.copyWithCount(existing.getCount() - amount));
                carried = carried.copyWithCount(carried.getCount() + amount);
            }
        }
        return before == carried.getCount() ? Result.UNCHANGED : Result.CHANGED;
    }

    /** Eligibility matches vanilla quick-craft admission, including matching full stacks.
     * Visited-slot count and available cursor count are applied by previewDistribution. */
    public boolean canDistribute(Slot slot, int button) {
        if (slot == null || button < 0 || button > 2 || button == 2 && !creative
                || !hasCarried() || !supports(slot) || !accepts(slot, carried)) return false;
        ItemStack existing = read(slot);
        return existing.isEmpty() || same(existing, carried) && existing.getCount() <= carried.getMaxStackSize();
    }

    /** Duplicate visits count once. Noncreative admission stops at cursor count. Capped portions
     * are left on the cursor rather than redistributed. Creative fill uses the original cursor
     * prototype and vanilla cursor subtraction (exhausted/negative remaining becomes empty).
     * A single creative slot is unchanged: vanilla delegates to PICKUP with button 2. */
    public Distribution previewDistribution(Collection<Slot> visited, int button) {
        Objects.requireNonNull(visited);
        LinkedHashSet<Slot> admitted = new LinkedHashSet<>();
        for (Slot slot : visited) {
            if (canDistribute(slot, button) && (button == 2 || admitted.size() < carried.getCount())) admitted.add(slot);
        }
        Map<Slot, ItemStack> result = new LinkedHashMap<>();
        if (admitted.isEmpty()) return new Distribution(result, carried);
        if (button == 2 && admitted.size() == 1) {
            Slot slot = admitted.iterator().next();
            result.put(slot, read(slot));
            return new Distribution(result, carried);
        }
        int portion = button == 0 ? carried.getCount() / admitted.size() : button == 1 ? 1 : carried.getMaxStackSize();
        int remaining = carried.getCount();
        for (Slot slot : admitted) {
            int oldCount = read(slot).getCount();
            int newCount = Math.min(oldCount + portion, limit(slot, carried));
            // Never shrink an initially over-cap stack supplied by a server snapshot.
            newCount = Math.max(oldCount, newCount);
            remaining -= newCount - oldCount;
            result.put(slot, carried.copyWithCount(newCount));
        }
        return new Distribution(result, carried.copyWithCount(Math.max(0, remaining)));
    }

    public Result distribute(Collection<Slot> visited, int button) {
        Distribution preview = previewDistribution(visited, button);
        boolean changed = !ItemStack.matches(carried, preview.carried);
        for (var entry : preview.slots.entrySet()) {
            changed |= !ItemStack.matches(read(entry.getKey()), entry.getValue());
            write(entry.getKey(), entry.getValue());
        }
        carried = copy(preview.carried);
        if (carried.isEmpty()) origin = null;
        return changed ? Result.CHANGED : Result.UNCHANGED;
    }

    /** Original slot first, then compatible inventory stacks, then empty inventory slots.
     * Work is staged on a separate draft: failure leaves every stack, component and origin intact. */
    public boolean returnCarried() {
        if (carried.isEmpty()) return true;
        EntityEquipmentDraft staged = detached();
        if (origin != null) staged.returnInto(origin);
        for (Slot slot : inventoryOrder(false)) {
            if (!staged.read(slot).isEmpty()) staged.returnInto(slot);
        }
        for (Slot slot : inventoryOrder(false)) {
            if (staged.read(slot).isEmpty()) staged.returnInto(slot);
        }
        if (!staged.carried.isEmpty()) return false;
        adopt(staged);
        return true;
    }

    private void returnInto(Slot slot) {
        if (carried.isEmpty() || !supports(slot) || !accepts(slot, carried)) return;
        ItemStack existing = read(slot);
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, carried)) return;
        int amount = Math.min(carried.getCount(), limit(slot, carried) - existing.getCount());
        if (amount <= 0) return;
        write(slot, carried.copyWithCount(existing.getCount() + amount));
        consume(amount);
    }

    private void consume(int amount) {
        carried = carried.copyWithCount(carried.getCount() - amount);
        if (carried.isEmpty()) { carried = ItemStack.EMPTY; origin = null; }
    }

    private EntityEquipmentDraft detached() {
        EntityEquipmentDraft result = new EntityEquipmentDraft(equipment, inventory);
        result.carried = copy(carried);
        result.origin = origin;
        result.creative = creative;
        result.dropped = new ArrayList<>(dropped());
        return result;
    }
    private void adopt(EntityEquipmentDraft other) {
        equipment = other.equipment;
        inventory = other.inventory;
        carried = other.carried;
        origin = other.origin;
        creative = other.creative;
        dropped = other.dropped;
    }
    private static boolean same(ItemStack first, ItemStack second) {
        return !first.isEmpty() && !second.isEmpty() && ItemStack.isSameItemSameComponents(first, second);
    }
    private static boolean sameOwner(Slot first, Slot second) {
        return (first.equipment() == null) == (second.equipment() == null);
    }
    private List<Slot> canonicalOrder() {
        List<Slot> result = new ArrayList<>();
        for (String name : EQUIPMENT_SLOTS) if (equipment.containsKey(name)) result.add(Slot.equipment(name));
        result.addAll(inventoryOrder(false));
        return result;
    }
    private static List<Slot> inventoryRegion(boolean main) {
        List<Slot> result = new ArrayList<>();
        for (int i = main ? 9 : 0; i < (main ? INVENTORY_SIZE : 9); i++) result.add(Slot.inventory(i));
        return result;
    }
    private static List<Slot> inventoryOrder(boolean reverse) {
        List<Slot> result = inventoryRegion(true);
        result.addAll(inventoryRegion(false));
        if (reverse) Collections.reverse(result);
        return result;
    }
    /** Two passes preserve vanilla merge-before-empty behavior and all component data. */
    private void insert(ItemStack moving, List<Slot> destinations) {
        for (int pass = 0; pass < 2 && !moving.isEmpty(); pass++) {
            for (Slot slot : destinations) {
                if (moving.isEmpty()) break;
                ItemStack existing = read(slot);
                if (!accepts(slot, moving) || (pass == 0 ? !same(existing, moving) : !existing.isEmpty())) continue;
                int amount = Math.min(moving.getCount(), limit(slot, moving) - existing.getCount());
                if (amount <= 0) continue;
                write(slot, moving.copyWithCount(existing.getCount() + amount));
                moving.shrink(amount);
            }
        }
    }
    private static int limit(Slot slot, ItemStack stack) { return slot.isArmor() ? 1 : stack.getMaxStackSize(); }
    private ItemStack read(Slot slot) {
        return slot.equipment() == null ? inventory.get(slot.inventory()) : equipment.getOrDefault(slot.equipment(), ItemStack.EMPTY);
    }
    private void write(Slot slot, ItemStack stack) {
        if (slot.equipment() == null) inventory.set(slot.inventory(), copy(stack));
        else equipment.put(slot.equipment(), copy(stack));
    }
    private static ItemStack copy(ItemStack stack) { return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy(); }
}
