package com.mine.geometry_node.client.model.debug;

import com.mine.geometry_node.client.model.render.integration.ModelIntegrationController;
import com.mine.geometry_node.client.model.render.backend.host.entity.HostStaticEntityRenderer;
import com.mine.geometry_node.client.model.runtime.ClientModelRuntime;
import com.mine.geometry_node.client.model.runtime.ModelDimensionId;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Locale;

/** Compact client-only model runtime diagnostics overlay. */
public final class ModelDebugHud {
    private static final int MARGIN = 6;
    private static final int LOADING_MIN_WIDTH = 160;
    private static final int LOADING_MAX_WIDTH = 200;
    private static final int GRAPH_WIDTH = 132;
    private static final int GRAPH_HEIGHT = 42;
    private static final long GRAPH_WINDOW_NANOS = 10_000_000_000L;
    private static final int GRAPH_SAMPLE_CAPACITY = 4096;
    private static final SampleHistory CPU_HISTORY = new SampleHistory(GRAPH_SAMPLE_CAPACITY);
    private static final SampleHistory FPS_HISTORY = new SampleHistory(GRAPH_SAMPLE_CAPACITY);
    private static volatile boolean enabled;

    private ModelDebugHud() {}

    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            CPU_HISTORY.clear();
            FPS_HISTORY.clear();
        }
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!enabled || minecraft.level == null || minecraft.options.hideGui || minecraft.screen != null) return;

        ClientModelRuntime runtime = ClientModelRuntime.INSTANCE;
        var instances = runtime.instances().diagnostics();
        var scene = runtime.instances().sceneGeometry(new ModelDimensionId(
                minecraft.level.dimension().identifier().toString()));
        var frame = runtime.frameDiagnostics();
        var gpu = runtime.gpuDiagnostics();
        var integration = ModelIntegrationController.integrationStatus();
        var hostStatic = HostStaticEntityRenderer.diagnostics();
        long trackedGpuBytes = saturatedAdd(gpu.liveBufferBytes(), gpu.liveTextureBytes());
        List<HudLine> lines = new java.util.ArrayList<>();
        lines.add(new HudLine("GeometryNode  " + integration.effectiveMode() + " / "
                + integration.profileId(), 0xFF72D6A0));
        lines.add(new HudLine("Models  " + instances.ready() + '/' + instances.instances()
                + " ready  assets " + instances.resources(),
                instances.ready() == instances.instances() ? 0xFFF0F0F0 : 0xFFFFC857));
        lines.add(new HudLine("Scene  vertices " + number(scene.vertices()) + "  triangles "
                + number(scene.triangles()), 0xFFB8C0C8));
        lines.add(new HudLine("Frame  triangles " + number(frame.submittedTriangles())
                + "  draws " + frame.drawCalls(), 0xFF67C8FF));
        String culling = frame.candidateDraws() < 0
                ? "Culling  n/a"
                : "Culling  draws " + frame.culledDraws() + '/' + frame.candidateDraws()
                + "  clusters " + hostStatic.culledClusters() + '/' + hostStatic.candidateClusters();
        lines.add(new HudLine(culling, 0xFF78D6C6));
        if (hostStatic.lodGeometries() > 0) {
            lines.add(new HudLine("Model LOD requested  " + hostStatic.requestedLod0() + '/'
                    + hostStatic.requestedLod1() + '/' + hostStatic.requestedLod2() + '/'
                    + hostStatic.requestedLod3(), 0xFFFFC857));
            lines.add(new HudLine("Model LOD actual  " + hostStatic.actualLod0() + '/'
                    + hostStatic.actualLod1() + '/' + hostStatic.actualLod2() + '/'
                    + hostStatic.actualLod3(), 0xFFD7B8FF));
            lines.add(new HudLine("Model LOD available  " + hostStatic.availableLod0() + '/'
                    + hostStatic.availableLod1() + '/' + hostStatic.availableLod2() + '/'
                    + hostStatic.availableLod3(), 0xFFD7B8FF));
            lines.add(new HudLine("Model LOD tris  " + number(hostStatic.lodSourceTriangles()) + '/'
                    + number(hostStatic.lodLevel1Triangles()) + '/'
                    + number(hostStatic.lodLevel2Triangles()) + '/'
                    + number(hostStatic.lodLevel3Triangles()), 0xFFC9A7E8));
            lines.add(new HudLine(String.format(Locale.ROOT,
                    "Model LOD build  locked %.1f%%  fail %d  %.1f ms",
                    hostStatic.lodLockedRatio() * 100.0, hostStatic.lodBuildFailures(),
                    hostStatic.lodBuildNanos() / 1_000_000.0), 0xFFC9A7E8));
        }
        lines.add(new HudLine("GPU  " + bytes(trackedGpuBytes) + "  buffers "
                + bytes(gpu.liveBufferBytes()) + "  textures " + bytes(gpu.liveTextureBytes()), 0xFF78AFFF));
        if (hostStatic.buildingDraws() > 0 || hostStatic.fallbackDraws() > 0 || hostStatic.deferredImmediateDraws() > 0
                || hostStatic.rangeLimitFallbacks() > 0) {
            lines.add(new HudLine("Static  building " + hostStatic.buildingDraws()
                    + "  fallback " + hostStatic.fallbackDraws()
                    + "  deferred " + hostStatic.deferredImmediateDraws()
                    + "  ranges " + hostStatic.rangeLimitFallbacks(), 0xFFFFA24A));
        }

        Font font = minecraft.font;
        int width = lines.stream().map(HudLine::text).mapToInt(font::width).max().orElse(0);
        int lineHeight = font.lineHeight + 2;
        int height = lines.size() * lineHeight + 4;
        graphics.fill(MARGIN - 3, MARGIN - 3, MARGIN + width + 3, MARGIN + height - 1, 0xB0101214);
        for (int index = 0; index < lines.size(); index++) {
            HudLine line = lines.get(index);
            graphics.text(font, line.text(), MARGIN, MARGIN + index * lineHeight, line.color(), true);
        }
        renderLoading(graphics, font, ModelLoadProgressTracker.snapshot());
        long now = System.nanoTime();
        CPU_HISTORY.push(now, frame.renderCpuNanos() / 1_000_000.0);
        FPS_HISTORY.push(now, minecraft.getFps());
        renderPerformance(graphics, font, now);
    }

    private static void renderLoading(GuiGraphicsExtractor graphics, Font font,
                                      List<ModelLoadProgressTracker.Snapshot> loading) {
        if (loading.isEmpty()) return;
        int availableWidth = Math.max(1, graphics.guiWidth() - MARGIN * 2);
        int width = Math.min(LOADING_MAX_WIDTH, availableWidth);
        if (availableWidth >= LOADING_MIN_WIDTH) width = Math.max(LOADING_MIN_WIDTH, width);
        int rowHeight = font.lineHeight + 11;
        int visible = Math.min(loading.size(), Math.max(1, (graphics.guiHeight() - MARGIN * 2 - 16) / rowHeight));
        int height = 16 + visible * rowHeight + 3;
        int left = graphics.guiWidth() - MARGIN - width;
        graphics.fill(left, MARGIN - 3, left + width, MARGIN + height, 0xB0101214);
        graphics.text(font, "Loading models (" + loading.size() + ')', left + 4, MARGIN,
                0xFF72D6A0, true);
        for (int index = 0; index < visible; index++) {
            ModelLoadProgressTracker.Snapshot item = loading.get(index);
            int top = MARGIN + 14 + index * rowHeight;
            int percent = (int) Math.round(item.progress() * 100.0);
            String text = item.label() + "  " + item.stage() + " " + percent + '%';
            int available = width - 8;
            graphics.text(font, font.plainSubstrByWidth(text, available), left + 4, top,
                    0xFFF0F0F0, true);
            int barTop = top + font.lineHeight + 1;
            int barWidth = width - 8;
            graphics.fill(left + 4, barTop, left + 4 + barWidth, barTop + 4, 0xFF34393E);
            graphics.fill(left + 4, barTop, left + 4 + (int) Math.round(barWidth * item.progress()),
                    barTop + 4, 0xFF58C98B);
        }
    }

    private static void renderPerformance(GuiGraphicsExtractor graphics, Font font, long now) {
        int left = graphics.guiWidth() - MARGIN - GRAPH_WIDTH;
        int fpsTop = graphics.guiHeight() - MARGIN - GRAPH_HEIGHT;
        int cpuTop = fpsTop - 4 - GRAPH_HEIGHT;
        renderGraph(graphics, font, left, cpuTop, "Model CPU 10s", CPU_HISTORY, now,
                16.67, " ms", 0xFF58C98B);
        renderGraph(graphics, font, left, fpsTop, "FPS 10s", FPS_HISTORY, now,
                120.0, "", 0xFF63A8FF);
    }

    private static void renderGraph(GuiGraphicsExtractor graphics, Font font, int left, int top,
                                    String label, SampleHistory history, long now, double minimumScale,
                                    String suffix, int color) {
        graphics.fill(left, top, left + GRAPH_WIDTH, top + GRAPH_HEIGHT, 0xB0101214);
        double current = history.latest();
        int chartWidth = GRAPH_WIDTH - 8;
        SampleWindow window = history.window(now, GRAPH_WINDOW_NANOS, chartWidth);
        double scale = Math.max(minimumScale, window.maximum());
        String value = suffix.isEmpty()
                ? String.format(Locale.ROOT, "%.0f  L %.0f", current,
                history.lowAverage(now, GRAPH_WINDOW_NANOS, 0.01))
                : String.format(Locale.ROOT, "%.2f%s", current, suffix);
        graphics.text(font, label, left + 4, top + 3, 0xFFF0F0F0, true);
        graphics.text(font, value, left + GRAPH_WIDTH - 4 - font.width(value), top + 3,
                color, true);
        int chartLeft = left + 4;
        int chartTop = top + font.lineHeight + 6;
        int chartHeight = GRAPH_HEIGHT - font.lineHeight - 9;
        graphics.fill(chartLeft, chartTop, chartLeft + chartWidth, chartTop + chartHeight, 0xFF25292D);
        int previousX = chartLeft;
        int previousY = chartTop + chartHeight - 1 - graphY(window.values()[0], scale, chartHeight);
        for (int index = 1; index < chartWidth; index++) {
            int x = chartLeft + index;
            int y = chartTop + chartHeight - 1 - graphY(window.values()[index], scale, chartHeight);
            line(graphics, previousX, previousY, x, y, color);
            previousX = x;
            previousY = y;
        }
    }

    private static int graphY(double value, double scale, int height) {
        return (int) Math.round(Math.clamp(value / scale, 0.0, 1.0) * (height - 1));
    }

    private static void line(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) return;
            int doubled = error * 2;
            if (doubled >= dy) { error += dy; x0 += sx; }
            if (doubled <= dx) { error += dx; y0 += sy; }
        }
    }

    private static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
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

    private record HudLine(String text, int color) {}

    private static final class SampleHistory {
        private final long[] timestamps;
        private final double[] values;
        private int start;
        private int size;

        private SampleHistory(int capacity) {
            timestamps = new long[capacity];
            values = new double[capacity];
        }

        private void push(long timestamp, double value) {
            if (!Double.isFinite(value) || value < 0) value = 0;
            int index = (start + size) % values.length;
            timestamps[index] = timestamp;
            values[index] = value;
            if (size < values.length) size++;
            else start = (start + 1) % values.length;
        }

        private double get(int index) { return values[(start + index) % values.length]; }
        private long timestamp(int index) { return timestamps[(start + index) % values.length]; }
        private double latest() { return size == 0 ? 0 : get(size - 1); }
        private SampleWindow window(long now, long duration, int width) {
            double[] result = new double[width];
            boolean[] populated = new boolean[width];
            long beginning = now - duration;
            for (int index = 0; index < size; index++) {
                long timestamp = timestamp(index);
                if (timestamp < beginning) continue;
                int bucket = (int) Math.min(width - 1,
                        Math.max(0, (timestamp - beginning) * width / duration));
                result[bucket] = get(index);
                populated[bucket] = true;
            }
            double previous = 0;
            double maximum = 0;
            for (int index = 0; index < width; index++) {
                if (populated[index]) previous = result[index];
                else result[index] = previous;
                maximum = Math.max(maximum, result[index]);
            }
            return new SampleWindow(result, maximum);
        }
        private double lowAverage(long now, long duration, double fraction) {
            long beginning = now - duration;
            double[] window = new double[size];
            int count = 0;
            for (int index = 0; index < size; index++) {
                if (timestamp(index) >= beginning) window[count++] = get(index);
            }
            if (count == 0) return 0;
            java.util.Arrays.sort(window, 0, count);
            int lowCount = Math.max(1, (int) Math.ceil(count * fraction));
            double sum = 0;
            for (int index = 0; index < lowCount; index++) sum += window[index];
            return sum / lowCount;
        }
        private void clear() { start = 0; size = 0; }
    }

    private record SampleWindow(double[] values, double maximum) {}
}
