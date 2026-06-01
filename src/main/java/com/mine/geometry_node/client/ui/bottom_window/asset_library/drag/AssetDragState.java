package com.mine.geometry_node.client.ui.bottom_window.asset_library.drag;

import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetEntry;

public final class AssetDragState {
    private static Payload sPayload;

    private AssetDragState() {
    }

    public static void start(Payload payload) {
        sPayload = payload;
    }

    public static Payload current() {
        return sPayload;
    }

    public static Payload consume() {
        Payload payload = sPayload;
        sPayload = null;
        return payload;
    }

    public static void clear() {
        sPayload = null;
    }

    public record Payload(AssetEntry entry) {
        public boolean isSingleJsonGraph() {
            return entry != null && entry.isJsonFile();
        }
    }
}
