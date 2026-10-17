package com.mine.geometry_node.client.runtime.game.snap;

import com.mine.geometry_node.client.runtime.game.render.PresentedViewportFrame;
import com.mine.geometry_node.client.runtime.game.scene.ViewportSceneMath;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.mine.geometry_node.client.runtime.game.snap.ViewportSnapSettings.Target;

/** Shared move solver for modal G and axis handles. No world, input, or UI state is read here. */
public final class ViewportSnapSolver {
    private ViewportSnapSolver() {}
    public static Vec3 grid(Vec3 origin, Vec3 movement, int axes) {
        if(movement.lengthSqr()<1e-16)return Vec3.ZERO;
        Vec3 p=origin.add(movement);
        return mask(new Vec3(round(p.x)-origin.x,round(p.y)-origin.y,round(p.z)-origin.z),axes);
    }
    private static double round(double value) { return Math.floor(value*2+.5)*.5; }
    private static Vec3 mask(Vec3 v,int axes) {
        int bits=axes==0?7:axes;
        return new Vec3((bits&1)!=0?v.x:0,(bits&2)!=0?v.y:0,(bits&4)!=0?v.z:0);
    }
    public static Vec3 move(ViewportSnapSettings settings, Vec3 origin, Vec3 movement, int axes,
                            List<AABB> sourceBounds, PresentedViewportFrame frame, ViewportSnapGeometry geometry,
                            double cursorU, double cursorV, double radiusU, double radiusV) {
        if(!settings.enabled())return movement;
        Vec3 reference=origin.add(movement);
        Search search=new Search(settings,origin,movement,axes,sourceBounds,frame,cursorU,cursorV,radiusU,radiusV);
        Vec3 frontFace=null,nearestFace=null;double frontDistance=Double.POSITIVE_INFINITY,nearestDistance=Double.POSITIVE_INFINITY;
        if(geometry.generation()==frame.scene().worldGeneration())for(var mesh:geometry.meshes()) {
            if(settings.targets().contains(Target.VERTEX))for(var vertex:mesh.vertices())search.offer(vertex);
            if(settings.targets().contains(Target.EDGE)||settings.targets().contains(Target.EDGE_MIDPOINT))for(var edge:mesh.edges()) {
                if(settings.targets().contains(Target.EDGE_MIDPOINT))search.offer(edge.a().add(edge.b()).scale(.5));
                if(settings.targets().contains(Target.EDGE))search.offer(search.edge(edge));
            }
            double entry=Double.POSITIVE_INFINITY,exit=Double.NEGATIVE_INFINITY;
            var projectionRay=search.ray==null?null:new ViewportSceneMath.Ray(reference.subtract(search.ray.direction().scale(1024)),search.ray.direction(),2048);
            double projectDistance=Double.POSITIVE_INFINITY;
            Vec3 projected=null;
            for(var face:mesh.faces()) {
                double hit=intersection(search.ray,face);
                if(Double.isFinite(hit)) {
                    entry=Math.min(entry,hit);exit=Math.max(exit,hit);
                    if(hit<frontDistance){frontDistance=hit;frontFace=search.ray.origin().add(search.ray.direction().scale(hit));}
                }
                if(settings.targets().contains(Target.FACE_NEAREST)) {
                    var point=closest(reference,face);double distance=point.distanceToSqr(reference);
                    if(distance<nearestDistance){nearestDistance=distance;nearestFace=point;}
                }
                if(settings.targets().contains(Target.FACE_PROJECT)) {
                    double t=intersection(projectionRay,face),distance=Math.abs(t-1024);
                    if(Double.isFinite(t)&&distance<projectDistance){projectDistance=distance;projected=projectionRay.origin().add(projectionRay.direction().scale(t));}
                }
            }
            if(projected!=null)search.offer(projected);
            // A volume requires entry AND exit, not a single open triangle hit.
            if(settings.targets().contains(Target.VOLUME)&&mesh.closed()&&Double.isFinite(entry)&&exit-entry>1e-7)
                search.offer(search.ray.origin().add(search.ray.direction().scale((entry+exit)*.5)));
        }
        if(settings.targets().contains(Target.FACE)&&frontFace!=null)search.offer(frontFace);
        if(nearestFace!=null)search.offer(nearestFace);
        if(search.best!=null)return search.best;
        if(settings.targets().contains(Target.GRID)) {
            Vec3 anchor=origin;
            if(settings.base()==ViewportSnapSettings.Base.CLOSEST&&!sourceBounds.isEmpty()) {
                double best=Double.POSITIVE_INFINITY;
                for(var box:sourceBounds)for(var vertex:ViewportSnapGeometry.Mesh.box(box).vertices()) {
                    Vec3 delta=grid(vertex,movement,axes);
                    double distance=delta.distanceToSqr(movement);
                    if(distance<best){best=distance;anchor=vertex;}
                }
            }
            return grid(anchor,movement,axes);
        }
        if(settings.targets().contains(Target.INCREMENT))return mask(new Vec3(round(movement.x),round(movement.y),round(movement.z)),axes);
        return movement;
    }
    private static final class Search {
        final ViewportSnapSettings settings;
        final Vec3 origin,movement,reference;
        final int axes;
        final List<AABB> bounds;
        final PresentedViewportFrame frame;
        final double cursorU,cursorV,radiusU,radiusV;
        final ViewportSceneMath.Ray ray;
        Vec3 best;
        double score=Double.POSITIVE_INFINITY;
        Search(ViewportSnapSettings settings,Vec3 origin,Vec3 movement,int axes,List<AABB> bounds,
               PresentedViewportFrame frame,double cursorU,double cursorV,double radiusU,double radiusV) {
            this.settings=settings;this.origin=origin;this.movement=movement;this.axes=axes;this.bounds=bounds;
            this.frame=frame;this.radiusU=radiusU;this.radiusV=radiusV;
            reference=origin.add(movement);this.cursorU=cursorU;this.cursorV=cursorV;
            ray=Double.isFinite(cursorU)&&Double.isFinite(cursorV)&&cursorU>=0&&cursorU<=1&&cursorV>=0&&cursorV<=1
                    ?ViewportSceneMath.ray(frame,cursorU,cursorV):null;
        }
        Vec3 edge(ViewportSnapGeometry.Edge edge) {
            // Minimize image-space distance, then recover perspective-correct world interpolation.
            var a=ViewportSceneMath.project(frame,edge.a());var b=ViewportSceneMath.project(frame,edge.b());
            if(!a.visible()||!b.visible())return null;
            double x=(b.u()-a.u())/radiusU,y=(b.v()-a.v())/radiusV,den=x*x+y*y;
            double t=den<1e-16?0:Math.clamp(((cursorU-a.u())/radiusU*x+(cursorV-a.v())/radiusV*y)/den,0,1);
            if(!frame.camera().orthographic()) {
                var centerRay=ViewportSceneMath.ray(frame,.5,.5);
                var camera=frame.camera();var eye=new Vec3(camera.x(),camera.y(),camera.z());
                double da=edge.a().subtract(eye).dot(centerRay.direction()),db=edge.b().subtract(eye).dot(centerRay.direction());
                if(da>0&&db>0)t=(t/db)/((1-t)/da+t/db);
            }
            return edge.a().add(edge.b().subtract(edge.a()).scale(t));
        }
        void offer(Vec3 target) {
            if(target==null)return;
            var p=ViewportSceneMath.project(frame,target);
            if(!p.visible()||ray==null)return;
            double dx=(p.u()-cursorU)/radiusU,dy=(p.v()-cursorV)/radiusV;
            double error=dx*dx+dy*dy;if(error>1)return;
            Vec3 base=reference;
            if(settings.base()==ViewportSnapSettings.Base.CLOSEST) {
                double distance=Double.POSITIVE_INFINITY;
                for(var box:bounds) {
                    Vec3 point=onBox(target,box.move(movement));double d=point.distanceToSqr(target);
                    if(d<distance){distance=d;base=point;}
                }
            }
            Vec3 correction=mask(target.subtract(base),axes);
            // Screen proximity first; ties choose the surface requiring least displacement.
            double candidate=error+Math.min(1e6,correction.lengthSqr())*1e-9;
            if(candidate<score){score=candidate;best=movement.add(correction);}
        }
    }
    private static Vec3 onBox(Vec3 p,AABB box) {
        Vec3 q=new Vec3(Math.clamp(p.x,box.minX,box.maxX),Math.clamp(p.y,box.minY,box.maxY),Math.clamp(p.z,box.minZ,box.maxZ));
        if(!box.contains(p))return q;
        double[] distances={p.x-box.minX,box.maxX-p.x,p.y-box.minY,box.maxY-p.y,p.z-box.minZ,box.maxZ-p.z};
        int face=0;for(int i=1;i<6;i++)if(distances[i]<distances[face])face=i;
        return switch(face){case 0->new Vec3(box.minX,p.y,p.z);case 1->new Vec3(box.maxX,p.y,p.z);case 2->new Vec3(p.x,box.minY,p.z);case 3->new Vec3(p.x,box.maxY,p.z);case 4->new Vec3(p.x,p.y,box.minZ);default->new Vec3(p.x,p.y,box.maxZ);};
    }
    /** Two-sided Möller–Trumbore; outside the published camera segment is never a hit. */
    static double intersection(ViewportSceneMath.Ray ray,ViewportSnapGeometry.Triangle face) {
        if(ray==null)return Double.POSITIVE_INFINITY;
        Vec3 ab=face.b().subtract(face.a()),ac=face.c().subtract(face.a()),h=ray.direction().cross(ac);
        double determinant=ab.dot(h);if(Math.abs(determinant)<1e-12)return Double.POSITIVE_INFINITY;
        Vec3 s=ray.origin().subtract(face.a());double u=s.dot(h)/determinant;
        if(u<0||u>1)return Double.POSITIVE_INFINITY;
        Vec3 q=s.cross(ab);double v=ray.direction().dot(q)/determinant;
        if(v<0||u+v>1)return Double.POSITIVE_INFINITY;
        double t=ac.dot(q)/determinant;return t>=0&&t<=ray.length()?t:Double.POSITIVE_INFINITY;
    }
    /** Closest point on a triangle, including vertex/edge Voronoi regions. */
    static Vec3 closest(Vec3 p,ViewportSnapGeometry.Triangle t) {
        Vec3 a=t.a(),b=t.b(),c=t.c(),ab=b.subtract(a),ac=c.subtract(a),ap=p.subtract(a);
        double d1=ab.dot(ap),d2=ac.dot(ap);if(d1<=0&&d2<=0)return a;
        Vec3 bp=p.subtract(b);double d3=ab.dot(bp),d4=ac.dot(bp);if(d3>=0&&d4<=d3)return b;
        double vc=d1*d4-d3*d2;if(vc<=0&&d1>=0&&d3<=0)return a.add(ab.scale(d1/(d1-d3)));
        Vec3 cp=p.subtract(c);double d5=ab.dot(cp),d6=ac.dot(cp);if(d6>=0&&d5<=d6)return c;
        double vb=d5*d2-d1*d6;if(vb<=0&&d2>=0&&d6<=0)return a.add(ac.scale(d2/(d2-d6)));
        double va=d3*d6-d5*d4;if(va<=0&&d4-d3>=0&&d5-d6>=0)return b.add(c.subtract(b).scale((d4-d3)/((d4-d3)+(d5-d6))));
        double denominator=va+vb+vc;if(Math.abs(denominator)<1e-20)return a;
        return a.add(ab.scale(vb/denominator)).add(ac.scale(vc/denominator));
    }
}
