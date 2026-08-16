package com.mine.geometry_node.client.model.debug;

import com.mine.geometry_node.client.model.render.integration.ModelIntegrationController;
import com.mine.geometry_node.client.model.runtime.ClientModelRuntime;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Locale;

/** Compact client-only model runtime diagnostics overlay. */
public final class ModelDebugHud {
    private static final int MARGIN = 6;
    private static volatile boolean enabled;

    private ModelDebugHud() {}

    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!enabled || minecraft.level == null || minecraft.options.hideGui || minecraft.screen != null) return;

        ClientModelRuntime runtime = ClientModelRuntime.INSTANCE;
        var instances = runtime.instances().diagnostics();
        var frame = runtime.frameDiagnostics();
        var gpu = runtime.gpuDiagnostics();
        var integration = ModelIntegrationController.integrationStatus();
        long trackedGpuBytes = saturatedAdd(gpu.liveBufferBytes(), gpu.liveTextureBytes());
        String culling = frame.candidateDraws() < 0
                ? "Culling: n/a for " + integration.effectiveMode()
                : "Culling: " + frame.culledDraws() + '/' + frame.candidateDraws() + " culled  draws "
                + frame.drawCalls() + "  vertices " + number(frame.submittedVertices());
        List<String> lines = List.of(
                "GeometryNode Model  " + integration.effectiveMode() + " / " + integration.profileId(),
                "Instances: " + instances.ready() + '/' + instances.instances() + " ready  assets "
                        + instances.resources(),
                "Geometry: vertices " + number(instances.vertices()) + "  triangles "
                        + number(instances.triangles()) + "  source " + bytes(instances.sourceBytes()),
                "Frame: triangles " + number(frame.submittedTriangles()) + "  CPU "
                        + millis(frame.renderCpuNanos()) + " ms",
                culling,
                "Tracked GPU: " + bytes(trackedGpuBytes) + "  buffers " + bytes(gpu.liveBufferBytes())
                        + "  textures " + bytes(gpu.liveTextureBytes()));

        Font font = minecraft.font;
        int width = lines.stream().mapToInt(font::width).max().orElse(0);
        int lineHeight = font.lineHeight + 2;
        int height = lines.size() * lineHeight + 4;
        graphics.fill(MARGIN - 3, MARGIN - 3, MARGIN + width + 3, MARGIN + height - 1, 0xB0101214);
        for (int index = 0; index < lines.size(); index++) {
            int color = index == 0 ? 0xFF72D6A0 : 0xFFF0F0F0;
            graphics.text(font, lines.get(index), MARGIN, MARGIN + index * lineHeight, color, true);
        }
    }

    private static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0);
    }

    private static String bytes(long value) {
        if (value < 1024) return value + " B";
        double amount = value;
        String[] units = {"KiB", "MiB", "GiB", "TiB"};
        int unit = -1;
        do {
            amount /= 1024.0;
            unit++;
        } while (amount >= 1024.0 && unit < units.length - 1);
        return String.format(Locale.ROOT, "%.1f %s", amount, units[unit]);
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }
}
