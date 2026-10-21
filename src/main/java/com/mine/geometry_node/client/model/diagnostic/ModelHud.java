package com.mine.geometry_node.client.model.diagnostic;

import com.mine.geometry_node.client.model.render.bvh.ModelBvhLightResolution;
import com.mine.geometry_node.client.model.render.bvh.ModelIrisHeldLight;
import com.mine.geometry_node.client.model.render.bvh.ModelLightingSettings;
import com.mine.geometry_node.client.model.render.lod.model.ModelLodMetrics;
import com.mine.geometry_node.client.model.render.lod.model.ModelLodPolicy;
import com.mine.geometry_node.client.model.render.pipeline.ModelStaticRenderer;
import com.mine.geometry_node.client.model.render.shadow.ModelShadowDistancePolicy;
import com.mine.geometry_node.client.model.runtime.ClientModelService;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Render-thread HUD: four lightweight reads per second, cached text for intervening frames. */
public final class ModelHud {
    private static final String KEY = "geometry_node.model.hud.";
    private static final long REFRESH_NANOS = 250_000_000L;
    private static final int MARGIN = 5;
    private static final int GENERAL = UiThemes.HUD.text().normal();
    private static final int CPU = UiThemes.HUD.text().secondary();
    private static final int WARNING = UiThemes.HUD.status().warning();
    // Metric family identities, independent of the HUD's chrome and warning state.
    private static final int LOD = 0xFF71D7EF;
    private static final int SHADOW = 0xFFFFD179;
    private static final int LOCAL_LIGHT = 0xFF8BE09C;
    private static final int GI = 0xFFC6AEF5;
    private static boolean enabled;
    private static long nextRefresh;
    private static int cachedWidth = -1, textWidth;
    private static Language language;
    private static Object level;
    private static List<Line> lines = List.of();
    private record Line(FormattedCharSequence text, int color) {}
    private record Row(Component text, int color) {}

    private ModelHud() {}

    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; invalidate(); }
    public static void invalidate() { lines = List.of(); nextRefresh = 0; cachedWidth = -1; level = null; }

    static int command(Boolean value) {
        if (value != null) setEnabled(value);
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(text("command", state(enabled ? "on" : "off")));
        return 1;
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        var minecraft = Minecraft.getInstance();
        if (!enabled) return;
        if (minecraft.player == null || minecraft.level == null) { invalidate(); return; }
        if (minecraft.screen != null || minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen()) return;
        int width = Math.max(1, graphics.guiWidth() - MARGIN * 2);
        long now = System.nanoTime();
        if (now >= nextRefresh || width != cachedWidth || language != Language.getInstance() || level != minecraft.level) {
            nextRefresh = now + REFRESH_NANOS;
            cachedWidth = width; language = Language.getInstance(); level = minecraft.level;
            var formatted = new ArrayList<Line>();
            textWidth = 0;
            for (var row : snapshot(minecraft.getFps())) {
                for (var part : minecraft.font.split(row.text(), width)) {
                    formatted.add(new Line(part, row.color()));
                    textWidth = Math.max(textWidth, minecraft.font.width(part));
                }
            }
            lines = List.copyOf(formatted);
        }
        int lineHeight = minecraft.font.lineHeight + 1;
        int count = Math.min(lines.size(), Math.max(0, (graphics.guiHeight() - MARGIN * 2) / lineHeight));
        if (count == 0) return;
        graphics.fill(MARGIN - 2, MARGIN - 2, MARGIN + textWidth + 2, MARGIN + count * lineHeight + 1,
                UiTheme.withAlpha(UiThemes.HUD.surface().tooltip(), 176));
        for (int i = 0; i < count; i++) {
            var line = lines.get(i);
            graphics.text(minecraft.font, line.text(), MARGIN, MARGIN + i * lineHeight, line.color(), true);
        }
    }

    private static List<Row> snapshot(int fps) {
        var metrics = ModelStaticRenderer.metrics();
        var cpu = metrics.hudSnapshot();
        var main = metrics.modelLodMetrics().hudSnapshot(ModelLodMetrics.Pass.MAIN);
        var loaded = ClientModelService.INSTANCE.loadedCounts();
        var lod = ModelLodPolicy.active();
        var shadow = ModelStaticRenderer.shadowHudSnapshot();
        var shared = ModelIrisHeldLight.hudSnapshot();
        var lighting = ModelIrisHeldLight.lightingHudSnapshot();
        var settings = ModelLightingSettings.global();
        var result = new ArrayList<Row>(9);
        result.add(new Row(text("overview", fps, loaded.instances(), loaded.objects()), GENERAL));
        result.add(new Row(text("cpu", milliseconds(cpu.mainCpuNanos()),
                milliseconds(shadow.available() ? cpu.shadowCpuNanos() : 0), milliseconds(cpu.lightingCpuNanos())), CPU));
        result.add(new Row(text("lod", mode(lod.enabled(), lod.automatic()),
                main.level0Objects(), main.level1Objects(), main.level2Objects(), main.level3Objects(),
                main.selectedTriangles(), main.sourceTriangles()), LOD));
        Component shadowMode = !shadow.available() ? state("unavailable")
                : mode(ModelShadowDistancePolicy.enabled(), ModelShadowDistancePolicy.automatic());
        result.add(new Row(text("shadow", shadowMode,
                shadow.toleranceSupported() ? range(shadow.minTolerance(), shadow.maxTolerance(), true) : state("unavailable"),
                shadow.available() ? cpu.shadowDraws() : 0), SHADOW));
        boolean globalLocalActive = settings.localEnabled() && (settings.quality().automatic()
                || settings.quality().areaSamples() > 0 && settings.quality().resolutionScale() > 0);
        result.add(new Row(text("local_light", mode(globalLocalActive, settings.quality().automatic()),
                lighting.localActiveReceivers(), lighting.receiverCount(),
                range(lighting.minSamples(), lighting.maxSamples(), false)), LOCAL_LIGHT));
        result.add(new Row(text("resolution", range(lighting.minResolution(), lighting.maxResolution(), true),
                text("resolution." + ModelBvhLightResolution.hudState().name().toLowerCase(Locale.ROOT))), LOCAL_LIGHT));
        result.add(new Row(text("gi", state(settings.giEnabled() ? "on" : "off"),
                lighting.giActiveReceivers(), lighting.receiverCount()), GI));
        if (lighting.autoDisabledReceivers() > 0 || lighting.overrideCount() > 0)
            result.add(new Row(text("receivers", lighting.autoDisabledReceivers(), lighting.overrideCount()), CPU));
        if (shared.state() != ModelIrisHeldLight.HudState.READY && shared.state() != ModelIrisHeldLight.HudState.EMPTY)
            result.add(new Row(text("lighting_state", state(shared.state().name().toLowerCase(Locale.ROOT))), WARNING));
        long pending = main.pendingObjects();
        if (pending > 0) result.add(new Row(text("upload", pending), WARNING));
        return result;
    }

    private static Component range(double min, double max, boolean decimal) {
        if (min < 0 || max < 0) return state("none");
        String first = decimal ? number(min) : Long.toString((long) min);
        String last = decimal ? number(max) : Long.toString((long) max);
        return first.equals(last) ? Component.literal(first) : text("range", first, last);
    }

    private static Component mode(boolean enabled, boolean automatic) {
        return state(!enabled ? "off" : automatic ? "auto" : "manual");
    }
    private static String milliseconds(long nanos) { return number(nanos / 1_000_000.0); }
    private static String number(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    private static Component state(String suffix) { return text("state." + suffix); }
    private static Component text(String suffix, Object... args) { return Component.translatable(KEY + suffix, args); }
}
