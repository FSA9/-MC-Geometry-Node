package com.mine.geometry_node.core.engine.system.entity.edit;

import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Function;

/** Prevalidated, reversible slot batch. Setters must defer external side effects until committed(). */
public final class EntityEditorItemTransfer<T> {
    /** Created without side effects; registered for rollback before spawn(), including partial spawn failure. */
    public interface Drop {
        void spawn();
        /** Check that later spawn hooks have not removed or changed this output. */
        default void validate() {}
        void remove();
        default void committed() {}
    }
    public interface Slot<T> {
        T get();
        boolean accepts(T stack);
        void set(T stack);
        default void committed(T before, T after) {}
    }
    private final List<Slot<T>> slots;
    private final List<T> before, after;
    private final ConservedItemStacks.Stacks<T> stacks;
    private final List<T> drops;
    private final Function<T, Drop> dropFactory;
    private final List<Drop> spawned = new ArrayList<>();
    private boolean written;
    private EntityEditorItemTransfer(List<Slot<T>> slots, List<T> before, List<T> after, List<T> drops,
                                     Function<T, Drop> dropFactory, ConservedItemStacks.Stacks<T> stacks) {
        this.slots = slots; this.before = before; this.after = after; this.stacks = stacks;
        this.drops = drops; this.dropFactory = dropFactory;
    }
    public static EntityEditorItemTransfer<ItemStack> prepare(List<Slot<ItemStack>> slots, List<ItemStack> requested) {
        return prepare(slots, requested, ConservedItemStacks.ITEM_STACKS);
    }
    /** creative must come from current server authority, never from the submitted snapshot capability. */
    public static EntityEditorItemTransfer<ItemStack> prepare(List<Slot<ItemStack>> slots, List<ItemStack> requested,
            List<ItemStack> drops, boolean creative, Function<ItemStack, Drop> dropFactory) {
        return prepare(slots, requested, drops, creative, dropFactory, ConservedItemStacks.ITEM_STACKS);
    }
    static <T> EntityEditorItemTransfer<T> prepare(List<Slot<T>> slots, List<T> requested, ConservedItemStacks.Stacks<T> stacks) {
        return prepare(slots, requested, List.of(), false, stack -> { throw new IllegalStateException("No drop factory"); }, stacks);
    }
    static <T> EntityEditorItemTransfer<T> prepare(List<Slot<T>> slots, List<T> requested, List<T> drops,
            boolean creative, Function<T, Drop> dropFactory, ConservedItemStacks.Stacks<T> stacks) {
        List<Slot<T>> targets = List.copyOf(slots);
        List<T> source = targets.stream().map(slot -> stacks.copy(slot.get())).toList();
        var resolution = ConservedItemStacks.resolve(source, requested, drops, creative, stacks);
        List<T> resolved = resolution.slots();
        for (int i = 0; i < targets.size(); i++)
            // Retaining an existing nonstandard stack is safe; new placements must satisfy current entity rules.
            if (!stacks.matches(source.get(i), resolved.get(i)) && !targets.get(i).accepts(resolved.get(i)))
                throw new IllegalArgumentException("Invalid destination slot");
        return new EntityEditorItemTransfer<>(targets, source, resolved, resolution.drops(), Objects.requireNonNull(dropFactory), stacks);
    }
    public void apply() {
        if (written) throw new IllegalStateException("Transfer already applied");
        for (int i = 0; i < slots.size(); i++)
            if (!stacks.matches(slots.get(i).get(), before.get(i))) throw new IllegalStateException("Slot changed after validation");
        written = true;
        try {
            for (int i = 0; i < slots.size(); i++)
                if (!stacks.matches(before.get(i), after.get(i))) slots.get(i).set(stacks.copy(after.get(i)));
            for (int i = 0; i < slots.size(); i++)
                if (!stacks.matches(slots.get(i).get(), after.get(i))) throw new IllegalStateException("Slot rejected transfer");
            for (T stack : drops) {
                Drop drop = Objects.requireNonNull(dropFactory.apply(stacks.copy(stack)));
                spawned.add(drop);
                drop.spawn();
            }
            for (Drop drop : spawned) drop.validate();
            // World spawn callbacks run synchronously and can also touch an owner's slots.
            for (int i = 0; i < slots.size(); i++)
                if (!stacks.matches(slots.get(i).get(), after.get(i))) throw new IllegalStateException("Slot changed while spawning drops");
        } catch (RuntimeException failure) {
            try { rollback(); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }
    public void rollback() {
        if (!written) return;
        RuntimeException failure = null;
        for (int i = spawned.size() - 1; i >= 0; i--) {
            try { spawned.get(i).remove(); spawned.remove(i); }
            catch (RuntimeException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        // Do not restore source stacks while an unremoved drop could still be picked up.
        if (failure != null) throw failure;
        for (int i = slots.size() - 1; i >= 0; i--) {
            try { if (!stacks.matches(slots.get(i).get(), before.get(i))) slots.get(i).set(stacks.copy(before.get(i))); }
            catch (RuntimeException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        if (failure != null) throw failure;
        written = false;
    }
    /** Commit notifications cannot turn a completed conserved transfer into a failed/retryable request. */
    public void notifyCommitted() {
        if (!written) return;
        for (Drop drop : spawned) {
            try { drop.committed(); }
            catch (RuntimeException failure) {
                System.getLogger(EntityEditorItemTransfer.class.getName()).log(System.Logger.Level.WARNING, "Drop commit notification failed", failure);
            }
        }
        for (int i = 0; i < slots.size(); i++) {
            if (stacks.matches(before.get(i), after.get(i))) continue;
            try { slots.get(i).committed(stacks.copy(before.get(i)), stacks.copy(after.get(i))); }
            catch (RuntimeException failure) {
                System.getLogger(EntityEditorItemTransfer.class.getName()).log(System.Logger.Level.WARNING, "Equipment commit notification failed", failure);
            }
        }
    }
}
