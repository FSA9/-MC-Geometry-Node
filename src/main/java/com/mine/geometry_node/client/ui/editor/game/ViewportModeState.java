package com.mine.geometry_node.client.ui.editor.game;

import com.mine.geometry_node.client.runtime.game.EditViewportCamera;

/** Mode selection outlives mouse capture; only leaving Fly restores orbit projection. */
final class ViewportModeState {
    private final EditViewportCamera camera;
    private ViewportMode mode = ViewportMode.GAME;
    private boolean savedOrthographic;

    ViewportModeState(EditViewportCamera camera) { this.camera = camera; }

    ViewportMode current() { return mode; }

    void select(ViewportMode next) {
        if (next == mode) return;
        if (mode == ViewportMode.FLY) camera.setOrthographic(savedOrthographic);
        if (next == ViewportMode.FLY) {
            savedOrthographic = camera.isOrthographic();
            camera.setOrthographic(false);
        }
        mode = next;
    }
}
