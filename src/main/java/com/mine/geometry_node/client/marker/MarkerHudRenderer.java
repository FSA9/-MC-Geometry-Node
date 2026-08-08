package com.mine.geometry_node.client.marker;

import com.mine.geometry_node.core.engine.system.marker.MarkerType;
import com.mine.geometry_node.core.engine.system.marker.MarkerTypeRegistry;
import com.mine.geometry_node.core.network.packet.marker.MarkerPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Projects authoritative marker snapshots into an always-visible HUD layer.
 */
public final class MarkerHudRenderer {
    private static final int EDGE_MARGIN = 20;
    private static final int FALLBACK_COLOR = 0xFF4DA3FF;
    private static final ClientMarkerRenderer FALLBACK_RENDERER = new DefaultMarkerRenderer();

    private MarkerHudRenderer() {
    }

    public static void init() {
        ClientMarkerRendererRegistry.register(MarkerTypeRegistry.DEFAULT_RENDERER_ID, FALLBACK_RENDERER);
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }

        String currentDimension = minecraft.level.dimension().identifier().toString();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        Vec3 playerPosition = minecraft.player.getEyePosition(partialTick);
        List<RenderEntry> entries = new ArrayList<>();

        for (ClientMarkerStore.ClientMarker marker : ClientMarkerStore.snapshot()) {
            MarkerPayload payload = marker.payload();
            if (!payload.active() || !currentDimension.equals(payload.dimension())) {
                continue;
            }
            Vec3 position = marker.renderPosition(minecraft, partialTick);
            double distance = Math.sqrt(playerPosition.distanceToSqr(position));
            entries.add(new RenderEntry(payload, position, distance));
        }

        entries.sort(Comparator.comparingDouble(RenderEntry::distance).reversed());
        for (RenderEntry entry : entries) {
            renderEntry(graphics, minecraft, entry);
        }
    }

    private static void renderEntry(GuiGraphicsExtractor graphics, Minecraft minecraft, RenderEntry entry) {
        Vec3 projected = minecraft.gameRenderer.projectPointToScreen(entry.position());
        if (!Double.isFinite(projected.x) || !Double.isFinite(projected.y) || !Double.isFinite(projected.z)) {
            return;
        }

        boolean behind = projected.z > 1.0D;
        double normalizedX = behind ? -projected.x : projected.x;
        double normalizedY = behind ? -projected.y : projected.y;
        double screenX = (normalizedX * 0.5D + 0.5D) * graphics.guiWidth();
        double screenY = (0.5D - normalizedY * 0.5D) * graphics.guiHeight();
        boolean screenEdge = behind
                || screenX < EDGE_MARGIN
                || screenX > graphics.guiWidth() - EDGE_MARGIN
                || screenY < EDGE_MARGIN
                || screenY > graphics.guiHeight() - EDGE_MARGIN;

        MarkerRenderContext.EdgeDirection edgeDirection = MarkerRenderContext.EdgeDirection.UP;
        if (screenEdge) {
            double centerX = graphics.guiWidth() * 0.5D;
            double centerY = graphics.guiHeight() * 0.5D;
            double deltaX = screenX - centerX;
            double deltaY = screenY - centerY;
            if (Math.abs(deltaX) < 1.0E-6D && Math.abs(deltaY) < 1.0E-6D) {
                deltaY = -1.0D;
            }
            double scaleX = (centerX - EDGE_MARGIN) / Math.max(1.0E-6D, Math.abs(deltaX));
            double scaleY = (centerY - EDGE_MARGIN) / Math.max(1.0E-6D, Math.abs(deltaY));
            double scale = Math.min(1.0D, Math.min(scaleX, scaleY));
            screenX = centerX + deltaX * scale;
            screenY = centerY + deltaY * scale;
            edgeDirection = edgeDirection(deltaX, deltaY);
        }

        MarkerType type = MarkerTypeRegistry.INSTANCE.get(entry.payload().typeId());
        int color = type != null ? type.color() : FALLBACK_COLOR;
        ClientMarkerRenderer renderer = type != null
                ? ClientMarkerRendererRegistry.get(type.rendererId())
                : null;
        if (renderer == null) {
            renderer = FALLBACK_RENDERER;
        }

        renderer.render(graphics, new MarkerRenderContext(
                entry.payload(),
                (int) Math.round(screenX),
                (int) Math.round(screenY),
                color,
                screenEdge,
                edgeDirection,
                displayText(entry.payload(), entry.distance())
        ));
    }

    private static MarkerRenderContext.EdgeDirection edgeDirection(double x, double y) {
        if (Math.abs(x) >= Math.abs(y)) {
            return x < 0.0D ? MarkerRenderContext.EdgeDirection.LEFT : MarkerRenderContext.EdgeDirection.RIGHT;
        }
        return y < 0.0D ? MarkerRenderContext.EdgeDirection.UP : MarkerRenderContext.EdgeDirection.DOWN;
    }

    private static String displayText(MarkerPayload payload, double distance) {
        String distanceText = payload.showDistance() ? formatDistance(distance) : "";
        if (payload.text().isBlank()) {
            return distanceText;
        }
        if (distanceText.isBlank()) {
            return payload.text();
        }
        return payload.text() + " | " + distanceText;
    }

    private static String formatDistance(double blocks) {
        if (blocks < 1000.0D) {
            return Math.round(blocks) + " m";
        }
        return String.format(Locale.ROOT, "%.1f km", blocks / 1000.0D);
    }

    private record RenderEntry(MarkerPayload payload, Vec3 position, double distance) {
    }
}
