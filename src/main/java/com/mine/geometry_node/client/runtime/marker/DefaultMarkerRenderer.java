package com.mine.geometry_node.client.runtime.marker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class DefaultMarkerRenderer implements ClientMarkerRenderer {
    @Override
    public void render(GuiGraphicsExtractor graphics, MarkerRenderContext context) {
        Font font = Minecraft.getInstance().font;
        int x = context.screenX();
        int y = context.screenY();

        if (context.screenEdge()) {
            String arrow = switch (context.edgeDirection()) {
                case LEFT -> "<";
                case RIGHT -> ">";
                case UP -> "^";
                case DOWN -> "v";
            };
            int arrowWidth = font.width(arrow);
            graphics.fill(x - 6, y - 6, x + 7, y + 7, 0xC0101214);
            graphics.outline(x - 6, y - 6, 13, 13, context.color());
            graphics.text(font, arrow, x - arrowWidth / 2, y - 4, context.color(), true);
        } else {
            graphics.fill(x - 6, y - 6, x + 7, y + 7, 0xC0101214);
            graphics.outline(x - 6, y - 6, 13, 13, context.color());
            graphics.fill(x - 3, y - 3, x + 4, y + 4, context.color());
            graphics.fill(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
        }

        if (context.displayText().isBlank()) {
            return;
        }

        int textWidth = font.width(context.displayText());
        int textX = Math.clamp(x - textWidth / 2, 3, Math.max(3, graphics.guiWidth() - textWidth - 3));
        int textY = y + 10;
        if (textY + font.lineHeight + 3 > graphics.guiHeight()) {
            textY = y - font.lineHeight - 10;
        }
        graphics.fill(textX - 3, textY - 2, textX + textWidth + 3, textY + font.lineHeight + 1, 0xB0101214);
        graphics.text(font, context.displayText(), textX, textY, 0xFFF0EEE7, true);
    }
}
