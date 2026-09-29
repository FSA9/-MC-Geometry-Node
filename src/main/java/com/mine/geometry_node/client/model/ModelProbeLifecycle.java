package com.mine.geometry_node.client.model;
import com.mine.geometry_node.client.model.render.ModelProbeController;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
/** All client wiring lives here; common model types never initialize this class. */
public final class ModelProbeLifecycle {
    private ModelProbeLifecycle() {}
    public static void register(IEventBus modBus) {
        modBus.addListener(ModelProbeLifecycle::reload);
        NeoForge.EVENT_BUS.addListener(ModelProbeLifecycle::submit);
        NeoForge.EVENT_BUS.addListener(ModelProbeLifecycle::draw);
        NeoForge.EVENT_BUS.addListener(ModelProbeLifecycle::logout);
    }
    private static void reload(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath("geometry_node","model_probe"),
                (ResourceManagerReloadListener)manager->{
                    com.mine.geometry_node.client.model.diagnostic.ModelProbeCommands.cancel();
                    Minecraft.getInstance().execute(ModelProbeController.INSTANCE::clear);
                });
    }
    private static void submit(SubmitCustomGeometryEvent event) {
        ModelProbeController.INSTANCE.submit(event.getPoseStack(),event.getSubmitNodeCollector(),event.getLevelRenderState().cameraRenderState.pos);
    }
    private static void draw(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        ModelProbeController.INSTANCE.draw(event.getModelViewMatrix(),event.getLevelRenderState().cameraRenderState.pos);
    }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        com.mine.geometry_node.client.model.diagnostic.ModelProbeCommands.cancel();
        Minecraft.getInstance().execute(ModelProbeController.INSTANCE::clear);
    }
}
