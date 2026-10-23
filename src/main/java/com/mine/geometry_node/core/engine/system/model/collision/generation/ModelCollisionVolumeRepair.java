package com.mine.geometry_node.core.engine.system.model.collision.generation;

import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.*;

/** Caps complete, small, simple planar loops; preserves concavities and rejects nested holes. */
final class ModelCollisionVolumeRepair {
    private static final double EPS = 1e-10;
    private record Point(int index, double x, double y) {}
    private record PlanarLoop(Vector3d origin,Vector3d normal,Vector3d u,Vector3d v,List<Point> points) {}
    private static final class Edge { int count, direction, from, to; }
    private ModelCollisionVolumeRepair() {}

    static int[] repair(List<Vector3d> points, int[] triangles, double mergeScale, double fitError,
                        ModelCancellation token, long deadline) throws IOException {
        token.check();
        if (!Double.isFinite(mergeScale) || mergeScale <= 0 || !Double.isFinite(fitError) || fitError < 0
                || triangles.length < 9 || triangles.length % 3 != 0 || triangles.length > 60_000) return triangles;
        try {
            Map<Long,Edge> edges = edges(points,triangles,token,deadline);
            if (edges == null) return triangles;
            Map<Integer,Integer> outgoing = new LinkedHashMap<>(), incoming = new HashMap<>();
            for (Edge edge : edges.values()) if (edge.count == 1) {
                if (outgoing.put(edge.from,edge.to) != null || incoming.put(edge.to,edge.from) != null) return triangles;
            }
            if (outgoing.isEmpty() || outgoing.size() > 4096 || !outgoing.keySet().equals(incoming.keySet())) return triangles;
            Set<Integer> visited = new HashSet<>(); List<Integer> caps = new ArrayList<>();
            List<PlanarLoop> loops = new ArrayList<>();
            for (int first : outgoing.keySet()) {
                ModelCollisionOptimizationBudget.check(token,deadline);
                if (visited.contains(first)) continue;
                List<Integer> loop = new ArrayList<>(); int current = first;
                do {
                    if (!visited.add(current) || loop.size() >= 128) return triangles;
                    loop.add(current); current = outgoing.get(current);
                } while (current != first);
                PlanarLoop projected=project(points,loop,mergeScale,fitError);
                if(projected==null)return triangles;
                loops.add(projected);
            }
            for(int i=0;i<loops.size();i++) {
                ModelCollisionOptimizationBudget.check(token,deadline);
                for(int j=0;j<i;j++)if(overlappingCaps(points,loops.get(i),loops.get(j),fitError))return triangles;
                if(!triangulate(new ArrayList<>(loops.get(i).points),caps))return triangles;
            }
            int[] result = Arrays.copyOf(triangles,triangles.length + caps.size());
            for (int i = 0; i < caps.size(); i++) result[triangles.length+i] = caps.get(i);
            Map<Long,Edge> closed = edges(points,result,token,deadline);
            if (closed == null || closed.values().stream().anyMatch(edge -> edge.count != 2 || edge.direction != 0)) return triangles;
            Vector3d origin = points.get(result[0]); double volume = 0;
            for (int i = 0; i < result.length; i += 3) {
                Vector3d a = new Vector3d(points.get(result[i])).sub(origin),
                        b = new Vector3d(points.get(result[i+1])).sub(origin), c = new Vector3d(points.get(result[i+2])).sub(origin);
                volume += a.dot(b.cross(c));
            }
            return volume > 1e-12 && Double.isFinite(volume) ? result : triangles;
        } catch (ModelCollisionOptimizationBudget.Exhausted exhausted) {
            token.check(); return triangles;
        }
    }

    private static Map<Long,Edge> edges(List<Vector3d> points,int[] triangles,ModelCancellation token,long deadline) throws IOException {
        Map<Long,Edge> edges = new HashMap<>();
        for (int i = 0; i < triangles.length; i += 3) {
            if ((i & 255) == 0) ModelCollisionOptimizationBudget.check(token,deadline);
            int a=triangles[i],b=triangles[i+1],c=triangles[i+2];
            if (a<0||b<0||c<0||a>=points.size()||b>=points.size()||c>=points.size()||a==b||b==c||a==c) return null;
            if (!points.get(a).isFinite()||!points.get(b).isFinite()||!points.get(c).isFinite()
                    || new Vector3d(points.get(b)).sub(points.get(a)).cross(new Vector3d(points.get(c)).sub(points.get(a))).lengthSquared()<1e-24)
                return null;
            for (int j=0;j<3;j++) {
                int from=triangles[i+j],to=triangles[i+(j+1)%3];
                long key=((long)Math.min(from,to)<<32)|Math.max(from,to);
                Edge edge=edges.computeIfAbsent(key,ignored->new Edge());
                edge.from=from;edge.to=to;edge.direction+=from<to?1:-1;
                if(++edge.count>2||edge.count==2&&edge.direction!=0)return null;
            }
        }
        return edges;
    }

    private static PlanarLoop project(List<Vector3d> vertices,List<Integer> loop,double scale,double error) {
        if(loop.size()<3)return null;
        Vector3d origin=vertices.get(loop.getFirst()),normal=new Vector3d();
        for(int i=0;i<loop.size();i++) {
            Vector3d a=new Vector3d(vertices.get(loop.get(i))).sub(origin),
                    b=new Vector3d(vertices.get(loop.get((i+1)%loop.size()))).sub(origin);
            normal.add(a.cross(b));
        }
        if(normal.lengthSquared()<1e-24)return null;normal.normalize();
        Vector3d u=new Vector3d(vertices.get(loop.get(1))).sub(origin);u.fma(-u.dot(normal),normal);
        if(u.lengthSquared()<1e-24)return null;u.normalize();Vector3d v=new Vector3d(normal).cross(u);
        List<Point> polygon=new ArrayList<>();
        for(int id:loop) {
            Vector3d p=new Vector3d(vertices.get(id)).sub(origin);
            if(Math.abs(p.dot(normal))>error)return null;
            polygon.add(new Point(id,p.dot(u),p.dot(v)));
        }
        if(minimumWidth(polygon)>scale)return null;
        for(int i=0;i<polygon.size();i++) {
            Point a=polygon.get(i),b=polygon.get((i+1)%polygon.size());
            for(int j=i+1;j<polygon.size();j++) {
                if(j==i+1||i==0&&j==polygon.size()-1)continue;
                Point c=polygon.get(j),d=polygon.get((j+1)%polygon.size());
                if(intersects(a,b,c,d))return null;
            }
        }
        return new PlanarLoop(origin,normal,u,v,polygon);
    }

    private static boolean triangulate(List<Point> polygon,List<Integer> result) {
        while(polygon.size()>3) {
            int ear=-1;double best=0,totalArea=area(polygon);
            for(int i=0;i<polygon.size();i++) {
                int previous=(i+polygon.size()-1)%polygon.size(),next=(i+1)%polygon.size();
                Point a=polygon.get(previous),b=polygon.get(i),c=polygon.get(next);
                double area=cross(a,b,c);
                if(area<=best||totalArea-area<=1e-14)continue;
                boolean empty=true;
                for(int j=0;j<polygon.size();j++) {
                    if(j==previous||j==i||j==next)continue;
                    Point p=polygon.get(j);
                    if(cross(a,b,p)>=-1e-14&&cross(b,c,p)>=-1e-14&&cross(c,a,p)>=-1e-14){empty=false;break;}
                }
                if(empty){best=area;ear=i;}
            }
            if(ear<0||best<1e-14)return false;
            result.add(polygon.get((ear+polygon.size()-1)%polygon.size()).index);
            result.add(polygon.get((ear+1)%polygon.size()).index);result.add(polygon.get(ear).index);
            polygon.remove(ear);
        }
        if(cross(polygon.get(0),polygon.get(1),polygon.get(2))<1e-14)return false;
        result.add(polygon.get(0).index);result.add(polygon.get(2).index);result.add(polygon.get(1).index);
        return true;
    }

    private static boolean overlappingCaps(List<Vector3d> vertices,PlanarLoop a,PlanarLoop b,double error) {
        if(Math.abs(a.normal.dot(b.normal))<1-1e-8)return false;
        List<Point> second=new ArrayList<>();
        for(Point point:b.points) {
            Vector3d relative=new Vector3d(vertices.get(point.index)).sub(a.origin);
            if(Math.abs(relative.dot(a.normal))>2*error+EPS)return false;
            second.add(new Point(point.index,relative.dot(a.u),relative.dot(a.v)));
        }
        for(int i=0;i<a.points.size();i++)for(int j=0;j<second.size();j++)
            if(intersects(a.points.get(i),a.points.get((i+1)%a.points.size()),second.get(j),second.get((j+1)%second.size())))return true;
        return inside(a.points,second.getFirst())||inside(second,a.points.getFirst());
    }
    private static boolean inside(List<Point> polygon,Point p) {
        boolean inside=false;
        for(int i=0,j=polygon.size()-1;i<polygon.size();j=i++) {
            Point a=polygon.get(i),b=polygon.get(j);
            if((a.y>p.y)!=(b.y>p.y)&&p.x<(b.x-a.x)*(p.y-a.y)/(b.y-a.y)+a.x)inside=!inside;
        }
        return inside;
    }
    private static double minimumWidth(List<Point> polygon) {
        List<Point> sorted=new ArrayList<>(polygon);
        sorted.sort(Comparator.comparingDouble(Point::x).thenComparingDouble(Point::y));
        List<Point> hull=new ArrayList<>();
        for(Point p:sorted){while(hull.size()>=2&&cross(hull.get(hull.size()-2),hull.getLast(),p)<=0)hull.removeLast();hull.add(p);}
        int lower=hull.size();
        for(int i=sorted.size()-2;i>=0;i--){Point p=sorted.get(i);while(hull.size()>lower&&cross(hull.get(hull.size()-2),hull.getLast(),p)<=0)hull.removeLast();hull.add(p);}
        if(!hull.isEmpty())hull.removeLast();
        if(hull.size()<3)return Double.POSITIVE_INFINITY;
        double minimum=Double.POSITIVE_INFINITY;
        for(int i=0;i<hull.size();i++) {
            Point a=hull.get(i),b=hull.get((i+1)%hull.size());
            double length=Math.hypot(b.x-a.x,b.y-a.y),width=0;
            for(Point p:hull)width=Math.max(width,Math.abs(cross(a,b,p))/length);
            minimum=Math.min(minimum,width);
        }
        return minimum;
    }
    private static double area(List<Point> p){double sum=0;for(int i=1;i+1<p.size();i++)sum+=cross(p.getFirst(),p.get(i),p.get(i+1));return sum;}
    private static double cross(Point a,Point b,Point c){return(b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);}
    private static boolean intersects(Point a,Point b,Point c,Point d) {
        double abC=cross(a,b,c),abD=cross(a,b,d),cdA=cross(c,d,a),cdB=cross(c,d,b);
        if(abC*abD<-EPS*EPS&&cdA*cdB<-EPS*EPS)return true;
        return Math.abs(abC)<=EPS&&on(a,b,c)||Math.abs(abD)<=EPS&&on(a,b,d)
                ||Math.abs(cdA)<=EPS&&on(c,d,a)||Math.abs(cdB)<=EPS&&on(c,d,b);
    }
    private static boolean on(Point a,Point b,Point p){return p.x>=Math.min(a.x,b.x)-EPS&&p.x<=Math.max(a.x,b.x)+EPS&&p.y>=Math.min(a.y,b.y)-EPS&&p.y<=Math.max(a.y,b.y)+EPS;}
}
