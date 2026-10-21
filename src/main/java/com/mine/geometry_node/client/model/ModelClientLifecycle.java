package com.mine.geometry_node.client.model;

import com.mine.geometry_node.client.model.render.pipeline.ModelStaticRenderer;
import com.mine.geometry_node.client.model.render.pipeline.ModelRenderInstanceSource;
import com.mine.geometry_node.client.model.render.geometry.ModelRenderInstance;
import com.mine.geometry_node.client.model.render.lod.model.ModelLodMetrics;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import com.mine.geometry_node.client.model.runtime.ClientModelService;
import com.mine.geometry_node.client.runtime.light.diagnostic.LightReports;
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
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) ->
                event.enqueueWork(() -> ModelCacheIntegration.register(Minecraft.getInstance().gameDirectory.toPath())));
        ModelStaticRenderer.setInstanceSource(new ModelRenderInstanceSource() {
            public boolean hasVisibleInstances() { return ClientModelService.INSTANCE.hasVisibleInstances(); }
            public ModelLodMetrics.LoadedCounts loadedCounts() { return ClientModelService.INSTANCE.loadedCounts(); }
            public java.util.List<ModelRenderInstance> shadowInstances() { return ClientModelService.INSTANCE.renderSnapshots(); }
            public void invalidateRenderResources(String reason) { ClientModelService.INSTANCE.invalidateRenderResources(reason); }
        });
        modBus.addListener(ModelClientLifecycle::reload);
        modBus.addListener(ModelClientLifecycle::guiLayers);
        NeoForge.EVENT_BUS.addListener(ModelClientLifecycle::submit);
        NeoForge.EVENT_BUS.addListener(ModelClientLifecycle::drawLightSources);
        NeoForge.EVENT_BUS.addListener(ModelClientLifecycle::drawTransparent);
        NeoForge.EVENT_BUS.addListener(ModelClientLifecycle::drawResolvedTransparent);
        NeoForge.EVENT_BUS.addListener(ModelClientLifecycle::logout);
        NeoForge.EVENT_BUS.addListener(ModelClientLifecycle::shutdown);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            ClientModelService.INSTANCE.tickResidency();
            LightReports.tick();
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) -> {
            if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                com.mine.geometry_node.client.model.runtime.ClientModelCollisions.INSTANCE.tickServer(level);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload event) -> {
            if(event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level)
                Minecraft.getInstance().execute(() -> ClientModelService.INSTANCE.unload(level));
            else if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                com.mine.geometry_node.client.model.runtime.ClientModelCollisions.INSTANCE.unloadServer(level);
        });
    }
    private static void guiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath("geometry_node", "model_hud"),
                com.mine.geometry_node.client.model.diagnostic.ModelHud::render);
    }
    private static void reload(AddClientReloadListenersEvent event){
        event.addListener(Identifier.fromNamespaceAndPath("geometry_node","model_loader"),(ResourceManagerReloadListener)manager->
                Minecraft.getInstance().execute(() -> {
                    LightReports.cancel();
                    ModelStaticRenderer.refreshIris();
                    ClientModelService.INSTANCE.invalidateRenderResources("resource_reload");
                    ModelStaticRenderer.closeShadowResources();
                    com.mine.geometry_node.client.runtime.light.render.NodeLightRenderer.clear();
                    com.mine.geometry_node.client.model.diagnostic.ModelHud.invalidate();
                }));
    }
    public static void prepareFrame(net.minecraft.client.renderer.state.level.CameraRenderState camera){
        ModelStaticRenderer.initializeIris();
        ModelStaticRenderer.pollIrisLifecycle();
        ModelStaticRenderer.beginMainFrame();
        ClientModelService.INSTANCE.processRenderUploads();
        ModelStaticRenderer.prepareLighting(camera.pos,camera.cullFrustum,ClientModelService.INSTANCE.renderSnapshots());
    }
    private static void submit(SubmitCustomGeometryEvent event){
        ModelStaticRenderer.submit(event.getPoseStack(),event.getSubmitNodeCollector(),event.getLevelRenderState().cameraRenderState.pos,event.getLevelRenderState().cameraRenderState.cullFrustum,ClientModelService.INSTANCE.renderSnapshots());
        // This event is before FeatureRenderDispatcher.renderSolidFeatures and outside a GPU pass.
        // Solid particles can write additional pack targets that a later model draw cannot erase.
        // Establish model depth before they run; snapshot the host stack before our draws mutate it.
        ModelStaticRenderer.drawResidentOpaque(new Matrix4f(RenderSystem.getModelViewStack()));
    }
    private static void drawTransparent(RenderLevelStageEvent.AfterTranslucentParticles event){
        ModelStaticRenderer.drawTransparent(event.getModelViewMatrix(),event.getLevelRenderState().cameraRenderState.pos,event.getLevelRenderState().cameraRenderState.cullFrustum,ClientModelService.INSTANCE.renderSnapshots());
    }
    private static void drawLightSources(RenderLevelStageEvent.AfterOpaqueFeatures event){
        // Establish source depth/color before glass, water and transparent models blend over it.
        com.mine.geometry_node.client.runtime.light.render.NodeLightSourceRenderer.render(event.getPoseStack());
    }
    /** Iris has completed deferred/composite/final; world depth and projection are still bound. */
    private static void drawResolvedTransparent(RenderLevelStageEvent.AfterLevel event){
        com.mine.geometry_node.client.runtime.light.render.NativeVolumeLights.render(event.getModelViewMatrix());
        if (!ModelStaticRenderer.needsResolvedTransparency()) return;
        ModelStaticRenderer.drawResolvedTransparent(event.getModelViewMatrix(), event.getLevelRenderState().cameraRenderState.pos,
                event.getLevelRenderState().cameraRenderState.cullFrustum, ClientModelService.INSTANCE.renderSnapshots());
    }
    private static void shutdown(ClientStoppingEvent event){ModelCacheIntegration.unregister(); LightReports.cancel(); ClientModelService.INSTANCE.shutdown(); ModelStaticRenderer.closeShadowResources(); com.mine.geometry_node.client.runtime.light.render.NodeLightRenderer.clear(); com.mine.geometry_node.client.runtime.light.render.NativeVolumeLights.close();}
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event){Minecraft.getInstance().execute(() -> { LightReports.cancel(); ClientModelService.INSTANCE.clear(); ModelStaticRenderer.closeShadowResources(); com.mine.geometry_node.client.runtime.light.render.NodeLightRenderer.clear(); com.mine.geometry_node.client.model.diagnostic.ModelHud.invalidate(); });}
}
