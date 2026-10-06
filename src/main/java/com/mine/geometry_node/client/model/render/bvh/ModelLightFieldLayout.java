package com.mine.geometry_node.client.model.render.bvh;

import java.util.*;

/** Camera-prioritized, reusable world tiles; planning never enumerates a whole large building. */
final class ModelLightFieldLayout {
    static final int MAX_FIELDS=8, SPACING=28;
    record Bounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {}
    record Tile(int x,int y,int z) {}
    static List<Tile> select(List<Bounds> buildings,double cx,double cy,double cz) {
        Set<Tile> candidates=new HashSet<>();
        for(Bounds b:buildings) {
            if(!Double.isFinite(b.minX)||!Double.isFinite(b.minY)||!Double.isFinite(b.minZ)
                    ||!Double.isFinite(b.maxX)||!Double.isFinite(b.maxY)||!Double.isFinite(b.maxZ)
                    ||b.minX>b.maxX||b.minY>b.maxY||b.minZ>b.maxZ)continue;
            int lx=index(b.minX),ly=index(b.minY),lz=index(b.minZ);
            int hx=index(b.maxX),hy=index(b.maxY),hz=index(b.maxZ);
            int px=Math.clamp(index(cx),lx,hx),py=Math.clamp(index(cy),ly,hy),pz=Math.clamp(index(cz),lz,hz);
            for(int dz=-1;dz<=1;dz++)for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                int x=Math.clamp(px+dx,lx,hx),y=Math.clamp(py+dy,ly,hy),z=Math.clamp(pz+dz,lz,hz);
                candidates.add(new Tile(x*SPACING,y*SPACING,z*SPACING));
            }
        }
        return candidates.stream().sorted(Comparator.comparingDouble((Tile t)->distance(t,cx,cy,cz))
                .thenComparingInt(Tile::x).thenComparingInt(Tile::y).thenComparingInt(Tile::z)).limit(MAX_FIELDS).toList();
    }
    private static int index(double value) {return (int)Math.clamp(Math.round(value/SPACING), (Integer.MIN_VALUE+64L)/SPACING, (Integer.MAX_VALUE-64L)/SPACING);}
    private static double distance(Tile t,double x,double y,double z) {
        double dx=Math.max(0,Math.abs(t.x-x)-14),dy=Math.max(0,Math.abs(t.y-y)-14),dz=Math.max(0,Math.abs(t.z-z)-14);
        return dx*dx+dy*dy+dz*dz;
    }
    private ModelLightFieldLayout() {}
}
