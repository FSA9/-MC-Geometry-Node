package com.mine.geometry_node.client.runtime.game.snap;

import com.mine.geometry_node.client.runtime.game.render.PresentedViewportFrame;
import com.mine.geometry_node.client.runtime.game.scene.ViewportSceneMath;
import com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Immutable snap geometry crossing the render/UI boundary; plugins can supply arbitrary meshes. */
public record ViewportSnapGeometry(long generation, long queryToken, List<Mesh> meshes) {
    public static final ViewportSnapGeometry EMPTY = new ViewportSnapGeometry(-1, -1, List.of());
    public ViewportSnapGeometry { meshes = List.copyOf(meshes); }
    public record Edge(Vec3 a, Vec3 b) {}
    public record Triangle(Vec3 a, Vec3 b, Vec3 c) {}
    public record Mesh(List<Vec3> vertices, List<Edge> edges, List<Triangle> faces, boolean closed) {
        public Mesh { vertices = List.copyOf(vertices); edges = List.copyOf(edges); faces = List.copyOf(faces); }
        public Mesh(List<Vec3> vertices,List<Edge> edges,List<Triangle> faces){this(vertices,edges,faces,closedSurface(faces));}
        public static Mesh box(AABB box) {
            List<Vec3> vertices = new ArrayList<>();
            List<Edge> edges = new ArrayList<>();
            List<Triangle> faces = new ArrayList<>();
            for (int i = 0; i < 8; i++) vertices.add(new Vec3((i&1)==0?box.minX:box.maxX,
                    (i&2)==0?box.minY:box.maxY, (i&4)==0?box.minZ:box.maxZ));
            for (int i = 0; i < 8; i++) for (int bit : new int[]{1,2,4})
                if ((i&bit)==0) edges.add(new Edge(vertices.get(i), vertices.get(i|bit)));
            for (int[] quad : new int[][]{{0,2,6,4},{1,5,7,3},{0,4,5,1},{2,3,7,6},{0,1,3,2},{4,6,7,5}}) {
                faces.add(new Triangle(vertices.get(quad[0]),vertices.get(quad[1]),vertices.get(quad[2])));
                faces.add(new Triangle(vertices.get(quad[0]),vertices.get(quad[2]),vertices.get(quad[3])));
            }
            return new Mesh(vertices, edges, faces, true);
        }
        private record Point(long x,long y,long z) implements Comparable<Point> {
            static Point of(Vec3 v){return new Point(Math.round(v.x*1e6),Math.round(v.y*1e6),Math.round(v.z*1e6));}
            @Override public int compareTo(Point b){int c=Long.compare(x,b.x);if(c==0)c=Long.compare(y,b.y);return c==0?Long.compare(z,b.z):c;}
        }
        private record Side(Point a,Point b) {
            static Side of(Vec3 a,Vec3 b){Point p=Point.of(a),q=Point.of(b);return p.compareTo(q)<=0?new Side(p,q):new Side(q,p);}
        }
        private static boolean closedSurface(List<Triangle> faces){
            if(faces.isEmpty())return false;
            var sides=new java.util.HashMap<Side,Integer>();
            for(var face:faces){
                if(face.b().subtract(face.a()).cross(face.c().subtract(face.a())).lengthSqr()<1e-18)continue;
                sides.merge(Side.of(face.a(),face.b()),1,Integer::sum);
                sides.merge(Side.of(face.b(),face.c()),1,Integer::sum);
                sides.merge(Side.of(face.c(),face.a()),1,Integer::sum);
            }
            return !sides.isEmpty()&&sides.values().stream().allMatch(count->count==2);
        }
    }
    public record Query(long generation, long queryToken, Vec3 reference, List<ViewportSceneMath.Ray> rays,
                        double u, double v, double radiusU, double radiusV,
                        Set<UUID> excluded, ViewportVisualization.Snapshot visibility,
                        boolean excludeUnselectable) {
        public Query { rays = List.copyOf(rays); excluded = Set.copyOf(excluded); }
        /** Signed orthographic near planes must not consume the whole bounded probe before the target. */
        public ViewportSceneMath.Ray probe(ViewportSceneMath.Ray ray) {
            double span=Math.min(1024,ray.length());
            double depth=reference.subtract(ray.origin()).dot(ray.direction());
            double start=Math.clamp(depth-span*.5,0,Math.max(0,ray.length()-span));
            return new ViewportSceneMath.Ray(ray.origin().add(ray.direction().scale(start)),ray.direction(),span);
        }
        public static Query create(PresentedViewportFrame frame, long token, Vec3 reference, double u, double v, double radiusU, double radiusV,
                                   Set<UUID> excluded, ViewportVisualization.Snapshot visibility, boolean filter) {
            // The cursor selects geometry; the moved reference only supplies the probe depth.
            if (!Double.isFinite(u)||!Double.isFinite(v)||u<0||u>1||v<0||v>1) return null;
            List<ViewportSceneMath.Ray> rays = new ArrayList<>();
            for (int y=-1;y<=1;y++) for (int x=-1;x<=1;x++) {
                var ray=ViewportSceneMath.ray(frame,u+x*radiusU,v+y*radiusV);
                if (ray!=null) rays.add(ray);
            }
            return rays.isEmpty()?null:new Query(frame.scene().worldGeneration(),token,reference,rays,u,v,
                    radiusU,radiusV,excluded,visibility,filter);
        }
    }
}
