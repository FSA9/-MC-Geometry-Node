package com.mine.geometry_node.core.engine.system.entityeditor;

import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Resolves every destination from authoritative stacks. Client stacks are matching requests, never write values. */
public final class ConservedItemStacks {
    public static final int MAX_DROPS = 128;
    public record Resolution<T>(List<T> slots, List<T> drops) {}
    private ConservedItemStacks() {}
    interface Stacks<T> {
        boolean empty(T stack);
        T emptyStack();
        int count(T stack);
        int maximum(T stack);
        boolean sameItemAndComponents(T first, T second);
        T copyWithCount(T source, int count);
        default T copy(T source) { return empty(source) ? emptyStack() : copyWithCount(source, count(source)); }
        default boolean matches(T first, T second) {
            return empty(first) && empty(second) || !empty(first) && !empty(second)
                    && count(first) == count(second) && sameItemAndComponents(first, second);
        }
    }
    static final Stacks<ItemStack> ITEM_STACKS = new Stacks<>() {
        public boolean empty(ItemStack stack) { return stack.isEmpty(); }
        public ItemStack emptyStack() { return ItemStack.EMPTY; }
        public int count(ItemStack stack) { return stack.getCount(); }
        public int maximum(ItemStack stack) { return stack.getMaxStackSize(); }
        public boolean sameItemAndComponents(ItemStack first, ItemStack second) { return ItemStack.isSameItemSameComponents(first, second); }
        public ItemStack copyWithCount(ItemStack source, int count) { return source.copyWithCount(count); }
    };
    public static List<ItemStack> resolve(List<ItemStack> source, List<ItemStack> requested) {
        return resolve(source, requested, ITEM_STACKS);
    }
    public static Resolution<ItemStack> resolve(List<ItemStack> source, List<ItemStack> requested,
                                               List<ItemStack> drops, boolean creative) {
        return resolve(source, requested, drops, creative, ITEM_STACKS);
    }
    /** The algorithm is independent of host registries, so conservation can be tested without starting a game server. */
    static <T> List<T> resolve(List<T> source, List<T> requested, Stacks<T> stacks) {
        return resolve(source, requested, List.of(), false, stacks).slots();
    }
    /** Creative may clone or delete, but every output must match an original authoritative prototype. */
    static <T> Resolution<T> resolve(List<T> source, List<T> requested, List<T> drops, boolean creative, Stacks<T> stacks) {
        if (source.size() != requested.size()) throw new IllegalArgumentException("Slot count mismatch");
        if (drops.size() > MAX_DROPS) throw new IllegalArgumentException("Too many drops");
        for (T drop : drops)
            if (stacks.empty(Objects.requireNonNull(drop))) throw new IllegalArgumentException("Empty drop");
        int[] remaining = new int[source.size()];
        for (int i = 0; i < source.size(); i++) remaining[i] = stacks.empty(source.get(i)) ? 0 : stacks.count(source.get(i));
        List<T> destinations = new ArrayList<>(requested); destinations.addAll(drops);
        List<T> resolved = new ArrayList<>(destinations.size());
        for (T destination : destinations) {
            Objects.requireNonNull(destination);
            if (stacks.empty(destination)) { resolved.add(stacks.emptyStack()); continue; }
            int count = stacks.count(destination);
            if (count < 1 || count > stacks.maximum(destination)) throw new IllegalArgumentException("Invalid stack count");
            int needed = count; T prototype = null;
            for (int i = 0; i < source.size() && needed > 0; i++) {
                T candidate = source.get(i);
                if (stacks.empty(candidate) || !stacks.sameItemAndComponents(candidate, destination)) continue;
                if (prototype == null) prototype = candidate;
                if (creative) { needed = 0; break; }
                int take = Math.min(needed, remaining[i]); remaining[i] -= take; needed -= take;
            }
            if (needed != 0 || prototype == null) throw new IllegalArgumentException("Item creation or component change");
            // Limits also come from the authoritative prototype, including mod-specific stack limits.
            if (count > stacks.maximum(prototype)) throw new IllegalArgumentException("Stack exceeds authoritative maximum");
            resolved.add(stacks.copyWithCount(prototype, count));
        }
        if (!creative) for (int count : remaining) if (count != 0) throw new IllegalArgumentException("Item loss");
        return new Resolution<>(List.copyOf(resolved.subList(0, requested.size())),
                List.copyOf(resolved.subList(requested.size(), resolved.size())));
    }
}
