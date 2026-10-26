package com.mine.geometry_node.client.integration.ai.graph;

import com.mine.geometry_node.client.feature.ai.graph.query.GraphQueryService;
import com.mine.geometry_node.client.feature.ai.graph.query.GraphReadSnapshot;
import com.mine.geometry_node.core.node.document.NodeGraph;
import icyllis.modernui.core.Core;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;

/** Captures editor-owned query inputs before feature code builds and queries its snapshot. */
final class GraphQueryCapture {
    private GraphQueryCapture() {}

    static GraphReadSnapshot.CapturedDocument document(NodeGraph graph) {
        if (!Core.isOnUiThread()) {
            throw new IllegalStateException("graph read snapshots must be captured on the ModernUI thread");
        }
        return GraphReadSnapshot.captureDocument(graph);
    }

    static GraphQueryService queries(NodeGraph graph) {
        return GraphQueryService.forSnapshot(GraphReadSnapshot.build(document(graph)), currentRegistryAccess());
    }

    static RegistryAccess currentRegistryAccess() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? null : minecraft.level.registryAccess();
    }
}
