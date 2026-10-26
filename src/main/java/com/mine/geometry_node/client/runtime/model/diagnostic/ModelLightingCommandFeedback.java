package com.mine.geometry_node.client.runtime.model.diagnostic;

import com.mine.geometry_node.client.runtime.model.render.bvh.ModelLightingProfiler;
import com.mine.geometry_node.client.runtime.model.render.bvh.ModelLightingSettings;
import com.mine.geometry_node.client.runtime.model.runtime.ClientModelService;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Loaded only on execution, keeping command-tree registration independent of the client UI. */
final class ModelLightingCommandFeedback {
    private static final String KEY = "geometry_node.model.lighting.command.";

    static int run(String id, boolean local, Runnable change) {
        try {
            if (id != null && ClientModelService.INSTANCE.query(id).isEmpty()) {
                say("unknown_instance", id);
                return 0;
            }
            if (change != null) {
                if (ModelLightingProfiler.active()) {
                    say("profile_running");
                    return 0;
                }
                change.run();
            }
            var settings = ModelLightingSettings.effective(id);
            var target = id == null ? text("global") : Component.literal(id);
            var source = text(id == null ? "global" : (local ? ModelLightingSettings.hasLocalOverride(id)
                    : ModelLightingSettings.hasGiOverride(id)) ? "overridden" : "inherited");
            if (local) {
                var quality = settings.quality();
                boolean enabled = settings.localEnabled() && (quality.automatic()
                        || quality.areaSamples() > 0 && quality.resolutionScale() > 0);
                say("local", target, text(enabled ? "on" : "off"), text(quality.automatic() ? "automatic" : "manual"),
                        quality.automatic() ? "0..4" : Integer.toString(quality.areaSamples()),
                        quality.automatic() ? "0..1" : Float.toString(quality.resolutionScale()), source);
            } else {
                say("gi", target, text(settings.giEnabled() ? "on" : "off"), source);
            }
            return 1;
        } catch (RuntimeException error) {
            say("error", ClientModelService.rootMessage(error));
            return 0;
        }
    }

    private static Component text(String key, Object... values) { return Component.translatable(KEY + key, values); }
    private static void say(String key, Object... values) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(text(key, values));
    }

    private ModelLightingCommandFeedback() {}
}
