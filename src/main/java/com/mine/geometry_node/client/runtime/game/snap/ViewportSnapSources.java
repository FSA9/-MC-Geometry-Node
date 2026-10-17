package com.mine.geometry_node.client.runtime.game.snap;

import com.mine.geometry_node.client.runtime.game.render.PresentedViewportFrame;
import com.mine.geometry_node.client.runtime.game.scene.SceneGeometryInputs;
import com.mine.geometry_node.client.runtime.game.scene.ScenePresentations;
import com.mine.geometry_node.client.runtime.game.scene.ViewportSceneMath;
import com.mine.geometry_node.client.runtime.game.selection.ViewportSelectionSources;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/** Runs exclusively on the client render thread, only while a geometry-snapped move is active. */
public final class ViewportSnapSources {
    @FunctionalInterface public interface Source {
        void capture(PresentedViewportFrame frame, ViewportSnapGeometry.Query query, Collector result);
    }
    /** Shared bounded budget also applies to plugin sources. */
    public static final class Collector {
        public static final int MAX_MESHES = 512;
        public static final int MAX_VERTICES = 32768;
        public static final int MAX_EDGES = 65536;
        public static final int MAX_FACES = 65536;
        private final List<ViewportSnapGeometry.Mesh> meshes = new ArrayList<>();
        private int vertices;
        private int edges;
        private int faces;

        public boolean isFull() {
            return meshes.size() >= MAX_MESHES;
        }

        public boolean canAdd(int vertexCount, int edgeCount, int faceCount) {
            return !isFull() && vertexCount >= 0 && edgeCount >= 0 && faceCount >= 0
                    && vertexCount <= MAX_VERTICES - vertices
                    && edgeCount <= MAX_EDGES - edges
                    && faceCount <= MAX_FACES - faces;
        }

        public boolean add(ViewportSnapGeometry.Mesh mesh) {
            if (!canAdd(mesh.vertices().size(), mesh.edges().size(), mesh.faces().size())) return false;
            meshes.add(mesh);
            vertices += mesh.vertices().size();
            edges += mesh.edges().size();
            faces += mesh.faces().size();
            return true;
        }

        /** Plugin sources can also avoid constructing candidates that cannot fit. Recheck actual counts. */
        public boolean add(ViewportSnapMeshCounts counts, Supplier<ViewportSnapGeometry.Mesh> build) {
            return counts.fits(this) && add(build.get());
        }

        public boolean addBox(net.minecraft.world.phys.AABB box) {
            return add(ViewportSnapMeshCounts.BOX, () -> ViewportSnapGeometry.Mesh.box(box));
        }
    }
    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();
    static { SOURCES.add(ViewportSnapSources::blocks); SOURCES.add(ViewportSnapSources::objects); }
    private ViewportSnapSources() {}
    public static void register(Source source) { SOURCES.add(Objects.requireNonNull(source)); }
    public static ViewportSnapGeometry capture(PresentedViewportFrame frame, ViewportSnapGeometry.Query query) {
        var mc=Minecraft.getInstance();
        if (mc.level == null) SceneGeometryInputs.beginWorld(Long.MIN_VALUE);
        if(query==null || query.generation()!=frame.scene().worldGeneration() || mc.level==null || mc.player==null
                || !mc.level.dimension().identifier().toString().equals(frame.scene().dimension())) return ViewportSnapGeometry.EMPTY;
        SceneGeometryInputs.beginWorld(frame.scene().worldGeneration());
        Collector result=new Collector();
        for (var source : SOURCES) {
            if (result.isFull()) break;
            source.capture(frame, query, result);
        }
        return new ViewportSnapGeometry(query.generation(),query.queryToken(),result.meshes);
    }
    private static void blocks(PresentedViewportFrame frame, ViewportSnapGeometry.Query query, Collector result) {
        var mc=Minecraft.getInstance(); var level=mc.level;
        Set<BlockPos> positions=new LinkedHashSet<>();
        // Small local neighborhood plus nine image-space probes. No region scans or chunk loads.
        addNeighbors(positions,BlockPos.containing(query.reference()));
        for(var ray:query.rays()) {
            var probe=query.probe(ray);
            var hit=level.clip(new ClipContext(probe.origin(),probe.origin().add(probe.direction().scale(probe.length())),
                    ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
            if(hit.getType()!=HitResult.Type.MISS)addNeighbors(positions,hit.getBlockPos());
        }
        for(var pos:positions) {
            if(!level.hasChunkAt(pos))continue;
            var state=level.getBlockState(pos);
            if(state.isAir())continue;
            for(var box:state.getShape(level,pos).toAabbs())
                if (!result.addBox(box.move(pos))) return;
        }
    }
    private static void addNeighbors(Set<BlockPos> positions,BlockPos center) {
        for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++)positions.add(center.offset(x,y,z));
    }
    private static boolean near(PresentedViewportFrame frame, net.minecraft.world.phys.AABB box, ViewportSnapGeometry.Query query) {
        return ViewportSceneMath.intersects(frame,box,query.u()-query.radiusU()*2,query.v()-query.radiusV()*2,
                query.u()+query.radiusU()*2,query.v()+query.radiusV()*2);
    }
    private static void objects(PresentedViewportFrame frame, ViewportSnapGeometry.Query query, Collector result) {
        for(var object:frame.scene().objects()) {
            if (result.isFull()) return;
            var layer=ScenePresentations.layer(object);
            if(query.excluded().contains(object.id()) || !object.enabled() || layer==null || !query.visibility().visible(layer)
                    || query.excludeUnselectable()&&!query.visibility().selectable().contains(layer)
                    || !near(frame,ViewportSceneMath.bounds(object),query))continue;
            // An oversized object is skipped; smaller objects later in the scene may still fit.
            SceneGeometryInputs.captureSnapMesh(object, result);
        }
        for(var target:frame.targets()) {
            if (result.isFull()) return;
            if(query.excludeUnselectable()&&!ViewportSelectionSources.selectable(target.key(),query.visibility()))continue;
            if (near(frame, target.bounds(), query)) result.addBox(target.bounds());
        }
    }
}
