package com.mine.geometry_node.client.ui.utils;

import com.mine.geometry_node.GeometryNode;
import net.neoforged.api.distmarker.Dist;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = GeometryNode.MODID, value = Dist.CLIENT)
public class
ItemTooltipProxy {

    private static ItemStack currentStack = ItemStack.EMPTY;
    private static int tooltipX = 0;
    private static int tooltipY = 0;

    /**
     * 设置当前需要显示的 Tooltip 任务
     */
    public static void setTooltipTask(ItemStack stack, int guiX, int guiY) {
        if (stack != null && !stack.isEmpty()) {
            currentStack = stack;
            tooltipX = guiX;
            tooltipY = guiY;
        }
    }

    /**
     * 清除 Tooltip 任务（鼠标移出时调用）
     */
    public static void clearTooltipTask(ItemStack stack) {
        if (currentStack == stack) {
            currentStack = ItemStack.EMPTY;
        }
    }

    public static void clearTooltipTask() {
        currentStack = ItemStack.EMPTY;
    }

    /**
     * 在原版 GUI 渲染的最后阶段（绝对最顶层）绘制信息框
     */
    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (!currentStack.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            event.getGuiGraphics().setTooltipForNextFrame(mc.font, currentStack, tooltipX, tooltipY);
        }
    }
}
