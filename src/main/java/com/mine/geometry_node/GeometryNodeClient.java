package com.mine.geometry_node;

import com.mine.geometry_node.client.key.KeyBindings;
import com.mine.geometry_node.client.render.ClientVisualManager;
import com.mine.geometry_node.client.ui.MainUI;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;


@Mod(value = GeometryNode.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = GeometryNode.MODID, value = Dist.CLIENT)
public class GeometryNodeClient {
    public GeometryNodeClient(IEventBus modBus) {
        // 注册按键
        modBus.addListener(KeyBindings::register);

        // 监听按键
        NeoForge.EVENT_BUS.addListener(this::onClientTick);

        // 监听世界渲染
        NeoForge.EVENT_BUS.addListener(this::onRenderLevelStage);

        // 渲染注册
        ClientVisualManager.init();
    }

    private void onClientTick(ClientTickEvent.Post event) {
        while (KeyBindings.OPEN_EDITOR.consumeClick()) {
            icyllis.modernui.mc.MuiModApi.openScreen(new MainUI());
        }

        ClientVisualManager.tick();
    }

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            ClientVisualManager.renderWorld(
                    event.getPoseStack(),
                    event.getCamera()
            );
        }
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        GeometryNode.LOGGER.info("HELLO FROM CLIENT SETUP");
        GeometryNode.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
