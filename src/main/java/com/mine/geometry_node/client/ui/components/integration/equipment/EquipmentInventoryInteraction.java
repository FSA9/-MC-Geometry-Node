package com.mine.geometry_node.client.ui.components.integration.equipment;

import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.function.Consumer;

/** Inventory pointer gestures over detached slots; independent of a window, layout and event API. */
final class EquipmentInventoryInteraction {
    private static final long DOUBLE_CLICK_NANOS = 250_000_000L;
    private final EntityEquipmentDraft draft;
    private final Consumer<EntityEquipmentDraft.Result> completed;
    private final Runnable presentationChanged;
    private final LinkedHashSet<EntityEquipmentDraft.Slot> visited = new LinkedHashSet<>();
    private EntityEquipmentDraft.Slot previousSlot, pressedSlot;
    private long previousTime;
    private int previousButton = -1, pressedButton = -1, inputButton = -1;
    private boolean dragging, doubleClick, shifted, pressed;
    private ItemStack quickMoved = ItemStack.EMPTY;

    EquipmentInventoryInteraction(EntityEquipmentDraft draft,
                                  Consumer<EntityEquipmentDraft.Result> completed, Runnable presentationChanged) {
        this.draft = Objects.requireNonNull(draft);
        this.completed = Objects.requireNonNull(completed);
        this.presentationChanged = Objects.requireNonNull(presentationChanged);
    }

    boolean press(EntityEquipmentDraft.Slot slot, int button, boolean shift, boolean clone, long time) {
        cancel();
        if (slot == null || !draft.supports(slot) || button < 0) return false;
        if (clone && !draft.creative()) return true;
        if (!clone && button > 1) return false;
        pressed = true;
        pressedSlot = slot;
        pressedButton = clone ? 2 : button;
        inputButton = button;
        shifted = shift;
        doubleClick = !clone && button == 0 && slot.equals(previousSlot) && previousButton == button
                && time >= previousTime && time - previousTime < DOUBLE_CLICK_NANOS;
        previousSlot = slot;
        previousButton = button;
        previousTime = time;
        if (doubleClick) {
            if (!shift && !draft.hasCarried()) completed.accept(draft.click(slot, false));
            return true; // The release chooses collection or shift bulk transfer.
        }
        if (clone && !draft.hasCarried()) {
            completed.accept(draft.cloneStack(slot));
        } else if (shift && !draft.hasCarried()) {
            completed.accept(quickMove(slot));
        } else if (!draft.hasCarried()) {
            completed.accept(draft.click(slot, button == 1));
        } else {
            dragging = true;
        }
        return true;
    }

    void move(EntityEquipmentDraft.Slot slot) {
        if (pressed && dragging) visit(slot);
    }

    private void visit(EntityEquipmentDraft.Slot slot) {
        if (slot == null || !draft.supports(slot) || !draft.canDistribute(slot, pressedButton)) return;
        if (pressedButton != 2 && draft.carried().getCount() <= visited.size()) return;
        if (visited.add(slot)) presentationChanged.run();
    }

    void release(EntityEquipmentDraft.Slot slot, int button) { release(slot, button, shifted); }

    void release(EntityEquipmentDraft.Slot slot, int button, boolean shift) {
        if (!pressed) return;
        EntityEquipmentDraft.Result result = EntityEquipmentDraft.Result.UNCHANGED;
        try {
            if (button != inputButton) return;
            if (doubleClick && button == 0 && pressedSlot.equals(slot)) {
                if (shift && !quickMoved.isEmpty())
                    result = draft.quickMoveMatching(pressedSlot, quickMoved);
                else if (!shift && draft.stack(slot).isEmpty()) result = draft.collect(false);
                previousSlot = null;
            } else if (dragging) {
                if (!visited.isEmpty()) result = draft.distribute(visited, pressedButton);
                // Without admitted drag slots, release chooses the ordinary click operation.
                else if (slot != null) {
                    if (pressedButton == 2) result = draft.cloneStack(slot);
                    else if (shift) result = quickMove(slot);
                    else result = draft.click(slot, pressedButton == 1);
                }
            }
        } finally {
            cancel();
        }
        completed.accept(result);
    }

    private EntityEquipmentDraft.Result quickMove(EntityEquipmentDraft.Slot slot) {
        ItemStack source = draft.stack(slot);
        if (!source.isEmpty()) quickMoved = source;
        return draft.quickMove(slot);
    }

    EntityEquipmentDraft.Distribution preview() {
        return dragging && !visited.isEmpty() ? draft.previewDistribution(visited, pressedButton) : null;
    }

    boolean active() { return pressed; }

    void cancel() {
        boolean hadPreview = dragging;
        visited.clear();
        pressed = dragging = doubleClick = shifted = false;
        pressedSlot = null;
        pressedButton = inputButton = -1;
        if (hadPreview) presentationChanged.run();
    }

    void reset() {
        cancel();
        previousSlot = null;
        previousButton = -1;
        quickMoved = ItemStack.EMPTY;
    }
}
