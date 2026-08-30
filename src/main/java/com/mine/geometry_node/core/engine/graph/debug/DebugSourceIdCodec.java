package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIdCodec;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Lossless debug source encoding used to derive wire-level element ids. */
public final class DebugSourceIdCodec {
    private DebugSourceIdCodec() {
    }

    public static String encode(DebugSourceId id) {
        String ownerKind;
        String ownerValue;
        if (id.owner() instanceof DebugSourceId.Owner.Graph graph) {
            ownerKind = "g";
            ownerValue = GraphResourceIdCodec.encode(graph.resourceId());
        } else if (id.owner() instanceof DebugSourceId.Owner.SchematicPlacement placement) {
            ownerKind = "s";
            ownerValue = placement.dimension().identifier() + "\u0000" + placement.key();
        } else {
            throw new IllegalArgumentException("Unsupported debug source owner");
        }
        return "ds1." + id.channel().id() + "." + ownerKind + "." + part(ownerValue);
    }

    public static String element(DebugSourceId sourceId, String localId) {
        String local = localId == null || localId.isBlank() ? "element" : localId;
        return encode(sourceId) + ".el." + part(local);
    }

    private static String part(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
