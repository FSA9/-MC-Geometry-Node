package com.mine.geometry_node;

import com.mine.geometry_node.client.runtime.dialogue.ClientDialogueState;
import com.mine.geometry_node.client.runtime.behavior.ClientBehaviorDebugStore;
import com.mine.geometry_node.client.runtime.dialogue.DialogueStyleRenderer;
import com.mine.geometry_node.client.input.ClientBlueprintInputManager;
import com.mine.geometry_node.client.input.KeyBindings;
import com.mine.geometry_node.client.runtime.quest.ClientQuestScreenState;
import com.mine.geometry_node.client.runtime.marker.ClientMarkerStore;
import com.mine.geometry_node.core.network.ClientNetworkReceiverRegistry;
import com.mine.geometry_node.client.runtime.marker.MarkerHudRenderer;
import com.mine.geometry_node.client.runtime.render.ClientVisualManager;
import com.mine.geometry_node.client.runtime.render.image.ClientImageAssetManager;
import com.mine.geometry_node.client.runtime.render.debug.GeometryDebugRenderer;
import com.mine.geometry_node.client.runtime.render.debug.SchematicProjectionRenderer;
import com.mine.geometry_node.client.ui.MainUI;
import com.mine.geometry_node.client.ui.editor.asset.remote.RemoteGraphClientState;
import com.mine.geometry_node.client.ui.editor.sidebar.BuiltinSidebarPanels;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.EntityTemplatePickerController;
import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferService;
import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferPlanState;
import com.mine.geometry_node.client.asset.preview.ClientAssetPreviewService;
import com.mine.geometry_node.core.command.registry.ModClientCommands;
import icyllis.modernui.mc.ModernUIMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;


@Mod(value = GeometryNode.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = GeometryNode.MODID, value = Dist.CLIENT)
public class GeometryNodeClient {
    public GeometryNodeClient(IEventBus modBus) {
        disableModernUiDevRegistries();
        ClientNetworkReceiverRegistry.init();

        // 注册按键
        modBus.addListener(KeyBindings::register);
        modBus.addListener(this::onRegisterGuiLayers);

        // 监听按键
        NeoForge.EVENT_BUS.addListener(this::onClientTick);

        // 监听世界渲染
        NeoForge.EVENT_BUS.addListener(this::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(this::onSubmitCustomGeometry);
        NeoForge.EVENT_BUS.addListener(this::onClientLoggingOut);
        NeoForge.EVENT_BUS.addListener(this::onInteractionKeyMappingTriggered);

        // 渲染注册
        ClientVisualManager.init();
        MarkerHudRenderer.init();

        BuiltinSidebarPanels.register();

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
        while (KeyBindings.OPEN_QUEST_SCREEN.consumeClick()) {
            if (Minecraft.getInstance().player != null) {
                ClientQuestScreenState.requestOpen();
            }
        }

        ClientVisualManager.tick();
        DialogueStyleRenderer.tick();

        ClientBlueprintInputManager.tick();
    }

    private void onRenderLevelStage(RenderLevelStageEvent.AfterTranslucentParticles event) {
        GeometryDebugRenderer.render(event.getPoseStack(), Minecraft.getInstance().gameRenderer.getMainCamera());
        SchematicProjectionRenderer.render(event.getPoseStack(), Minecraft.getInstance().gameRenderer.getMainCamera());
    }

    private void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        ClientVisualManager.renderWorld(event.getPoseStack(), event.getSubmitNodeCollector());
        SchematicProjectionRenderer.submitFeatures(event.getPoseStack(), event.getSubmitNodeCollector(), event.getLevelRenderState());
    }

    private void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBlueprintInputManager.reset();
        ClientQuestScreenState.reset();
        ClientDialogueState.reset();
        RemoteGraphClientState.reset();
        ClientAssetTransferPlanState.reset();
        ClientAssetTransferService.INSTANCE.resetConnection();
        ClientAssetPreviewService.INSTANCE.resetConnection();
        ClientBehaviorDebugStore.clear();
        EntityTemplatePickerController.reset();
        clearClientRenderState();
    }

    private void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        EntityTemplatePickerController.handleInteraction(event);
    }

    private void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(GeometryNode.MODID, "markers"),
                MarkerHudRenderer::render
        );
    }

    private static void clearClientRenderState() {
        ClientVisualManager.clear();
        ClientMarkerStore.clear();
        ClientImageAssetManager.clear();
        GeometryDebugRenderer.clear();
        SchematicProjectionRenderer.clear();
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        GeometryNode.LOGGER.info("HELLO FROM CLIENT SETUP");
        GeometryNode.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

        com.mine.geometry_node.client.ui.persistence.config.ConfigManager.INSTANCE.initOrLoad();
        com.mine.geometry_node.core.node.NodeRegistry.INSTANCE.init();
    }
}
