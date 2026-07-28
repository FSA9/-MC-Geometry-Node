package com.mine.geometry_node.client.dialogue.ui;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MinecraftSurfaceView;
import icyllis.modernui.widget.FrameLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

/**
 * ModernUI layout host that delegates Component rendering to Minecraft.
 */
public final class VanillaComponentView extends FrameLayout {
    private final float textSizeDp;
    private final MinecraftSurfaceView surfaceView;
    private volatile Component text;

    public VanillaComponentView(Context context, Component text, float textSizeDp) {
        super(context);
        this.text = safeCopy(text);
        this.textSizeDp = Math.max(1.0f, textSizeDp);

        surfaceView = new MinecraftSurfaceView(context);
        surfaceView.setEnabled(false);
        surfaceView.setClickable(false);
        surfaceView.setFocusable(false);
        surfaceView.setRenderer(new MinecraftSurfaceView.Renderer() {
            @Override
            public void onSurfaceChanged(int width, int height) {
            }

            @Override
            public void onDraw(@Nonnull GuiGraphicsExtractor graphics,
                               int mouseX,
                               int mouseY,
                               float deltaTick,
                               double guiScale,
                               float alpha) {
                drawComponent(graphics, guiScale, alpha);
            }
        });
        addView(surfaceView, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setText(Component text) {
        this.text = safeCopy(text);
        surfaceView.invalidate();
    }

    private void drawComponent(GuiGraphicsExtractor graphics, double guiScale, float alpha) {
        Component currentText = text;
        if (currentText.getString().isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        float safeGuiScale = guiScale > 0.0 ? (float) guiScale : 1.0f;
        float targetLineHeight = UIUtils.dp2px(textSizeDp) / safeGuiScale;
        float textScale = targetLineHeight / font.lineHeight;
        float viewHeight = surfaceView.getHeight() / safeGuiScale;
        float drawY = Math.max(0.0f, (viewHeight - font.lineHeight * textScale) * 0.5f);
        int opacity = Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
        int fallbackColor = (opacity << 24) | 0xFFFFFF;

        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0f, drawY);
        graphics.pose().scale(textScale, textScale);
        graphics.text(font, currentText, 0, 0, fallbackColor, false);
        graphics.pose().popMatrix();
    }

    private static Component safeCopy(Component text) {
        return text == null ? Component.empty() : text.copy();
    }
}
