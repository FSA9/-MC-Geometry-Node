package com.mine.geometry_node;

import com.mine.geometry_node.client.key.ClientBlueprintInputManager;
import com.mine.geometry_node.client.key.KeyBindings;
import com.mine.geometry_node.client.render.ClientVisualManager;
import com.mine.geometry_node.client.render.debug.AreaDebugRenderer;
import com.mine.geometry_node.client.render.debug.GeometryDebugRenderer;
import com.mine.geometry_node.client.render.debug.SchematicProjectionRenderer;
import com.mine.geometry_node.client.ui.MainUI;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.remote.RemoteGraphClientState;
import com.mine.geometry_node.core.command.registry.ModClientCommands;
import icyllis.modernui.mc.ModernUIMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;


@Mod(value = GeometryNode.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = GeometryNode.MODID, value = Dist.CLIENT)
public class GeometryNodeClient {
    public GeometryNodeClient(IEventBus modBus) {
        disableModernUiDevRegistries();

        // 注册按键
        modBus.addListener(KeyBindings::register);

        // 监听按键
        NeoForge.EVENT_BUS.addListener(this::onClientTick);

        // 监听世界渲染
        NeoForge.EVENT_BUS.addListener(this::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(this::onSubmitCustomGeometry);
        NeoForge.EVENT_BUS.addListener(this::onClientLoggingOut);

        // 渲染注册
        ClientVisualManager.init();

        ModClientCommands.register();
    }

    private static void disableModernUiDevRegistries() {
        if (Boolean.getBoolean("geometry_node.keepModernUiDevRegistries")) {
            return;
        }
        ModernUIMod.sDevelopment = false;
    }

    private void onClientTick(ClientTickEvent.Post event) {
        while (KeyBindings.OPEN_EDITOR.consumeClick()) {
            icyllis.modernui.mc.MuiModApi.openScreen(new MainUI());
        }

        ClientVisualManager.tick();

        ClientBlueprintInputManager.tick();
    }

    private void onRenderLevelStage(RenderLevelStageEvent.AfterTranslucentParticles event) {
        AreaDebugRenderer.render(event.getPoseStack(), Minecraft.getInstance().gameRenderer.getMainCamera());
        GeometryDebugRenderer.render(event.getPoseStack(), Minecraft.getInstance().gameRenderer.getMainCamera());
        SchematicProjectionRenderer.render(event.getPoseStack(), Minecraft.getInstance().gameRenderer.getMainCamera());
    }

    private void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        ClientVisualManager.renderWorld(event.getPoseStack(), event.getSubmitNodeCollector());
        SchematicProjectionRenderer.submitFeatures(event.getPoseStack(), event.getSubmitNodeCollector(), event.getLevelRenderState());
    }

    private void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RemoteGraphClientState.reset();
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        GeometryNode.LOGGER.info("HELLO FROM CLIENT SETUP");
        GeometryNode.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

        com.mine.geometry_node.client.ui.persistence.config.ConfigManager.INSTANCE.initOrLoad();
        com.mine.geometry_node.core.node.NodeRegistry.INSTANCE.init();
    }
}
