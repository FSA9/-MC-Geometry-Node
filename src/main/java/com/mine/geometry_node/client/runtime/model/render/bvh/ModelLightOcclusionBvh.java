package com.mine.geometry_node.client.runtime.model.render.bvh;

import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable, asset-local opaque triangle occluders. Build on a worker and transform both segment
 * endpoints into this coordinate system before querying. Materials, instances and light ownership
 * belong to the caller; this class does not interpret MASK textures or transparent transmission.
 */
public final class ModelLightOcclusionBvh {
    private static final int LEAF_TRIANGLES = 8;
    static final int NODE_TEXELS = 2;
    // The largest GPU-representable scene (2^24 triangles) needs 22 balanced levels at leaf size 8.
    static final int MAX_TRAVERSAL_STACK = 24;
    /**
     * Binned SAH keeps construction bounded while producing less overlapping child boxes than a
     * plain median split. Every internal node participates so small spatially isolated groups
     * are not forced back into overlapping median partitions.
     */
    private static final int SAH_BINS = 16;
    private static final int SAH_MIN_TRIANGLES = LEAF_TRIANGLES + 1;
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
    private final long buildNanos,peakCpuBytes;
    private final int nodeCount,maxDepth,sahSplitCount,medianSplitCount;

    private ModelLightOcclusionBvh(Builder b, long nanos) {
        triangles=b.triangles; opacity=b.opacity; bounds=b.bounds; first=b.first; count=b.count; escape=b.escape;
        buildNanos=nanos; peakCpuBytes=b.peakCpuBytes; nodeCount=b.next; maxDepth=b.maxDepth;
        sahSplitCount=b.sahSplitCount; medianSplitCount=b.medianSplitCount;
    }
    public int triangleCount() { return triangles.length / 9; }
    public int nodeCount() { return nodeCount; }
    public int nodeCapacity() { return first.length; }
    public int maxDepth() { return maxDepth; }
    public int sahSplitCount() { return sahSplitCount; }
    public int medianSplitCount() { return medianSplitCount; }
    /** Primitive array payload only; excludes JVM-dependent object/array headers and caller input. */
    public long cpuBytes() { return 4L * (triangles.length + (opacity == null ? 0L : opacity.length) + (long)bounds.length + first.length + count.length + escape.length); }
    public long buildNanos() { return buildNanos; }
    /** Triangle/node array payload at construction peak, including old/new arrays during growth. */
    public long peakCpuBytes() { return peakCpuBytes; }

    public int triangleTexelOffset() { return nodeCount()*NODE_TEXELS; }
    public long gpuBytes() { return NODE_TEXELS*16L*nodeCount()+48L*triangleCount(); }
    /**
     * RGBA32F upload snapshot: two texels per node (min.xyz/escape, max.xyz/metadata),
     * then three texels per triangle (vertex.xyz/opacity for vertex 0, vertex.xyz/0 for
     * the remaining vertices). Metadata stores a 24-bit first/right index and four-bit count
     * in the bits of a normal finite float, recovered with floatBitsToInt in the shader.
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
        // Escape links must round-trip numerically; packed first/right indices use 24 bits.
        if (nodeCount()>16_777_216 || triangleCount()>16_777_216) throw new BudgetExceeded("gpu_index_precision");
        if (maxDepth>MAX_TRAVERSAL_STACK) throw new BudgetExceeded("gpu_traversal_stack_budget");
        float[] output=new float[(int)(bytes/4)];
        for(int n=0;n<nodeCount();n++) {
            if((n&1023)==0)checkExport(cancellation,start,maxExportNanos);
            int i=n*NODE_TEXELS*4,b=n*6;
            output[i]=bounds[b];output[i+1]=bounds[b+1];output[i+2]=bounds[b+2];output[i+3]=escape[n];
            output[i+4]=bounds[b+3];output[i+5]=bounds[b+4];output[i+6]=bounds[b+5];
            output[i+7]=Float.intBitsToFloat(0x40000000|(first[n]<<4)|count[n]);
        }
        int base=nodeCount()*NODE_TEXELS*4;
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
        return build(triangleSoup,null,budget,cancellation,false,Integer.MAX_VALUE);
    }
    /**
     * Worker-only ownership transfer: the caller must exclusively own this temporary soup and
     * relinquish ALL reads/writes/reuse, including when construction fails or is cancelled.
     * Reorders that array in place and retains it, avoiding a second triangle-sized allocation.
     * cpuBytes includes the transferred array exactly as in the public copying build.
     */
    static ModelLightOcclusionBvh buildOwned(float[] triangleSoup, Budget budget,
                                            ModelCancellation cancellation) throws IOException {
        return build(triangleSoup,null,budget,cancellation,true,Integer.MAX_VALUE);
    }
    static ModelLightOcclusionBvh buildOwned(float[] triangleSoup, float[] opacity, Budget budget,
                                            ModelCancellation cancellation) throws IOException {
        return build(triangleSoup,opacity,budget,cancellation,true,Integer.MAX_VALUE);
    }
    static ModelLightOcclusionBvh buildOwned(float[] triangleSoup, float[] opacity, Budget budget,
            ModelCancellation cancellation, int maxNodeCapacity) throws IOException {
        return build(triangleSoup,opacity,budget,cancellation,true,maxNodeCapacity);
    }
    private static ModelLightOcclusionBvh build(float[] triangleSoup, float[] opacity, Budget budget,
            ModelCancellation cancellation, boolean owned, int maxNodeCapacity) throws IOException {
        Objects.requireNonNull(triangleSoup); Objects.requireNonNull(budget); Objects.requireNonNull(cancellation);
        if(maxNodeCapacity<0)throw new IllegalArgumentException("nonnegative node capacity required");
        cancellation.check(); long start=System.nanoTime();
        if (triangleSoup.length % 9 != 0) throw new IllegalArgumentException("triangle soup must contain nine floats per triangle");
        int n=triangleSoup.length/9;
        if (opacity != null) {
            if (opacity.length != n) throw new IllegalArgumentException("opacity count mismatch");
            for (float alpha : opacity) if (!Float.isFinite(alpha) || alpha < 0 || alpha > 1)
                throw new IllegalArgumentException("opacity must be within 0..1");
        }
        if (n>budget.maxTriangles()) throw new BudgetExceeded("triangle_budget");
        long triangleBytes=4L*triangleSoup.length+(opacity == null ? 0L : 4L*n);
        if (triangleBytes>budget.maxBytes()) throw new BudgetExceeded("cpu_budget");
        long maximumNodes=n==0?0:2L*n-1;
        int capacityLimit=(int)Math.min(Math.min(maximumNodes,Integer.MAX_VALUE/6),
                Math.min((long)maxNodeCapacity,(budget.maxBytes()-triangleBytes)/36));
        if(n>0&&capacityLimit==0)throw new BudgetExceeded("cpu_budget");
        int nodes=Math.min(nodeCount(n),capacityLimit);
        for (int i=0;i<triangleSoup.length;i++) {
            if ((i&4095)==0) check(cancellation,budget,start);
            if (!Float.isFinite(triangleSoup[i])) throw new IllegalArgumentException("non-finite occluder position");
        }
        Builder b=new Builder(triangleSoup,opacity,nodes,capacityLimit,triangleBytes,budget,cancellation,start,owned);
        check(cancellation,budget,start);
        if (n>0) b.buildNode(0,n,0);
        check(cancellation,budget,start);
        return new ModelLightOcclusionBvh(b,System.nanoTime()-start);
    }
    private static int nodeCount(int triangles) {
        if (triangles==0) return 0;
        if (triangles<=LEAF_TRIANGLES) return 1;
        return 1+nodeCount(triangles/2)+nodeCount(triangles-triangles/2);
    }
    private static int balancedDepth(int triangles) {
        return Integer.SIZE-Integer.numberOfLeadingZeros((triangles-1)/LEAF_TRIANGLES);
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
        if (length<=2*endpointEpsilon || length==0 || nodeCount==0) return false;
        double near=endpointEpsilon/length, far=1-near;
        for (int node=0;node<nodeCount;) {
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
        final float[] triangles,opacity;
        float[] bounds;
        int[] first,count,escape;
        final Budget budget; final ModelCancellation cancellation; final long start;
        final int capacityLimit;
        final long triangleBytes;
        final int[] binCounts = new int[3 * SAH_BINS];
        final float[] binBounds = new float[3 * SAH_BINS * 6];
        final int[] prefixCounts = new int[SAH_BINS];
        final int[] suffixCounts = new int[SAH_BINS];
        final float[] prefixBounds = new float[SAH_BINS * 6];
        final float[] suffixBounds = new float[SAH_BINS * 6];
        int next,maxDepth,sahSplitCount,medianSplitCount;
        long peakCpuBytes;
        Builder(float[] source,float[] opacity,int nodes,int capacityLimit,long triangleBytes,
                Budget budget,ModelCancellation cancellation,long start,boolean owned) {
            triangles=owned?source:source.clone();this.opacity=opacity==null?null:owned?opacity:opacity.clone();bounds=new float[nodes*6];first=new int[nodes];count=new int[nodes];escape=new int[nodes];
            this.budget=budget;this.cancellation=cancellation;this.start=start;
            this.capacityLimit=capacityLimit;this.triangleBytes=triangleBytes;
            peakCpuBytes=triangleBytes+36L*nodes;
        }
        void ensureCapacity(int required) throws IOException {
            int previous=first.length;
            if(required<=previous)return;
            check(cancellation,budget,start);
            if(required>capacityLimit)throw new BudgetExceeded("node_capacity_budget");
            long desired=Math.min(capacityLimit,Math.max(required,(long)previous+Math.max(1,previous/2)));
            long affordable=(budget.maxBytes()-triangleBytes)/36-previous;
            int capacity=(int)Math.min(desired,affordable);
            if(capacity<required)throw new BudgetExceeded("cpu_growth_budget");
            // Both generations can coexist until the JVM collects the old arrays.
            peakCpuBytes=Math.max(peakCpuBytes,triangleBytes+36L*(previous+(long)capacity));
            bounds=Arrays.copyOf(bounds,capacity*6);
            first=Arrays.copyOf(first,capacity);
            count=Arrays.copyOf(count,capacity);
            escape=Arrays.copyOf(escape,capacity);
            check(cancellation,budget,start);
        }
        int buildNode(int from,int to,int depth) throws IOException {
            check(cancellation,budget,start);ensureCapacity(next+1);int node=next++,b=node*6;
            maxDepth=Math.max(maxDepth,depth+1);
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
                int split = (to - from) >= SAH_MIN_TRIANGLES ? findSahSplit(from, to, b) : -1;
                int axis;
                int mid = -1;
                if (split >= 0) {
                    axis = split >>> 8;
                    int splitBin = split & 0xff;
                    float min = bounds[b + axis];
                    float max = bounds[b + axis + 3];
                    mid = partitionByBin(from, to, axis, splitBin, min, (double) max - min);
                    // A valid SAH candidate has populated bins on both sides. Keep a defensive
                    // fallback for NaN/rounding edge cases so no node can become empty.
                    // Reserve enough remaining levels for a median split to fit the GPU stack.
                    if (mid <= from || mid >= to
                            || depth + 1 + balancedDepth(mid - from) >= MAX_TRAVERSAL_STACK
                            || depth + 1 + balancedDepth(to - mid) >= MAX_TRAVERSAL_STACK) split = -1;
                }
                if (split < 0) {
                    medianSplitCount++;
                    axis = longestAxis(b);
                    mid = (from + to) >>> 1;
                    select(from, to - 1, mid, axis);
                } else sahSplitCount++;
                buildNode(from,mid,depth+1);
                int right = buildNode(mid,to,depth+1);
                // Internal nodes keep the right-child index in the existing `first` slot.
                // Leaves continue to use it as their first triangle index. Threaded traversal
                // can still use escape links without changing the atlas layout.
                first[node] = right;
            }
            escape[node]=next;return node;
        }
        int longestAxis(int b) {
            int axis = 0;
            for (int a=1;a<3;a++)
                if ((double)bounds[b+a+3]-bounds[b+a] > (double)bounds[b+axis+3]-bounds[b+axis]) axis=a;
            return axis;
        }

        /**
         * Find a split with binned surface-area heuristic (SAH). The return value packs axis in
         * the high byte and the leftmost bin in the low byte, or -1 when centroids are degenerate.
         * All scratch arrays are owned by this builder and are reused between recursive calls.
         */
        int findSahSplit(int from, int to, int nodeBounds) throws IOException {
            Arrays.fill(binCounts, 0);
            for (int i=0;i<binBounds.length;i+=6) {
                binBounds[i]=binBounds[i+1]=binBounds[i+2]=Float.POSITIVE_INFINITY;
                binBounds[i+3]=binBounds[i+4]=binBounds[i+5]=Float.NEGATIVE_INFINITY;
            }
            float minX=bounds[nodeBounds], minY=bounds[nodeBounds+1], minZ=bounds[nodeBounds+2];
            float maxX=bounds[nodeBounds+3], maxY=bounds[nodeBounds+4], maxZ=bounds[nodeBounds+5];
            double extentX=(double)maxX-minX, extentY=(double)maxY-minY, extentZ=(double)maxZ-minZ;
            if (!(extentX>0) && !(extentY>0) && !(extentZ>0)) return -1;

            for (int t=from;t<to;t++) {
                if (((t-from)&1023)==0) check(cancellation,budget,start);
                int i=t*9;
                float x0=triangles[i], y0=triangles[i+1], z0=triangles[i+2];
                float x1=triangles[i+3], y1=triangles[i+4], z1=triangles[i+5];
                float x2=triangles[i+6], y2=triangles[i+7], z2=triangles[i+8];
                float triMinX=Math.min(x0,Math.min(x1,x2)), triMaxX=Math.max(x0,Math.max(x1,x2));
                float triMinY=Math.min(y0,Math.min(y1,y2)), triMaxY=Math.max(y0,Math.max(y1,y2));
                float triMinZ=Math.min(z0,Math.min(z1,z2)), triMaxZ=Math.max(z0,Math.max(z1,z2));
                addBin(0, binIndex(((double)x0+x1+x2)/3.0,minX,extentX), triMinX,triMinY,triMinZ,triMaxX,triMaxY,triMaxZ);
                addBin(1, binIndex(((double)y0+y1+y2)/3.0,minY,extentY), triMinX,triMinY,triMinZ,triMaxX,triMaxY,triMaxZ);
                addBin(2, binIndex(((double)z0+z1+z2)/3.0,minZ,extentZ), triMinX,triMinY,triMinZ,triMaxX,triMaxY,triMaxZ);
            }

            double bestCost=Double.POSITIVE_INFINITY;
            int bestAxis=-1,bestBin=-1;
            for (int axis=0;axis<3;axis++) {
                int axisOffset=axis*SAH_BINS;
                int running=0;
                float pMinX=Float.POSITIVE_INFINITY,pMinY=Float.POSITIVE_INFINITY,pMinZ=Float.POSITIVE_INFINITY;
                float pMaxX=Float.NEGATIVE_INFINITY,pMaxY=Float.NEGATIVE_INFINITY,pMaxZ=Float.NEGATIVE_INFINITY;
                for (int bin=0;bin<SAH_BINS;bin++) {
                    int source=(axisOffset+bin)*6, target=bin*6;
                    running+=binCounts[axisOffset+bin];
                    pMinX=Math.min(pMinX,binBounds[source]); pMinY=Math.min(pMinY,binBounds[source+1]); pMinZ=Math.min(pMinZ,binBounds[source+2]);
                    pMaxX=Math.max(pMaxX,binBounds[source+3]); pMaxY=Math.max(pMaxY,binBounds[source+4]); pMaxZ=Math.max(pMaxZ,binBounds[source+5]);
                    prefixCounts[bin]=running;
                    prefixBounds[target]=pMinX; prefixBounds[target+1]=pMinY; prefixBounds[target+2]=pMinZ;
                    prefixBounds[target+3]=pMaxX; prefixBounds[target+4]=pMaxY; prefixBounds[target+5]=pMaxZ;
                }
                running=0;
                float sMinX=Float.POSITIVE_INFINITY,sMinY=Float.POSITIVE_INFINITY,sMinZ=Float.POSITIVE_INFINITY;
                float sMaxX=Float.NEGATIVE_INFINITY,sMaxY=Float.NEGATIVE_INFINITY,sMaxZ=Float.NEGATIVE_INFINITY;
                for (int bin=SAH_BINS-1;bin>=0;bin--) {
                    int source=(axisOffset+bin)*6, target=bin*6;
                    running+=binCounts[axisOffset+bin];
                    sMinX=Math.min(sMinX,binBounds[source]); sMinY=Math.min(sMinY,binBounds[source+1]); sMinZ=Math.min(sMinZ,binBounds[source+2]);
                    sMaxX=Math.max(sMaxX,binBounds[source+3]); sMaxY=Math.max(sMaxY,binBounds[source+4]); sMaxZ=Math.max(sMaxZ,binBounds[source+5]);
                    suffixCounts[bin]=running;
                    suffixBounds[target]=sMinX; suffixBounds[target+1]=sMinY; suffixBounds[target+2]=sMinZ;
                    suffixBounds[target+3]=sMaxX; suffixBounds[target+4]=sMaxY; suffixBounds[target+5]=sMaxZ;
                }
                for (int bin=0;bin<SAH_BINS-1;bin++) {
                    int left=prefixCounts[bin], right=suffixCounts[bin+1];
                    if (left==0 || right==0) continue;
                    double cost=surfaceArea(prefixBounds,bin*6)*left + surfaceArea(suffixBounds,(bin+1)*6)*right;
                    if (cost<bestCost) { bestCost=cost;bestAxis=axis;bestBin=bin; }
                }
            }
            return bestAxis<0 ? -1 : (bestAxis<<8)|bestBin;
        }

        void addBin(int axis,int bin,float minX,float minY,float minZ,float maxX,float maxY,float maxZ) {
            int index=axis*SAH_BINS+bin, b=index*6;
            binCounts[index]++;
            binBounds[b]=Math.min(binBounds[b],minX); binBounds[b+1]=Math.min(binBounds[b+1],minY); binBounds[b+2]=Math.min(binBounds[b+2],minZ);
            binBounds[b+3]=Math.max(binBounds[b+3],maxX); binBounds[b+4]=Math.max(binBounds[b+4],maxY); binBounds[b+5]=Math.max(binBounds[b+5],maxZ);
        }

        int binIndex(double centroid,float min,double extent) {
            if (!(extent>0)) return 0;
            int bin=(int)((centroid-min)/extent*SAH_BINS);
            return bin<0 ? 0 : bin>=SAH_BINS ? SAH_BINS-1 : bin;
        }

        int partitionByBin(int lo,int hi,int axis,int splitBin,float min,double extent) throws IOException {
            int left=lo,right=hi-1;
            int moved=0;
            while (left<=right) {
                while (left<=right && binIndex(centroid(left,axis),min,extent)<=splitBin) {
                    left++; if ((++moved&1023)==0) check(cancellation,budget,start);
                }
                while (left<=right && binIndex(centroid(right,axis),min,extent)>splitBin) {
                    right--; if ((++moved&1023)==0) check(cancellation,budget,start);
                }
                if (left<right) { swap(left++,right--); if ((++moved&1023)==0) check(cancellation,budget,start); }
            }
            return left;
        }

        static double surfaceArea(float[] box,int b) {
            double x=(double)box[b+3]-box[b], y=(double)box[b+4]-box[b+1], z=(double)box[b+5]-box[b+2];
            return x>=0&&y>=0&&z>=0 ? 2.0*(x*y+y*z+z*x) : 0.0;
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
