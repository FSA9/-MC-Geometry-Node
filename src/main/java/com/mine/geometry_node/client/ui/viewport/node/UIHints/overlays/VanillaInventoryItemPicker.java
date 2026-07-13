package com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays;

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

public final class VanillaInventoryItemPicker {
    private static final Component INVENTORY_TITLE = Component.translatable("container.crafting");

    private VanillaInventoryItemPicker() {
    }

    public static boolean open(Consumer<ItemStack> onPicked, Runnable onDismissed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> open(onPicked, onDismissed));
            return true;
        }

        Player player = minecraft.player;
        if (player == null) {
            return false;
        }

        Screen previousScreen = minecraft.screen;
        minecraft.setScreen(new PickerScreen(player, previousScreen, onPicked, onDismissed));
        return true;
    }

    private static final class PickerScreen extends AbstractContainerScreen<InventoryMenu> {
        private final Player player;
        private final Screen previousScreen;
        private final Consumer<ItemStack> onPicked;
        private final Runnable onDismissed;
        private float xMouse;
        private float yMouse;
        private boolean finished;

        private PickerScreen(Player player, @Nullable Screen previousScreen, Consumer<ItemStack> onPicked, Runnable onDismissed) {
            super(player.inventoryMenu, player.getInventory(), INVENTORY_TITLE);
            this.player = player;
            this.previousScreen = previousScreen;
            this.onPicked = onPicked;
            this.onDismissed = onDismissed;
            this.titleLabelX = 97;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            this.xMouse = mouseX;
            this.yMouse = mouseY;
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
            graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_LOCATION, leftPos, topPos,
                    0.0f, 0.0f, imageWidth, imageHeight, 256, 256);
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics,
                    leftPos + 26, topPos + 8, leftPos + 75, topPos + 78,
                    30, 0.0625f, xMouse, yMouse, player);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            graphics.text(font, title, titleLabelX, titleLabelY, 0xFF404040, false);
        }

        @Override
        protected void slotClicked(Slot slot, int slotId, int mouseButton, ContainerInput input) {
            if (slot != null) {
                pick(slot.getItem());
            }
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (event.key() == GLFW.GLFW_KEY_ESCAPE || minecraft.options.keyInventory.matches(event)) {
                cancel();
                return true;
            }
            return true;
        }

        @Override
        public void onClose() {
            cancel();
        }

        private void pick(ItemStack stack) {
            if (finished) {
                return;
            }
            finished = true;
            Minecraft.getInstance().setScreen(previousScreen);
            if (onPicked != null) {
                onPicked.accept(stack.copy());
            }
            if (onDismissed != null) {
                onDismissed.run();
            }
        }

        private void cancel() {
            if (finished) {
                return;
            }
            finished = true;
            Minecraft.getInstance().setScreen(previousScreen);
            if (onDismissed != null) {
                onDismissed.run();
            }
        }
    }
}
