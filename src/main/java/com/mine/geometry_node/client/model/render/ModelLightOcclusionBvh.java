package com.mine.geometry_node.client.model.render;

import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import java.io.IOException;
import java.util.Objects;

/**
 * Immutable, asset-local opaque triangle occluders. Build on a worker and transform both segment
 * endpoints into this coordinate system before querying. Materials, instances and light ownership
 * belong to the caller; this class does not interpret MASK textures or transparent transmission.
 */
public final class ModelLightOcclusionBvh {
    private static final int LEAF_TRIANGLES = 8;
    public record Budget(int maxTriangles, long maxBytes, long maxBuildNanos) {
        public Budget {
            if (maxTriangles < 0 || maxBytes < 0 || maxBuildNanos <= 0)
                throw new IllegalArgumentException("invalid occlusion build budget");
        }
    }
    public static final class BudgetExceeded extends IOException {
        public BudgetExceeded(String reason) { super(reason); }
    }
    private final float[] triangles, bounds, opacity;
    private final int[] first, count, escape;
    private final long buildNanos;

    private ModelLightOcclusionBvh(Builder b, long nanos) {
        triangles=b.triangles; opacity=b.opacity; bounds=b.bounds; first=b.first; count=b.count; escape=b.escape;
        buildNanos=nanos;
    }
    public int triangleCount() { return triangles.length / 9; }
    public int nodeCount() { return first.length; }
    /** Primitive array payload only; excludes JVM-dependent object/array headers and caller input. */
    public long cpuBytes() { return 4L * (triangles.length + (opacity == null ? 0L : opacity.length) + (long)bounds.length + first.length + count.length + escape.length); }
    public long buildNanos() { return buildNanos; }

    public int triangleTexelOffset() { return nodeCount()*3; }
    public long gpuBytes() { return 48L*(nodeCount()+(long)triangleCount()); }
    /**
     * RGBA32F upload snapshot: three texels per node (min.xyz/escape, max.xyz/first,
     * count/0/0/0), then three texels per triangle (vertex.xyz/opacity for vertex 0, vertex.xyz/0 for others). Internal count is zero.
     * The caller budgets this temporary array separately and releases it after upload.
     * No traversal limit may silently treat unvisited nodes as unoccluded.
     */
    public float[] gpuTexels(long maxUploadBytes) throws BudgetExceeded {
        try { return export(maxUploadBytes,null,Long.MAX_VALUE); }
        catch (BudgetExceeded failure) { throw failure; }
        catch (IOException impossible) { throw new AssertionError(impossible); }
    }
    public float[] gpuTexels(long maxUploadBytes, ModelCancellation cancellation, long maxExportNanos) throws IOException {
        Objects.requireNonNull(cancellation);
        if(maxExportNanos<=0) throw new IllegalArgumentException("positive export time budget required");
        return export(maxUploadBytes,cancellation,maxExportNanos);
    }
    private float[] export(long maxUploadBytes,ModelCancellation cancellation,long maxExportNanos) throws IOException {
        long start=System.nanoTime();checkExport(cancellation,start,maxExportNanos);
        long bytes=gpuBytes();
        if (bytes>maxUploadBytes || bytes/4>Integer.MAX_VALUE) throw new BudgetExceeded("gpu_upload_budget");
        // All integer metadata must round-trip through float without losing an index.
        if (nodeCount()>16_777_216 || triangleCount()>16_777_216) throw new BudgetExceeded("gpu_index_precision");
        float[] output=new float[(int)(bytes/4)];
        for(int n=0;n<nodeCount();n++) {
            if((n&1023)==0)checkExport(cancellation,start,maxExportNanos);
            int i=n*12,b=n*6;
            output[i]=bounds[b];output[i+1]=bounds[b+1];output[i+2]=bounds[b+2];output[i+3]=escape[n];
            output[i+4]=bounds[b+3];output[i+5]=bounds[b+4];output[i+6]=bounds[b+5];output[i+7]=first[n];
            output[i+8]=count[n];
        }
        int base=nodeCount()*12;
        for(int t=0;t<triangleCount();t++) {
            if((t&1023)==0)checkExport(cancellation,start,maxExportNanos);
            output[base+t*12+3] = opacity == null ? 1 : opacity[t];
            for(int v=0;v<3;v++) {
                int i=base+t*12+v*4, source=t*9+v*3;
                output[i]=triangles[source];output[i+1]=triangles[source+1];output[i+2]=triangles[source+2];
            }
        }
        checkExport(cancellation,start,maxExportNanos);
        return output;
    }
    private static void checkExport(ModelCancellation cancellation,long start,long maxExportNanos) throws IOException {
        if(cancellation!=null)cancellation.check();
        if(System.nanoTime()-start>maxExportNanos)throw new BudgetExceeded("export_time_budget");
    }

    public static ModelLightOcclusionBvh build(float[] triangleSoup, Budget budget,
                                                ModelCancellation cancellation) throws IOException {
        return build(triangleSoup,null,budget,cancellation,false);
    }
    /**
     * Worker-only ownership transfer: the caller must exclusively own this temporary soup and
     * relinquish ALL reads/writes/reuse, including when construction fails or is cancelled.
     * Reorders that array in place and retains it, avoiding a second triangle-sized allocation.
     * cpuBytes includes the transferred array exactly as in the public copying build.
     */
    static ModelLightOcclusionBvh buildOwned(float[] triangleSoup, Budget budget,
                                            ModelCancellation cancellation) throws IOException {
        return build(triangleSoup,null,budget,cancellation,true);
    }
    static ModelLightOcclusionBvh buildOwned(float[] triangleSoup, float[] opacity, Budget budget,
                                            ModelCancellation cancellation) throws IOException {
        return build(triangleSoup,opacity,budget,cancellation,true);
    }
    private static ModelLightOcclusionBvh build(float[] triangleSoup, float[] opacity, Budget budget,
                                               ModelCancellation cancellation, boolean owned) throws IOException {
        Objects.requireNonNull(triangleSoup); Objects.requireNonNull(budget); Objects.requireNonNull(cancellation);
        cancellation.check(); long start=System.nanoTime();
        if (triangleSoup.length % 9 != 0) throw new IllegalArgumentException("triangle soup must contain nine floats per triangle");
        int n=triangleSoup.length/9;
        if (opacity != null) {
            if (opacity.length != n) throw new IllegalArgumentException("opacity count mismatch");
            for (float alpha : opacity) if (!Float.isFinite(alpha) || alpha < 0 || alpha > 1)
                throw new IllegalArgumentException("opacity must be within 0..1");
        }
        if (n>budget.maxTriangles()) throw new BudgetExceeded("triangle_budget");
        int nodes=nodeCount(n);
        long bytes=4L*triangleSoup.length+36L*nodes+(opacity == null ? 0L : 4L*n);
        if (bytes>budget.maxBytes()) throw new BudgetExceeded("cpu_budget");
        // Java arrays use int indices. Reject before allocating any partial structure.
        if (6L*nodes>Integer.MAX_VALUE) throw new BudgetExceeded("node_array_limit");
        for (int i=0;i<triangleSoup.length;i++) {
            if ((i&4095)==0) check(cancellation,budget,start);
            if (!Float.isFinite(triangleSoup[i])) throw new IllegalArgumentException("non-finite occluder position");
        }
        Builder b=new Builder(triangleSoup,opacity,nodes,budget,cancellation,start,owned);
        check(cancellation,budget,start);
        if (n>0) b.buildNode(0,n);
        check(cancellation,budget,start);
        return new ModelLightOcclusionBvh(b,System.nanoTime()-start);
    }
    private static int nodeCount(int triangles) {
        if (triangles==0) return 0;
        if (triangles<=LEAF_TRIANGLES) return 1;
        return 1+nodeCount(triangles/2)+nodeCount(triangles-triangles/2);
    }
    private static void check(ModelCancellation cancellation, Budget budget, long start) throws IOException {
        cancellation.check();
        if (System.nanoTime()-start>budget.maxBuildNanos()) throw new BudgetExceeded("worker_time_budget");
    }

    /**
     * Any opaque intersection strictly between the endpoints. Epsilon is a distance in this BVH's
     * coordinate system, excluded at BOTH ends to avoid source/receiver self intersections.
     * Two-sided and allocation-free, including traversal (thread safe, no mutable query scratch).
     */
    public boolean occluded(double sx,double sy,double sz,double rx,double ry,double rz,double endpointEpsilon) {
        if (!Double.isFinite(sx)||!Double.isFinite(sy)||!Double.isFinite(sz)
                ||!Double.isFinite(rx)||!Double.isFinite(ry)||!Double.isFinite(rz)
                ||!Double.isFinite(endpointEpsilon)||endpointEpsilon<0)
            throw new IllegalArgumentException("finite endpoints and nonnegative epsilon required");
        double dx=rx-sx,dy=ry-sy,dz=rz-sz;
        double length=Math.hypot(Math.hypot(dx,dy),dz);
        if (!Double.isFinite(length)) throw new IllegalArgumentException("segment length overflow");
        if (length<=2*endpointEpsilon || length==0 || first.length==0) return false;
        double near=endpointEpsilon/length, far=1-near;
        for (int node=0;node<first.length;) {
            if (!intersectsBox(node,sx,sy,sz,dx,dy,dz,near,far)) { node=escape[node]; continue; }
            if (count[node]>0) {
                int end=first[node]+count[node];
                for (int t=first[node];t<end;t++)
                    if (intersectsTriangle(t*9,sx,sy,sz,dx,dy,dz,near,far)) return true;
            }
            node++;
        }
        return false;
    }
    private boolean intersectsBox(int node,double sx,double sy,double sz,double dx,double dy,double dz,double near,double far) {
        int b=node*6;
        for (int a=0;a<3;a++) {
            double origin=a==0?sx:a==1?sy:sz, direction=a==0?dx:a==1?dy:dz;
            if (direction==0) { if (origin<bounds[b+a]||origin>bounds[b+a+3]) return false; }
            else {
                double lo=(bounds[b+a]-origin)/direction, hi=(bounds[b+a+3]-origin)/direction;
                if (lo>hi) { double swap=lo;lo=hi;hi=swap; }
                near=Math.max(near,lo);far=Math.min(far,hi);
                if (near>far) return false;
            }
        }
        return true;
    }
    private boolean intersectsTriangle(int i,double sx,double sy,double sz,double dx,double dy,double dz,double near,double far) {
        double ax=triangles[i],ay=triangles[i+1],az=triangles[i+2];
        double e1x=triangles[i+3]-ax,e1y=triangles[i+4]-ay,e1z=triangles[i+5]-az;
        double e2x=triangles[i+6]-ax,e2y=triangles[i+7]-ay,e2z=triangles[i+8]-az;
        double px=dy*e2z-dz*e2y,py=dz*e2x-dx*e2z,pz=dx*e2y-dy*e2x;
        double det=e1x*px+e1y*py+e1z*pz;
        double scale=Math.sqrt((e1x*e1x+e1y*e1y+e1z*e1z)*(px*px+py*py+pz*pz));
        if (Math.abs(det)<=1e-12*scale) return false;
        double tx=sx-ax,ty=sy-ay,tz=sz-az;
        double u=(tx*px+ty*py+tz*pz)/det;
        if (u<0||u>1) return false;
        double qx=ty*e1z-tz*e1y,qy=tz*e1x-tx*e1z,qz=tx*e1y-ty*e1x;
        double v=(dx*qx+dy*qy+dz*qz)/det;
        if (v<0||u+v>1) return false;
        double t=(e2x*qx+e2y*qy+e2z*qz)/det;
        return t>near&&t<far;
    }
    private static final class Builder {
        final float[] triangles,bounds,opacity;
        final int[] first,count,escape;
        final Budget budget; final ModelCancellation cancellation; final long start;
        int next;
        Builder(float[] source,float[] opacity,int nodes,Budget budget,ModelCancellation cancellation,long start,boolean owned) {
            triangles=owned?source:source.clone();this.opacity=opacity==null?null:owned?opacity:opacity.clone();bounds=new float[nodes*6];first=new int[nodes];count=new int[nodes];escape=new int[nodes];
            this.budget=budget;this.cancellation=cancellation;this.start=start;
        }
        int buildNode(int from,int to) throws IOException {
            check(cancellation,budget,start);int node=next++,b=node*6;
            bounds[b]=bounds[b+1]=bounds[b+2]=Float.POSITIVE_INFINITY;
            bounds[b+3]=bounds[b+4]=bounds[b+5]=Float.NEGATIVE_INFINITY;
            for (int t=from;t<to;t++) {
                if ((t&1023)==0) check(cancellation,budget,start);
                for (int v=0;v<3;v++) for (int a=0;a<3;a++) {
                    float value=triangles[t*9+v*3+a];
                    bounds[b+a]=Math.min(bounds[b+a],value);bounds[b+3+a]=Math.max(bounds[b+3+a],value);
                }
            }
            if (to-from<=LEAF_TRIANGLES) { first[node]=from;count[node]=to-from; }
            else {
                int axis=0;
                for (int a=1;a<3;a++) if ((double)bounds[b+a+3]-bounds[b+a]>(double)bounds[b+axis+3]-bounds[b+axis]) axis=a;
                int mid=(from+to)>>>1;select(from,to-1,mid,axis);
                buildNode(from,mid);buildNode(mid,to);
            }
            escape[node]=next;return node;
        }
        double centroid(int t,int axis) { int i=t*9+axis;return ((double)triangles[i]+triangles[i+3]+triangles[i+6])/3; }
        void swap(int a,int b) { if(a==b)return;if(opacity!=null){float v=opacity[a];opacity[a]=opacity[b];opacity[b]=v;}for(int k=0;k<9;k++){float v=triangles[a*9+k];triangles[a*9+k]=triangles[b*9+k];triangles[b*9+k]=v;} }
        void select(int lo,int hi,int median,int axis) throws IOException {
            while(lo<hi) {
                check(cancellation,budget,start);
                double pivot=centroid((lo+hi)>>>1,axis);int left=lo,right=hi;
                while(left<=right) {
                    while(centroid(left,axis)<pivot) {left++;if((left&1023)==0)check(cancellation,budget,start);}
                    while(centroid(right,axis)>pivot) {right--;if((right&1023)==0)check(cancellation,budget,start);}
                    if(left<=right) {swap(left++,right--);if((left&1023)==0)check(cancellation,budget,start);}
                }
                if(median<=right)hi=right;else if(median>=left)lo=left;else return;
            }
        }
    }
}
