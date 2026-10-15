package com.mine.geometry_node.client.ui.editor.game;

/** The same pixel rectangle is used for image presentation and capture hit testing. */
public record GameViewportLayout(float left, float top, float right, float bottom) {
    public static GameViewportLayout fit(int width, int height, int frameWidth, int frameHeight) {
        if (width <= 0 || height <= 0 || frameWidth <= 0 || frameHeight <= 0) {
            return new GameViewportLayout(0, 0, 0, 0);
        }
        double scale = Math.min((double) width / frameWidth, (double) height / frameHeight);
        float drawWidth = (float) (frameWidth * scale);
        float drawHeight = (float) (frameHeight * scale);
        float x = (width - drawWidth) / 2;
        float y = (height - drawHeight) / 2;
        return new GameViewportLayout(x, y, x + drawWidth, y + drawHeight);
    }

    public boolean contains(float x, float y) {
        return x >= left && x < right && y >= top && y < bottom;
    }
}
