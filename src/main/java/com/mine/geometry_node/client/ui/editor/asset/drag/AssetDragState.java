package com.mine.geometry_node.client.ui.editor.asset.drag;

import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public record Payload(List<AssetEntry> entries) {
        public Payload {
            entries = entries == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(entries));
        }

        public AssetEntry entry() {
            return entries.size() == 1 ? entries.get(0) : null;
        }

        public boolean isSingleJsonGraph() {
            AssetEntry entry = entry();
            return entry != null && entry.isJsonFile();
        }
    }
}
