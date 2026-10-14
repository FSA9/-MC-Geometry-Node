package com.mine.geometry_node.client.model.render.bvh;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.BitSet;
import java.util.Arrays;

/** Bounded render-thread snapshot; never requests chunk loads or scans the whole level. */
final class ModelPlacedLightVolume {
    static final int SIZE = 64, MAX_SOURCES = 16, TEXTURE_WIDTH = 512;
    static final int CELLS = SIZE * SIZE * SIZE;
    static final int COARSE_CELLS = 16 * 64 * 16;
    static final int MACRO_CELLS = 4 * 64 * 4;
    private static final Comparator<Source> BRIGHTEST_FIRST = Comparator.comparingInt(Source::emission).reversed();
    record Source(int x, int y, int z, int emission) {}
    /** -1 means unavailable; low four bits emission, bit 4 blocker, bits 8..14 optical material. */
    @FunctionalInterface interface Reader { int read(int x, int y, int z); }
    final int x, y, z;
    final byte[] blockers = new byte[CELLS];
    // Preserve individual height layers so ground does not occupy the air above it.
    // Low five bits count non-air voxels; bit 5 marks transparent material and
    // bit 6 marks sixteen opaque voxels, bit 7 marks a non-empty 16x1x16 parent.
    // Unknown voxels remain opaque.
    final byte[] coarseOccupancy = new byte[COARSE_CELLS];
    private final byte[] coarseTransparent = new byte[COARSE_CELLS];
    private final short[] macroOccupancy = new short[MACRO_CELLS];
    final List<Source> sources = new ArrayList<>();
    private int cursor;
    private String state = "SCANNING";
    private final boolean tolerantSources;
    private boolean sourceOverflow;
    private boolean sourceUnavailable;
    private final BitSet known;
    private int reused;
    private int shell, shellOffset;
    private boolean centerFirst;
    private int transparentCells;
    private boolean compactBounds;
    private final int[] occupiedMin={SIZE,SIZE,SIZE},occupiedMax={0,0,0};
    ModelPlacedLightVolume(int x, int y, int z) { this(x,y,z,false); }
    ModelPlacedLightVolume(int x, int y, int z, boolean tolerantSources) {
        this.x=x; this.y=y; this.z=z; this.tolerantSources=tolerantSources;
        known=tolerantSources?new BitSet(CELLS):null;
        centerFirst=tolerantSources;
        if(tolerantSources) {
            Arrays.fill(blockers,(byte)255);
            Arrays.fill(coarseOccupancy,(byte)(16|64|128));
            Arrays.fill(macroOccupancy,(short)256);
        }
    }
    boolean tolerantSources() {return tolerantSources;}
    boolean sourceOverflow() {return sourceOverflow;}
    boolean sourceUnavailable() {return sourceUnavailable;}
    int reusedCells() {return reused;}
    int transparentCells() {return transparentCells;}
    int emptyBricks() {
        int count=0;
        for(byte occupied:coarseOccupancy)if((occupied&31)==0)count++;
        return count;
    }
    int emptyParentBricks() {
        int count=0;
        for(short occupied:macroOccupancy)if(occupied==0)count++;
        return count;
    }
    int occupiedMin(int axis) {return compactBounds?occupiedMin[axis]:0;}
    int occupiedMax(int axis) {return compactBounds?occupiedMax[axis]:SIZE;}
    void invalidateChunk(int cx,int cz) {
        if(!tolerantSources)return;
        int fromX=Math.max(x,cx*16),toX=Math.min(x+SIZE,cx*16+16);
        int fromZ=Math.max(z,cz*16),toZ=Math.min(z+SIZE,cz*16+16);
        if(toX<=fromX||toZ<=fromZ)return;
        centerFirst=false;
        compactBounds=false;
        sources.removeIf(source -> source.x()>=fromX&&source.x()<toX&&source.z()>=fromZ&&source.z()<toZ);
        for(int pz=fromZ;pz<toZ;pz++)for(int py=0;py<SIZE;py++) {
            int start=fromX-x+SIZE*(py+SIZE*(pz-z)),end=start+toX-fromX;
            known.clear(start,end);
            for(int index=start;index<end;index++)setCell(index,(byte)255);
        }
        cursor=known.nextClearBit(0);reused=known.cardinality();state="SCANNING";
    }
    /** Unknown cells remain opaque; reuse only coordinates that were actually sampled. */
    int copyOverlap(ModelPlacedLightVolume previous) {
        if(!tolerantSources||previous==null||!previous.tolerantSources||previous==this)return 0;
        int minX=Math.max(x,previous.x),minY=Math.max(y,previous.y),minZ=Math.max(z,previous.z);
        int maxX=Math.min(x+SIZE,previous.x+SIZE),maxY=Math.min(y+SIZE,previous.y+SIZE),maxZ=Math.min(z+SIZE,previous.z+SIZE);
        int copied=0;
        for(int pz=minZ;pz<maxZ;pz++)for(int py=minY;py<maxY;py++) {
            if(maxX<=minX)continue;
            int target=(minX-x)+SIZE*((py-y)+SIZE*(pz-z));
            int source=(minX-previous.x)+SIZE*((py-previous.y)+SIZE*(pz-previous.z));
            int length=maxX-minX;
            if(previous.ready()) {
                for(int j=0;j<length;j++)setCell(target+j,previous.blockers[source+j]);
                known.set(target,target+length);copied+=length;
            } else for(int j=previous.known.nextSetBit(source);j>=source&&j<source+length;) {
                int end=Math.min(source+length,previous.known.nextClearBit(j));
                int index=target+j-source;
                for(int k=j;k<end;k++)setCell(index+k-j,previous.blockers[k]);
                known.set(index,index+end-j);copied+=end-j;
                j=previous.known.nextSetBit(end);
            }
        }
        reused=known.cardinality();cursor=known.nextClearBit(0);centerFirst=false;
        if(copied>0) {
            sourceUnavailable|=previous.sourceUnavailable;
            if(previous.sourceOverflow) {sourceOverflow=true;sources.clear();}
            else if(!sourceOverflow)for(Source source:previous.sources)
                if(contains(source.x(),source.y(),source.z())&&!sources.contains(source))addSource(source);
        }
        sources.sort(BRIGHTEST_FIRST);
        if(cursor>=CELLS)finishScan();
        return copied;
    }
    boolean contains(int px, int py, int pz) {
        return (long)px-x>=0 && (long)px-x<SIZE && (long)py-y>=0 && (long)py-y<SIZE && (long)pz-z>=0 && (long)pz-z<SIZE;
    }
    boolean ready() { return state.equals("READY"); }
    String state() { return state; }
    int scannedCells() { return tolerantSources?known.cardinality():cursor; }
    /** A block update patches a finished snapshot in O(source cap), without rescanning its volume. */
    boolean patch(int px,int py,int pz,int value) {
        if((!ready()&&!tolerantSources) || !contains(px,py,pz) || value<0) return false;
        sources.removeIf(source -> source.x()==px && source.y()==py && source.z()==pz);
        setCell((px-x)+SIZE*((py-y)+SIZE*(pz-z)),com.mine.geometry_node.client.runtime.light.NodeLightWorldMaterial.cell(value));
        if(tolerantSources)known.set((px-x)+SIZE*((py-y)+SIZE*(pz-z)));
        int emission=value&15;
        if(emission>0) {
            if(tolerantSources) {addSource(new Source(px,py,pz,emission));sources.sort(BRIGHTEST_FIRST);return true;}
            if(sources.size()==MAX_SOURCES) {state="SOURCE_LIMIT";return false;}
            sources.add(new Source(px,py,pz,emission));
            sources.sort(BRIGHTEST_FIRST);
        }
        return true;
    }
    int step(Reader reader, int maxCells, long maxNanos) {
        if (maxCells <= 0 || maxNanos <= 0) throw new IllegalArgumentException("positive scan budget required");
        if (!state.equals("SCANNING")) return 0;
        if(tolerantSources)return stepBlockers(reader,maxCells,maxNanos);
        long start=System.nanoTime(); int before=cursor;
        while(cursor<CELLS && cursor-before<maxCells) {
            if (((cursor-before)&63)==0 && System.nanoTime()-start>=maxNanos) break;
            int px=x+cursor%SIZE, py=y+(cursor/SIZE)%SIZE, pz=z+cursor/(SIZE*SIZE);
            int value=reader.read(px,py,pz);
            if(value<0)sourceUnavailable=true;
            if(value<0) {
                state="UNLOADED_REGION";break;
            }
            int emission=value&15;
            if(emission>0) {
                if(sources.size()==MAX_SOURCES) {state="SOURCE_LIMIT";break;}
                sources.add(new Source(px,py,pz,emission));
            }
            setCell(cursor++,com.mine.geometry_node.client.runtime.light.NodeLightWorldMaterial.cell(value));
        }
        if(cursor==CELLS) {
            sources.sort(BRIGHTEST_FIRST);
            finishScan();
        }
        return cursor-before;
    }
    private int stepBlockers(Reader reader,int maxCells,long maxNanos) {
        long started=System.nanoTime();int sampled=0,visited=0;
        while(sampled<maxCells) {
            if((visited++&63)==0&&System.nanoTime()-started>=maxNanos)break;
            int index=centerFirst?nextCentralCell():known.nextClearBit(cursor);
            if(index>=CELLS) {finishScan();break;}
            if(index<0||known.get(index))continue;
            int px=x+index%SIZE,py=y+(index/SIZE)%SIZE,pz=z+index/(SIZE*SIZE);
            int value=reader.read(px,py,pz);
            if(value<0)sourceUnavailable=true;
            if(value>=0&&(value&15)>0)addSource(new Source(px,py,pz,value&15));
            setCell(index,com.mine.geometry_node.client.runtime.light.NodeLightWorldMaterial.cell(value));
            known.set(index);sampled++;
            if(!centerFirst)cursor=index+1;
        }
        sources.sort(BRIGHTEST_FIRST);
        if(known.cardinality()==CELLS)finishScan();
        return sampled;
    }
    private void setCell(int index,byte value) {
        int previous=Byte.toUnsignedInt(blockers[index]),next=Byte.toUnsignedInt(value);
        boolean previousTransparent=previous>0&&previous<128,nextTransparent=next>0&&next<128;
        if(previousTransparent)transparentCells--;
        if(nextTransparent)transparentCells++;
        if((previous==0)!=(next==0)||previousTransparent!=nextTransparent) {
            int brick=(index&63)/4+16*(index>>6&63)+1024*((index>>12)/4);
            int occupancyDelta=(next!=0?1:0)-(previous!=0?1:0);
            int occupied=(coarseOccupancy[brick]&31)+occupancyDelta;
            int transparent=coarseTransparent[brick]+(nextTransparent?1:0)-(previousTransparent?1:0);
            if(occupancyDelta!=0) {
                int parent=(index&63)/16+4*(index>>6&63)+256*((index>>12)/16);
                int before=macroOccupancy[parent],after=before+occupancyDelta;
                macroOccupancy[parent]=(short)after;
                if((before==0)!=(after==0)) {
                    int first=brick&~(3|(3<<10)),flag=after>0?128:0;
                    for(int bz=0;bz<4;bz++)for(int bx=0;bx<4;bx++) {
                        int child=first+bx+bz*1024;
                        coarseOccupancy[child]=(byte)((coarseOccupancy[child]&127)|flag);
                    }
                }
            }
            coarseTransparent[brick]=(byte)transparent;
            coarseOccupancy[brick]=(byte)((coarseOccupancy[brick]&128)|occupied
                    |(transparent>0?32:0)|(occupied==16&&transparent==0?64:0));
        }
        blockers[index]=value;
        if(next!=0)expandBounds(index);
    }
    private void expandBounds(int index) {
        int px=index%SIZE,py=(index/SIZE)%SIZE,pz=index/(SIZE*SIZE);
        occupiedMin[0]=Math.min(occupiedMin[0],px);occupiedMax[0]=Math.max(occupiedMax[0],px+1);
        occupiedMin[1]=Math.min(occupiedMin[1],py);occupiedMax[1]=Math.max(occupiedMax[1],py+1);
        occupiedMin[2]=Math.min(occupiedMin[2],pz);occupiedMax[2]=Math.max(occupiedMax[2],pz+1);
    }
    private void finishScan() {
        state="READY";
        // Every known cell passed through setCell, including unavailable 255 cells.
        // Publishing the accumulated bound avoids another full-volume scan here.
        compactBounds=true;
    }
    /** Visit expanding cube surfaces so a new light gets nearby blockers before remote tile corners. */
    private int nextCentralCell() {
        if(shell==0) {shell=1;return 32+SIZE*(32+SIZE*32);}
        if(shell>32)return CELLS;
        int side=2*shell+1,inner=side-2,offset=shellOffset++;
        int face=side*side,a,b,c;
        if(offset<2*face) {
            c=offset/face==0?-shell:shell;offset%=face;
            a=offset%side-shell;b=offset/side-shell;
        } else {
            offset-=2*face;face=side*inner;
            if(offset<2*face) {
                b=offset/face==0?-shell:shell;offset%=face;
                a=offset%side-shell;c=offset/side-shell+1;
            } else {
                offset-=2*face;face=inner*inner;
                a=offset/face==0?-shell:shell;offset%=face;
                b=offset%inner-shell+1;c=offset/inner-shell+1;
            }
        }
        int px=a+32,py=b+32,pz=c+32;
        if(shellOffset==24*shell*shell+2) {shell++;shellOffset=0;}
        if(px<0||px>=SIZE||py<0||py>=SIZE||pz<0||pz>=SIZE)return -1;
        return px+SIZE*(py+SIZE*pz);
    }
    private void addSource(Source source) {
        if(sourceOverflow)return;
        if(sources.size()==MAX_SOURCES) {sourceOverflow=true;sources.clear();return;}
        sources.add(source);
    }
}
