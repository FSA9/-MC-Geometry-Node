package com.mine.geometry_node.client.ui.editor.game;

/** Game uses the player camera; edit and fly share this window's independent camera. */
public enum ViewportMode {
    GAME,
    EDIT,
    FLY;

    public boolean usesEditorCamera() { return this != GAME; }
}
