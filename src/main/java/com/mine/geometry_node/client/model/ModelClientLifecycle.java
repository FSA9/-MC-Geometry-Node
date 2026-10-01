package com.mine.geometry_node.client.model;

import com.mine.geometry_node.client.model.render.ModelStaticRenderer;
import com.mine.geometry_node.client.model.runtime.ClientModelService;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;

/** Production model lifecycle. Common importer/runtime code has no dependency on this client bootstrap. */
public final class ModelClientLifecycle {
    private ModelClientLifecycle(){}
    public static void register(IEventBus modBus){
        modBus.addListener(ModelClientLifecycle::reload);
        NeoForge.EVENT_BUS.addListener(ModelClientLifecycle::submit);
        NeoForge.EVENT_BUS.addListener(ModelClientLifecycle::drawOpaqueResident);
        NeoForge.EVENT_BUS.addListener(ModelClientLifecycle::drawTransparent);
        NeoForge.EVENT_BUS.addListener(ModelClientLifecycle::drawResolvedTransparent);
        NeoForge.EVENT_BUS.addListener(ModelClientLifecycle::logout);
        NeoForge.EVENT_BUS.addListener(ModelClientLifecycle::shutdown);
    }
    private static void reload(AddClientReloadListenersEvent event){
        event.addListener(Identifier.fromNamespaceAndPath("geometry_node","model_loader"),(ResourceManagerReloadListener)manager->
                Minecraft.getInstance().execute(() -> {
                    ModelStaticRenderer.refreshIris();
                    ClientModelService.INSTANCE.invalidateRenderResources("resource_reload");
                    ModelStaticRenderer.closeShadowResources();
                }));
    }
    private static void submit(SubmitCustomGeometryEvent event){
        ModelStaticRenderer.initializeIris();
        ModelStaticRenderer.pollIrisLifecycle();
        ModelStaticRenderer.beginMainFrame();
        ClientModelService.INSTANCE.processRenderUploads();
        ModelStaticRenderer.submit(event.getPoseStack(),event.getSubmitNodeCollector(),event.getLevelRenderState().cameraRenderState.pos,event.getLevelRenderState().cameraRenderState.cullFrustum,ClientModelService.INSTANCE.renderSnapshots());
    }
    private static void drawOpaqueResident(RenderLevelStageEvent.AfterOpaqueFeatures event){
        ModelStaticRenderer.drawResidentOpaque(event.getModelViewMatrix());
    }
    private static void drawTransparent(RenderLevelStageEvent.AfterTranslucentParticles event){
        ModelStaticRenderer.drawTransparent(event.getModelViewMatrix(),event.getLevelRenderState().cameraRenderState.pos,event.getLevelRenderState().cameraRenderState.cullFrustum,ClientModelService.INSTANCE.renderSnapshots());
    }
    /** Iris has completed deferred/composite/final; world depth and projection are still bound. */
    private static void drawResolvedTransparent(RenderLevelStageEvent.AfterLevel event){
        if (!ModelStaticRenderer.needsResolvedTransparency()) return;
        ModelStaticRenderer.drawResolvedTransparent(event.getModelViewMatrix(), event.getLevelRenderState().cameraRenderState.pos,
                event.getLevelRenderState().cameraRenderState.cullFrustum, ClientModelService.INSTANCE.renderSnapshots());
    }
    private static void shutdown(ClientStoppingEvent event){ClientModelService.INSTANCE.shutdown(); ModelStaticRenderer.closeShadowResources();}
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event){Minecraft.getInstance().execute(() -> { ClientModelService.INSTANCE.clear(); ModelStaticRenderer.closeShadowResources(); });}
}
