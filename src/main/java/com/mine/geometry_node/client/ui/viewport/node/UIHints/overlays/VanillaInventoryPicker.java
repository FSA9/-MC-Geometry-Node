package com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays;

import com.mine.geometry_node.core.node.value.SlotRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public final class VanillaInventoryPicker {
    private static final Component INVENTORY_TITLE = Component.translatable("container.crafting");

    private VanillaInventoryPicker() {
    }

    public static boolean openItem(Consumer<ItemStack> onPicked, Runnable onDismissed) {
        return open((slot, slotId) -> slot.getItem().copy(), onPicked, onDismissed);
    }

    public static boolean openSlotRef(Consumer<SlotRef> onPicked, Runnable onDismissed) {
        return open((slot, slotId) -> toSlotRef(slotId), onPicked, onDismissed);
    }

    private static <T> boolean open(
            SlotSelection<T> selection,
            Consumer<T> onPicked,
            Runnable onDismissed
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> open(selection, onPicked, onDismissed));
            return true;
        }

        Player player = minecraft.player;
        if (player == null) {
            return false;
        }

        Screen previousScreen = minecraft.screen;
        minecraft.setScreen(new PickerScreen<>(
                player,
                previousScreen,
                selection,
                onPicked,
                onDismissed
        ));
        return true;
    }

    @Nullable
    private static SlotRef toSlotRef(int slotId) {
        if (slotId >= InventoryMenu.USE_ROW_SLOT_START && slotId < InventoryMenu.USE_ROW_SLOT_END) {
            return new SlotRef(SlotRef.PLAYER_INVENTORY, "hotbar." + (slotId - InventoryMenu.USE_ROW_SLOT_START));
        }
        if (slotId >= InventoryMenu.INV_SLOT_START && slotId < InventoryMenu.INV_SLOT_END) {
            return new SlotRef(SlotRef.PLAYER_INVENTORY, "main." + (slotId - InventoryMenu.INV_SLOT_START));
        }
        if (slotId >= InventoryMenu.ARMOR_SLOT_START && slotId < InventoryMenu.ARMOR_SLOT_END) {
            return switch (slotId - InventoryMenu.ARMOR_SLOT_START) {
                case 0 -> new SlotRef(SlotRef.EQUIPMENT, "head");
                case 1 -> new SlotRef(SlotRef.EQUIPMENT, "chest");
                case 2 -> new SlotRef(SlotRef.EQUIPMENT, "legs");
                case 3 -> new SlotRef(SlotRef.EQUIPMENT, "feet");
                default -> null;
            };
        }
        if (slotId == InventoryMenu.SHIELD_SLOT) {
            return new SlotRef(SlotRef.EQUIPMENT, "offhand");
        }
        return null;
    }

    @FunctionalInterface
    private interface SlotSelection<T> {
        @Nullable
        T select(Slot slot, int slotId);
    }

    private static final class PickerScreen<T> extends AbstractContainerScreen<InventoryMenu> {
        private final Player mPlayer;
        private final Screen mPreviousScreen;
        private final SlotSelection<T> mSelection;
        private final Consumer<T> mOnPicked;
        private final Runnable mOnDismissed;
        private float mMouseX;
        private float mMouseY;
        private boolean mFinished;

        private PickerScreen(
                Player player,
                @Nullable Screen previousScreen,
                SlotSelection<T> selection,
                Consumer<T> onPicked,
                Runnable onDismissed
        ) {
            super(player.inventoryMenu, player.getInventory(), INVENTORY_TITLE);
            mPlayer = player;
            mPreviousScreen = previousScreen;
            mSelection = selection;
            mOnPicked = onPicked;
            mOnDismissed = onDismissed;
            titleLabelX = 97;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            mMouseX = mouseX;
            mMouseY = mouseY;
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
            graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_LOCATION, leftPos, topPos,
                    0.0f, 0.0f, imageWidth, imageHeight, 256, 256);
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics,
                    leftPos + 26, topPos + 8, leftPos + 75, topPos + 78,
                    30, 0.0625f, mMouseX, mMouseY, mPlayer);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            graphics.text(font, title, titleLabelX, titleLabelY, 0xFF404040, false);
        }

        @Override
        protected void slotClicked(Slot slot, int slotId, int mouseButton, ContainerInput input) {
            if (slot == null) return;
            T selected = mSelection.select(slot, slotId);
            if (selected != null) {
                pick(selected);
            }
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (event.key() == GLFW.GLFW_KEY_ESCAPE || minecraft.options.keyInventory.matches(event)) {
                cancel();
            }
            return true;
        }

        @Override
        public void onClose() {
            cancel();
        }

        private void pick(T selected) {
            if (mFinished) return;
            mFinished = true;
            Minecraft.getInstance().setScreen(mPreviousScreen);
            if (mOnPicked != null) {
                mOnPicked.accept(selected);
            }
            notifyDismissed();
        }

        private void cancel() {
            if (mFinished) return;
            mFinished = true;
            Minecraft.getInstance().setScreen(mPreviousScreen);
            notifyDismissed();
        }

        private void notifyDismissed() {
            if (mOnDismissed != null) {
                mOnDismissed.run();
            }
        }
    }
}
