package com.mine.geometry_node.client.runtime.game.render;

/** Pixel dimensions of a framebuffer or published viewport texture, independent of UI layout units. */
public record ViewportFrameSize(int width, int height) {
    public static final ViewportFrameSize EMPTY = new ViewportFrameSize(0, 0);

    public boolean available() { return width > 0 && height > 0; }
}
