package com.mine.geometry_node.client.runtime.model.diagnostic;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11C;

import java.time.Instant;
import java.util.*;

/** Capture the settings needed to compare performance measurements. */
public final class ModelReportContext {
    public static Map<String, Object> capture(Instant capturedAt) {
        Minecraft minecraft = Minecraft.getInstance();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capturedAt", capturedAt.toString());
        result.put("reportedFps", minecraft.getFps());
        result.put("gpuRenderer", Objects.toString(GL11C.glGetString(GL11C.GL_RENDERER), "unavailable"));
        result.put("gpuVendor", Objects.toString(GL11C.glGetString(GL11C.GL_VENDOR), "unavailable"));
        result.put("openGlVersion", Objects.toString(GL11C.glGetString(GL11C.GL_VERSION), "unavailable"));
        result.put("javaVersion", System.getProperty("java.version"));
        result.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        result.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        var camera = minecraft.gameRenderer.getMainCamera().position();
        result.put("cameraPosition", List.of(camera.x, camera.y, camera.z));
        result.put("playerRotation", minecraft.player == null ? List.of() :
                List.of(minecraft.player.getYRot(), minecraft.player.getXRot()));
        result.put("paused", minecraft.isPaused());
        result.put("resolution", List.of(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()));
        result.put("configuredFpsLimit", minecraft.options.framerateLimit().get());
        result.put("vsync", minecraft.options.enableVsync().get());
        result.put("renderDistance", minecraft.options.renderDistance().get());
        try {
            String pack = (String) Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName").invoke(null);
            result.put("shaderpack", Objects.toString(pack, "none"));
        } catch (ReflectiveOperationException | LinkageError error) { result.put("shaderpack", "unavailable"); }
        return result;
    }
    private ModelReportContext() {}
}
