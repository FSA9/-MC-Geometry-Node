package com.mine.geometry_node.client.model.diagnostic;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11C;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Capture graphics/world state on the render thread; hash evidence only on the report IO worker. */
final class ModelReportContext {
    static Map<String, Object> capture() {
        Minecraft minecraft = Minecraft.getInstance();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capturedAt", Instant.now().toString());
        result.put("frameRate", Map.of("reportedFps", minecraft.getFps(),
                "scope", "Minecraft latest FPS counter at capture, not an interval benchmark or model-only/GPU timing"));
        Map<String, String> versions = new LinkedHashMap<>();
        for (var mod : net.neoforged.fml.ModList.get().getMods()) {
            if (Set.of("minecraft", "neoforge", "iris", "sodium", "geometry_node").contains(mod.getModId()))
                versions.put(mod.getModId(), mod.getVersion().toString());
        }
        result.put("versions", versions);
        result.put("gpuVendor", Objects.toString(GL11C.glGetString(GL11C.GL_VENDOR), "unavailable"));
        result.put("gpuRenderer", Objects.toString(GL11C.glGetString(GL11C.GL_RENDERER), "unavailable"));
        result.put("openGlVersion", Objects.toString(GL11C.glGetString(GL11C.GL_VERSION), "unavailable"));
        result.put("resolution", List.of(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()));
        result.put("renderSettings", Map.of("configuredFpsLimit", minecraft.options.framerateLimit().get(),
                "vsync", minecraft.options.enableVsync().get(),
                "renderDistance", minecraft.options.renderDistance().get(),
                "simulationDistance", minecraft.options.simulationDistance().get(),
                "inactivityFpsLimit", minecraft.options.inactivityFpsLimit().get().toString()));
        var camera = minecraft.gameRenderer.getMainCamera(); var position = camera.position();
        result.put("camera", Map.of("x", position.x, "y", position.y, "z", position.z, "pitch", camera.xRot(), "yaw", camera.yRot()));
        if (minecraft.level != null) result.put("world", Map.of("dimension", minecraft.level.dimension().toString(),
                "gameTime", minecraft.level.getGameTime(), "dayTime", minecraft.level.getDefaultClockTime(), "raining", minecraft.level.isRaining(), "thundering", minecraft.level.isThundering()));
        try {
            String pack = (String) Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName").invoke(null);
            result.put("shaderpack", Objects.toString(pack, "none"));
        } catch (ReflectiveOperationException | LinkageError error) { result.put("shaderpack", "unavailable"); }
        result.put("gpuTiming", "N/A: no model GPU timestamp query");
        return result;
    }
    static void addFileEvidence(Map<String, Object> context, Path gameDirectory) {
        Object name = context.get("shaderpack");
        if (!(name instanceof String pack)) return;
        Path directory = gameDirectory.resolve("shaderpacks").toAbsolutePath().normalize();
        Path file = directory.resolve(pack).normalize();
        try {
            if (!file.startsWith(directory) || !Files.isRegularFile(file)) {
                context.put("shaderpackHash", "N/A: active pack is not a local ZIP file"); return;
            }
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536]; int n;
                while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n);
            }
            context.put("shaderpackHash", HexFormat.of().formatHex(digest.digest()));
            context.put("shaderpackBytes", Files.size(file));
            Path settings = file.resolveSibling(file.getFileName() + ".txt");
            if (Files.isRegularFile(settings) && Files.size(settings) <= 65536)
                context.put("shaderpackSettings", Files.readString(settings));
        } catch (Exception error) { context.put("shaderpackHash", "unavailable: " + error.getMessage()); }
    }
    private ModelReportContext() {}
}
