package com.mine.geometry_node.core.engine.system.worldquery;

import com.mine.geometry_node.core.engine.graph.resource.*;
import com.mine.geometry_node.core.engine.graph.debug.*;
import com.mine.geometry_node.core.engine.graph.debug.geometry.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Metadata adapters, shared only with small owner-service snapshot accessors. */
public final class WorldObjectEntries {
    private WorldObjectEntries() {}
    public static WorldObjectEntry graph(String provider, String type, GraphResourceId id, Vec3 position,
                                          AABB bounds, String identity, Map<String, String> extra) {
        Map<String, String> refs = new LinkedHashMap<>(extra);
        refs.put("resourceId", GraphResourceIdCodec.encode(id));
        if (id.targetEntityId() != null) refs.put("targetEntityUuid", id.targetEntityId().toString());
        if (id.processInstanceId() != null) refs.put("processInstanceId", id.processInstanceId().toString());
        String dim = id.scope().dimension().identifier().toString();
        return new WorldObjectEntry(WorldObjectEntry.key(provider, dim, identity), provider, type, "", dim,
                position, bounds, List.of(), id.binding().graphId(), id.type().lifetime().name().toLowerCase(Locale.ROOT),
                null, null, refs);
    }
    /** AABB enclosing a rotated local box, with the same Y-X-Z convention used by areas. */
    public static AABB rotatedBounds(Vec3 center, Vec3 size, Vec3 rotation) {
        var q = new Quaternionf().rotationYXZ((float)Math.toRadians(rotation.y),
                (float)Math.toRadians(rotation.x), (float)Math.toRadians(rotation.z));
        double x = 0, y = 0, z = 0;
        for (int i = 0; i < 8; i++) {
            var p = q.transform(new Vector3f((float)(size.x * ((i & 1) == 0 ? -.5 : .5)),
                    (float)(size.y * ((i & 2) == 0 ? -.5 : .5)), (float)(size.z * ((i & 4) == 0 ? -.5 : .5))));
            x = Math.max(x, Math.abs(p.x)); y = Math.max(y, Math.abs(p.y)); z = Math.max(z, Math.abs(p.z));
        }
        return new AABB(center.subtract(x, y, z), center.add(x, y, z));
    }
    /** Called once when a real server geometry source is replaced; queries never scan vertex buffers. */
    public static WorldObjectEntry geometry(ServerLevel level, DebugSourceId source, GeometryDebugElement mesh) {
        try { return geometryMetadata(source, mesh); }
        catch (IllegalArgumentException invalidMetadata) { return null; }
    }
    private static WorldObjectEntry geometryMetadata(DebugSourceId source, GeometryDebugElement mesh) {
        if (source.channel() != DebugRenderChannel.GEOMETRY || !(source.owner() instanceof DebugSourceId.Owner.Graph graph)) return null;
        AABB bounds;
        if (mesh.vertices().length >= 3) {
            double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
            double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
            float[] v = mesh.vertices();
            for (int i = 0; i + 2 < v.length; i += 3) {
                minX = Math.min(minX, v[i]); minY = Math.min(minY, v[i+1]); minZ = Math.min(minZ, v[i+2]);
                maxX = Math.max(maxX, v[i]); maxY = Math.max(maxY, v[i+1]); maxZ = Math.max(maxZ, v[i+2]);
            }
            bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ).move(mesh.center());
        } else bounds = rotatedBounds(mesh.center(), mesh.size(), mesh.rotation());
        String resource = GraphResourceIdCodec.encode(graph.resourceId());
        Map<String, String> refs = mesh.anchorUuid() == null ? Map.of() : Map.of("anchorEntityUuid", mesh.anchorUuid().toString());
        var entry = graph("geometry", "geometry/" + mesh.type().name().toLowerCase(Locale.ROOT), graph.resourceId(),
                mesh.center(), bounds, WorldObjectEntry.key(resource, "", mesh.id()), refs);
        return entry;
    }
}
