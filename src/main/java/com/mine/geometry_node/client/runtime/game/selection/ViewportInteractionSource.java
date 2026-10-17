package com.mine.geometry_node.client.runtime.game.selection;

import com.mine.geometry_node.client.runtime.game.render.PresentedViewportFrame;
import com.mine.geometry_node.client.runtime.game.scene.ViewportDebugClient;
import com.mine.geometry_node.client.runtime.game.scene.ViewportSceneMath;
import com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization;
import com.mine.geometry_node.core.engine.graph.debug.ViewportDebugLayer;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryDebugSnapshot;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Native identity, paired with the exact server bounds used by the visible diagnostic mesh. */
public final class ViewportInteractionSource implements ViewportSelectionSources.Source {
    public static final String ID = "geometry_node:interaction";
    @Override public String id() { return ID; }
    @Override public Set<ViewportVisualization.Layer> selectableLayers() {
        return Set.of(ViewportVisualization.Layer.INTERACTION);
    }
    @Override public boolean selectable(ViewportVisualization.Snapshot settings) {
        return settings.helpers() && settings.selectable(ViewportVisualization.Layer.INTERACTION);
    }
    @Override public List<ViewportTarget> capture(PresentedViewportFrame frame) {
        var state = ViewportVisualization.current();
        if (!selectable(state))
            return List.of();
        List<ViewportTarget> result = new ArrayList<>();
        for (var mesh : ViewportDebugClient.meshes(ViewportDebugLayer.INTERACTION)) {
            // This typed layer carries the native entity UUID, including entities outside client tracking range.
            UUID id;
            try { id = UUID.fromString(mesh.id()); }
            catch (IllegalArgumentException invalidIdentity) { continue; }
            var target = target(id, mesh);
            if (target != null && ViewportSceneMath.intersects(frame, target.bounds(), 0, 0, 1, 1))
                result.add(target);
        }
        return List.copyOf(result);
    }
    static ViewportTarget target(UUID id, PacketGeometryDebugSnapshot.Mesh mesh) {
        // MESH stores its extents in local vertices; its size fields are zero.
        float[] vertices = mesh.vertices();
        if (vertices.length < 3) return null;
        double minX = vertices[0], minY = vertices[1], minZ = vertices[2];
        double maxX = minX, maxY = minY, maxZ = minZ;
        for (int i = 3; i + 2 < vertices.length; i += 3) {
            minX = Math.min(minX, vertices[i]); maxX = Math.max(maxX, vertices[i]);
            minY = Math.min(minY, vertices[i + 1]); maxY = Math.max(maxY, vertices[i + 1]);
            minZ = Math.min(minZ, vertices[i + 2]); maxZ = Math.max(maxZ, vertices[i + 2]);
        }
        AABB bounds = new AABB(minX + mesh.centerX(), minY + mesh.centerY(), minZ + mesh.centerZ(),
                maxX + mesh.centerX(), maxY + mesh.centerY(), maxZ + mesh.centerZ());
        return new ViewportTarget(new ViewportTarget.Key(ID, id.toString()), bounds,
                ViewportTarget.Decoration.BOX, List.of(bounds.getCenter()));
    }
}
