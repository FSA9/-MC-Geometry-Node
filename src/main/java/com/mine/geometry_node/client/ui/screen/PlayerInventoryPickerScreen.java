package com.mine.geometry_node.client.ui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public class PlayerInventoryPickerScreen extends Screen {

    private final Screen mParentScreen; // 用于回退的蓝图主界面
    private final Consumer<ItemStack> mOnPicked; // 选择后的回调

    private final int mSlotSize = 18;
    private final int mColumns = 9;
    private final int mRows = 4; // 3行背包 + 1行快捷栏

    public PlayerInventoryPickerScreen(Screen parentScreen, Consumer<ItemStack> onPicked) {
        super(Component.literal("请选择物品绑定到节点"));
        this.mParentScreen = parentScreen;
        this.mOnPicked = onPicked;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        Inventory inventory = Minecraft.getInstance().player.getInventory();

        int startX = (this.width - (mColumns * mSlotSize)) / 2;
        int startY = (this.height - (mRows * mSlotSize)) / 2;

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, startY - 20, 0xFFFFFF);

        // 渲染背包物品（包含快捷栏）
        for (int i = 0; i < 36; i++) {
            int col = i % mColumns;
            int row = i / mColumns;

            // 快捷栏为了美观，通常在最底下一行，这里做一点 Y 轴偏移区分
            int yOffset = (row == 0) ? startY + (3 * mSlotSize) + 4 : startY + ((row - 1) * mSlotSize);
            int slotX = startX + (col * mSlotSize);
            int slotY = yOffset;

            // 画原版槽位底图
            guiGraphics.fill(slotX, slotY, slotX + mSlotSize - 2, slotY + mSlotSize - 2, 0x88000000);

            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                guiGraphics.renderItem(stack, slotX, slotY);
                guiGraphics.renderItemDecorations(this.font, stack, slotX, slotY);

                // 如果鼠标悬浮，绘制 Tooltip
                if (mouseX >= slotX && mouseX < slotX + mSlotSize && mouseY >= slotY && mouseY < slotY + mSlotSize) {
                    guiGraphics.renderTooltip(this.font, stack, mouseX, mouseY);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button); // 仅限左键

        Inventory inventory = Minecraft.getInstance().player.getInventory();
        int startX = (this.width - (mColumns * mSlotSize)) / 2;
        int startY = (this.height - (mRows * mSlotSize)) / 2;

        for (int i = 0; i < 36; i++) {
            int col = i % mColumns;
            int row = i / mColumns;
            int yOffset = (row == 0) ? startY + (3 * mSlotSize) + 4 : startY + ((row - 1) * mSlotSize);
            int slotX = startX + (col * mSlotSize);
            int slotY = yOffset;

            // 检测点击命中
            if (mouseX >= slotX && mouseX < slotX + mSlotSize && mouseY >= slotY && mouseY < slotY + mSlotSize) {
                ItemStack clickedStack = inventory.getItem(i);

                mOnPicked.accept(clickedStack);

                // 【关键修复】：推迟关闭原版界面
                Minecraft.getInstance().tell(() -> {
                    Minecraft.getInstance().setScreen(mParentScreen);
                });
                return true;
            }
        }

        // 点击空白处直接退出
        Minecraft.getInstance().tell(() -> {
            Minecraft.getInstance().setScreen(mParentScreen);
        });
        return true;
    }
}