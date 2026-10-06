package com.mine.geometry_node.client.model.render.bvh;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bounded render-thread snapshot; never requests chunk loads or scans the whole level. */
final class ModelPlacedLightVolume {
    static final int SIZE = 64, MAX_SOURCES = 16, TEXTURE_WIDTH = 512;
    static final int CELLS = SIZE * SIZE * SIZE;
    private static final Comparator<Source> BRIGHTEST_FIRST = Comparator.comparingInt(Source::emission).reversed();
    record Source(int x, int y, int z, int emission) {}
    /** -1 means unavailable chunk; otherwise low four bits emission, bit 4 full light blocker. */
    @FunctionalInterface interface Reader { int read(int x, int y, int z); }
    final int x, y, z;
    final byte[] blockers = new byte[CELLS];
    final List<Source> sources = new ArrayList<>();
    private int cursor;
    private String state = "SCANNING";
    ModelPlacedLightVolume(int x, int y, int z) { this.x=x; this.y=y; this.z=z; }
    boolean contains(int px, int py, int pz) {
        return (long)px-x>=0 && (long)px-x<SIZE && (long)py-y>=0 && (long)py-y<SIZE && (long)pz-z>=0 && (long)pz-z<SIZE;
    }
    boolean ready() { return state.equals("READY"); }
    String state() { return state; }
    int scannedCells() { return cursor; }
    /** A block update patches a finished snapshot in O(source cap), without rescanning its volume. */
    boolean patch(int px,int py,int pz,int value) {
        if(!ready() || !contains(px,py,pz) || value<0) return false;
        sources.removeIf(source -> source.x()==px && source.y()==py && source.z()==pz);
        blockers[(px-x)+SIZE*((py-y)+SIZE*(pz-z))]=(byte)((value&16)!=0?255:0);
        int emission=value&15;
        if(emission>0) {
            if(sources.size()==MAX_SOURCES) {state="SOURCE_LIMIT";return false;}
            sources.add(new Source(px,py,pz,emission));
            sources.sort(BRIGHTEST_FIRST);
        }
        return true;
    }
    int step(Reader reader, int maxCells, long maxNanos) {
        if (maxCells <= 0 || maxNanos <= 0) throw new IllegalArgumentException("positive scan budget required");
        if (!state.equals("SCANNING")) return 0;
        long start=System.nanoTime(); int before=cursor;
        while(cursor<CELLS && cursor-before<maxCells) {
            if (((cursor-before)&63)==0 && System.nanoTime()-start>=maxNanos) break;
            int px=x+cursor%SIZE, py=y+(cursor/SIZE)%SIZE, pz=z+cursor/(SIZE*SIZE);
            int value=reader.read(px,py,pz);
            if(value<0) { state="UNLOADED_REGION";break; }
            int emission=value&15;
            if(emission>0) {
                if(sources.size()==MAX_SOURCES) {state="SOURCE_LIMIT";break;}
                sources.add(new Source(px,py,pz,emission));
            }
            blockers[cursor++]=(byte)((value&16)!=0?255:0);
        }
        if(cursor==CELLS) {
            sources.sort(BRIGHTEST_FIRST);
            state="READY";
        }
        return cursor-before;
    }
}
