package com.mine.geometry_node.client.runtime.marker;

import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Shared label/edge treatment; each registered style supplies its own silhouette. */
final class DefaultMarkerRenderer implements ClientMarkerRenderer {
    private static final int TEXTURE_SIZE = 96;
    private static final Identifier EDGE = Identifier.fromNamespaceAndPath("geometry_node", "textures/gui/markers/edge.png");
    private final Identifier texture;
    private final String previewResource;

    DefaultMarkerRenderer() { this("default"); }
    DefaultMarkerRenderer(String icon) {
        texture = Identifier.fromNamespaceAndPath("geometry_node", "textures/gui/markers/" + icon + ".png");
        previewResource = "/assets/geometry_node/icons/markers/" + icon + ".svg";
    }
    @Override public View createPreview(Context context, int color) {
        return new SvgIconView(context, previewResource, color);
    }
    @Override public void render(GuiGraphicsExtractor graphics, MarkerRenderContext context) {
        int x = context.screenX(), y = context.screenY();
        int size = context.screenEdge() ? 22 : 26;
        // A dark silhouette keeps the icon readable over sky, snow and busy terrain.
        drawIcon(graphics, texture, x + 1, y + 1, size + 2, 0xC010141B);
        drawIcon(graphics, texture, x, y, size, context.color());
        if (context.screenEdge()) drawEdge(graphics, context);
        if (!context.displayText().isBlank()) drawLabel(graphics, context, size);
    }
    private static void drawIcon(GuiGraphicsExtractor graphics, Identifier icon, int cx, int cy, int size, int color) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon, cx-size/2, cy-size/2, 0, 0,
                size, size, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, color);
    }
    private static void drawEdge(GuiGraphicsExtractor graphics, MarkerRenderContext context) {
        int dx = 0, dy = 0;
        float rotation = 0;
        switch (context.edgeDirection()) {
            case UP -> dy = -15;
            case DOWN -> { dy = 15; rotation = (float) Math.PI; }
            case LEFT -> { dx = -15; rotation = (float) -Math.PI / 2; }
            case RIGHT -> { dx = 15; rotation = (float) Math.PI / 2; }
        }
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(context.screenX()+dx, context.screenY()+dy);
            graphics.pose().rotate(rotation);
            drawIcon(graphics, EDGE, 0, 0, 10, context.color());
        } finally { graphics.pose().popMatrix(); }
    }
    private static void drawLabel(GuiGraphicsExtractor graphics, MarkerRenderContext context, int iconSize) {
        Font font = Minecraft.getInstance().font;
        int available = Math.max(0, graphics.guiWidth()-16);
        String text = context.displayText();
        if (font.width(text) > available) {
            text = font.plainSubstrByWidth(text, Math.max(0, available-font.width("…"))) + "…";
        }
        int width = font.width(text);
        int left = Math.clamp(context.screenX()-width/2, 8, Math.max(8, graphics.guiWidth()-width-8));
        int top = context.screenY()+iconSize/2+5;
        if (top+font.lineHeight+4 > graphics.guiHeight()) top = context.screenY()-iconSize/2-font.lineHeight-6;
        top = Math.max(3,top);
        graphics.fill(left-5, top-3, left+width+5, top+font.lineHeight+3, 0xB81A2028);
        graphics.fill(left-5, top-3, left-3, top+font.lineHeight+3, context.color());
        graphics.text(font, text, left, top, 0xFFF2F5F8, true);
    }
}
