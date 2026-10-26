package com.mine.geometry_node.client.integration.model;

import com.mine.geometry_node.client.ui.hud.model.ModelHud;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** UI registration kept outside the model runtime's render/resource lifecycle. */
public final class ModelHudIntegration {
    private ModelHudIntegration() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ModelHudIntegration::guiLayers);
    }

    private static void guiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath("geometry_node", "model_hud"), ModelHud::render);
    }
}
