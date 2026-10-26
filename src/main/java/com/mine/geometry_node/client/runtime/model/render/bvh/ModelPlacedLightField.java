package com.mine.geometry_node.client.runtime.model.render.bvh;

import com.mine.geometry_node.client.runtime.model.render.geometry.ModelRenderAsset;
import com.mine.geometry_node.client.runtime.model.render.geometry.ModelRenderInstance;
import com.mine.geometry_node.client.runtime.model.render.resource.ModelIrisUploadBudget;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.*;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/** Local placed-light provider. Independent from static model geometry and its upload lifetime. */
public final class ModelPlacedLightField {
    private static final int MAX_FIELDS= ModelLightFieldLayout.MAX_FIELDS;
    private static final int SNAPSHOT_BYTES=ModelPlacedLightVolume.CELLS+ModelPlacedLightVolume.CELLS/8
            +2*ModelPlacedLightVolume.COARSE_CELLS+Short.BYTES*ModelPlacedLightVolume.MACRO_CELLS;
    private static final class Field {
        final ModelLightFieldLayout.Tile tile;
        ModelPlacedLightVolume volume;
        boolean dirty,uploaded;
        long nextRetry;
        Field(ModelLightFieldLayout.Tile tile) {this.tile=tile;reset();}
        void reset() {volume=new ModelPlacedLightVolume(tile.x()-32,tile.y()-32,tile.z()-32,lightTiles.contains(tile));dirty=false;uploaded=false;nextRetry=System.nanoTime()+1_000_000_000L;scans++;}
    }
    private static ClientLevel level;
    private static final Field[] fields=new Field[MAX_FIELDS];
    private static int texture,scanCursor;
    private static boolean coarseAtlas;
    private static long snapshotCpuBytes,gpuBytes,retiredBytes,scans,scannedCells,updateNanos,peakUpdateNanos,uploadedBytes,patchedBlocks;
    private static String failure="";
    private static final BlockPos.MutableBlockPos POS=new BlockPos.MutableBlockPos();
    private static final float[] sources=new float[MAX_FIELDS*ModelPlacedLightVolume.MAX_SOURCES*4];
    private static final float[] origins=new float[MAX_FIELDS*4];
    private static final float[] occupiedMin=new float[MAX_FIELDS*4],occupiedMax=new float[MAX_FIELDS*4];
    private static final float[] revisionOrigins=new float[MAX_FIELDS*4],revisionMin=new float[MAX_FIELDS*4],revisionMax=new float[MAX_FIELDS*4];
    private static long contentRevision,revisionUploaded=-1;
    private static int revisionTexture=-1;
    private static final int[] sourceRanges=new int[MAX_FIELDS*2];
    private static java.util.List<ModelLightFieldLayout.Tile> desired=java.util.List.of();
    private static int instanceCount;
    private record Anchor(String id, Object asset, int scene, Object transform) {}
    private static java.util.List<Anchor> anchors=java.util.List.of();
    private static java.util.List<ModelLightFieldLayout.Tile> lightTiles=java.util.List.of();
    private static long eyeTileX=Long.MIN_VALUE,eyeTileY=Long.MIN_VALUE,eyeTileZ=Long.MIN_VALUE;
    private static boolean layoutChanged(java.util.List<ModelRenderInstance> instances,Vec3 eye) {
        var nextLights=com.mine.geometry_node.client.runtime.light.render.NodeLightRenderer.lights().stream()
                .map(l->{
                    double margin=Math.max(0,31-l.definition().range()-l.definition().emitterRadius());
                    for(var old:lightTiles) if(Math.abs(old.x()-l.position().x)<=margin
                            &&Math.abs(old.y()-l.position().y)<=margin&&Math.abs(old.z()-l.position().z)<=margin)return old;
                    return new ModelLightFieldLayout.Tile((int)Math.floor(l.position().x), (int)Math.floor(l.position().y), (int)Math.floor(l.position().z));
                }).distinct().toList();
        boolean changedLights=!nextLights.equals(lightTiles);lightTiles=nextLights;
        long x=Math.round(eye.x/28),y=Math.round(eye.y/28),z=Math.round(eye.z/28);
        boolean same=anchors.size()==instances.size();
        if(same)for(int i=0;i<anchors.size();i++) {
            Anchor a=anchors.get(i);var b=instances.get(i);
            if(!a.id.equals(b.id())||a.asset!=b.asset()||a.scene!=b.scene()||!a.transform.equals(b.transform())) {same=false;break;}
        }
        if(!changedLights&&same&&x==eyeTileX&&y==eyeTileY&&z==eyeTileZ)return false;
        eyeTileX=x;eyeTileY=y;eyeTileZ=z;
        anchors=instances.stream().map(i->new Anchor(i.id(),i.asset(),i.scene(),i.transform())).toList();
        return true;
    }
    private static final long SCAN_NANOS=500_000;
    private static final int SCAN_CELLS=4096;

    public static boolean watching(Object world,BlockPos pos) {
        if(world!=level)return false;
        for(Field f:fields)if(f!=null&&f.volume.contains(pos.getX(),pos.getY(),pos.getZ()))return true;
        return false;
    }
    public static void blockChanged(Object world,BlockPos pos,int value) {
        if(world!=level)return;
        for(Field f:fields)if(f!=null&&f.volume.contains(pos.getX(),pos.getY(),pos.getZ())) {
            if(f.volume.patch(pos.getX(),pos.getY(),pos.getZ(),value))patchedBlocks++;else f.dirty=true;
            f.uploaded=false;
        }
    }
    public static void chunkChanged(Object world,int cx,int cz) {
        if(world!=level)return;
        for(Field f:fields)if(f!=null&&((long)cx*16+15)>=f.volume.x&&(long)cx*16<f.volume.x+64
                &&((long)cz*16+15)>=f.volume.z&&(long)cz*16<f.volume.z+64) {
            if(f.volume.tolerantSources())f.volume.invalidateChunk(cx,cz);else f.dirty=true;
            f.uploaded=false;
        }
    }
    public static int texture() {return texture;}
    /** Exact shader-visible blocker state, including unavailable/retired tiles without a new upload. */
    public static long contentRevision() {
        if(revisionUploaded!=uploadedBytes || revisionTexture!=texture
                || !java.util.Arrays.equals(revisionOrigins,origins)
                || !java.util.Arrays.equals(revisionMin,occupiedMin)
                || !java.util.Arrays.equals(revisionMax,occupiedMax)) {
            revisionUploaded=uploadedBytes;revisionTexture=texture;contentRevision++;
            System.arraycopy(origins,0,revisionOrigins,0,origins.length);
            System.arraycopy(occupiedMin,0,revisionMin,0,occupiedMin.length);
            System.arraycopy(occupiedMax,0,revisionMax,0,occupiedMax.length);
        }
        return contentRevision;
    }
    static void update(Vec3 eye,java.util.List<ModelRenderInstance> instances,boolean enabled) {
        ClientLevel next=Minecraft.getInstance().level;
        if(!enabled||next==null) {if(level!=null||texture!=0)clear();return;}
        long started=System.nanoTime();
        try {
            if(level!=next) {clear();level=next;}
            if(layoutChanged(instances,eye)) {
            java.util.List<ModelLightFieldLayout.Bounds> bounds=new java.util.ArrayList<>();
            for(var instance:instances) {
                float[] t=instance.asset().lightOcclusion(instance.scene()).texels();
                if(t.length<8)continue;
                var tr=instance.transform();var matrix=tr.relativeTo(tr.x(),tr.y(),tr.z());
                double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
                for(int c=0;c<8;c++) {
                    Vector3f v=matrix.transformPosition(new Vector3f(t[(c&1)==0?0:4],t[(c&2)==0?1:5],t[(c&4)==0?2:6]));
                    minX=Math.min(minX,tr.x()+v.x);maxX=Math.max(maxX,tr.x()+v.x);
                    minY=Math.min(minY,tr.y()+v.y);maxY=Math.max(maxY,tr.y()+v.y);
                    minZ=Math.min(minZ,tr.z()+v.z);maxZ=Math.max(maxZ,tr.z()+v.z);
                }
                bounds.add(new ModelLightFieldLayout.Bounds(minX,minY,minZ,maxX,maxY,maxZ));
            }
            instanceCount=bounds.size();
            var tiles=new java.util.LinkedHashSet<ModelLightFieldLayout.Tile>(lightTiles);
            tiles.addAll(ModelLightFieldLayout.select(bounds,eye.x,eye.y,eye.z));
            desired=tiles.stream().limit(MAX_FIELDS).toList();
            }
            // Retain retiring snapshots until replacements have copied their shared world cells.
            var retiring=new java.util.ArrayList<Field>();
            for(int i=0;i<MAX_FIELDS;i++)if(fields[i]!=null&&!desired.contains(fields[i].tile)) {
                retiring.add(fields[i]);fields[i]=null;
            }
            try {
            for(var tile:desired) {
                boolean exists=false;for(Field f:fields)if(f!=null&&f.tile.equals(tile)) {
                    if(f.volume.tolerantSources()!=lightTiles.contains(tile))f.reset();
                    exists=true;
                }
                if(exists)continue;
                if(!ModelRenderAsset.residency().reserveTransientCpu(SNAPSHOT_BYTES)) {failure="placed-light CPU snapshot budget exhausted";break;}
                try {
                    for(int i=0;i<MAX_FIELDS;i++)if(fields[i]==null) {
                        Field replacement=new Field(tile);
                        if(replacement.volume.tolerantSources()) {
                            Field nearest=null;long distance=Long.MAX_VALUE;
                            for(Field old:retiring)if(!old.dirty&&old.volume.tolerantSources()) {
                                long d=Math.abs((long)old.tile.x()-tile.x())+Math.abs((long)old.tile.y()-tile.y())+Math.abs((long)old.tile.z()-tile.z());
                                if(d<distance){distance=d;nearest=old;}
                            }
                            if(nearest!=null)replacement.volume.copyOverlap(nearest.volume);
                        }
                        fields[i]=replacement;snapshotCpuBytes+=SNAPSHOT_BYTES;break;
                    }
                } catch(RuntimeException|Error e) {
                    ModelRenderAsset.residency().releaseTransientCpu(SNAPSHOT_BYTES);throw e;
                }
            }
            } finally {
                ModelRenderAsset.residency().releaseTransientCpu((long)retiring.size()*SNAPSHOT_BYTES);
                snapshotCpuBytes-=(long)retiring.size()*SNAPSHOT_BYTES;
            }
            long scanStart=System.nanoTime();int remaining=SCAN_CELLS;
            for(int attempt=0;attempt<MAX_FIELDS&&remaining>0;attempt++) {
                int slot=scanCursor;scanCursor=(scanCursor+1)%MAX_FIELDS;Field f=fields[slot];if(f==null)continue;
                if(f.dirty||(f.volume.state().equals("UNLOADED_REGION")&&started>=f.nextRetry))f.reset();
                long time=SCAN_NANOS-(System.nanoTime()-scanStart);if(time<=0)break;
                int n=f.volume.step((bx,by,bz)->{
                    if(!level.hasChunk(bx>>4,bz>>4))return -1;
                    POS.set(bx,by,bz);var state=level.getBlockState(POS);
                    return com.mine.geometry_node.client.runtime.light.material.NodeLightWorldMaterial.encode(state,level,POS);
                },remaining,time);
                scannedCells+=n;remaining-=n;
                if(n>0&&f.volume.tolerantSources())f.uploaded=false;
            }
            for(int i=0;i<MAX_FIELDS;i++) {
                Field f=fields[i];int base=i*4;origins[base+3]=0;sourceRanges[i*2]=i*ModelPlacedLightVolume.MAX_SOURCES;sourceRanges[i*2+1]=0;
                occupiedMax[base+3]=0;
                if(f==null)continue;
                boolean usable=f.volume.ready()||(f.volume.tolerantSources()&&f.volume.scannedCells()>0);
                if(usable&&!f.uploaded)upload(f,i);
                origins[base]=(float)(f.volume.x-eye.x);origins[base+1]=(float)(f.volume.y-eye.y);origins[base+2]=(float)(f.volume.z-eye.z);
                if(!f.uploaded||!usable)continue;
                origins[base+3]=1;
                for(int axis=0;axis<3;axis++) {
                    occupiedMin[base+axis]=f.volume.occupiedMin(axis);
                    occupiedMax[base+axis]=f.volume.occupiedMax(axis);
                }
                occupiedMin[base+3]=f.volume.transparentCells()>0?1:0;
                occupiedMax[base+3]=coarseAtlas?4:1;
                if(!f.volume.ready()||f.volume.sourceOverflow()||f.volume.sourceUnavailable())continue;
                int sourceCount=Math.min(f.volume.sources.size(),ModelPlacedLightVolume.MAX_SOURCES);
                sourceRanges[i*2+1]=sourceCount;
                for(int j=0;j<sourceCount;j++) {
                    var s=f.volume.sources.get(j);int p=(i*ModelPlacedLightVolume.MAX_SOURCES+j)*4;
                    sources[p]=(float)(s.x()+.5-eye.x);sources[p+1]=(float)(s.y()+.5-eye.y);sources[p+2]=(float)(s.z()+.5-eye.z);sources[p+3]=s.emission();
                }
            }
        }catch(RuntimeException e) {failure=e.toString();java.util.Arrays.fill(origins,0);}
        finally {updateNanos=System.nanoTime()-started;peakUpdateNanos=Math.max(peakUpdateNanos,updateNanos);}
    }
    private static void upload(Field field, int slot) {
        boolean allocate=texture==0;
        boolean withCoarse=allocate?GL11C.glGetInteger(GL11C.GL_MAX_TEXTURE_SIZE)>=544*MAX_FIELDS:coarseAtlas;
        int bytes=ModelPlacedLightVolume.CELLS+(withCoarse?ModelPlacedLightVolume.COARSE_CELLS:0);
        long allocationBytes=(long)bytes*MAX_FIELDS;
        if(!ModelIrisUploadBudget.INSTANCE.tryAcquire(bytes))return;
        long started=System.nanoTime();
        if(gpuBytes==0 && !ModelRenderAsset.residency().reserveTransientGpu(allocationBytes)) {
            failure="placed-light GPU residency budget exhausted";return;
        }
        gpuBytes=allocationBytes;
        int binding=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D),pbo=GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int row=GL11C.glGetInteger(GL12C.GL_UNPACK_ROW_LENGTH),skipX=GL11C.glGetInteger(GL11C.GL_UNPACK_SKIP_PIXELS),skipY=GL11C.glGetInteger(GL11C.GL_UNPACK_SKIP_ROWS);
        ByteBuffer data=MemoryUtil.memAlloc(bytes);
        try {
            data.put(field.volume.blockers);
            if(withCoarse)data.put(field.volume.coarseOccupancy);
            data.flip();
            if(texture==0) {gpuBytes=allocationBytes;texture=GL11C.glGenTextures();}
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,texture);GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER,0);
            GL11C.glPixelStorei(GL12C.GL_UNPACK_ROW_LENGTH,0);GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS,0);GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS,0);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MIN_FILTER,GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MAG_FILTER,GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL12C.GL_TEXTURE_MAX_LEVEL,0);
            int atlasHeight=(withCoarse?544:512)*MAX_FIELDS;
            if(allocate) GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D,0,GL30C.GL_R8,512,atlasHeight,0,GL11C.GL_RED,GL11C.GL_UNSIGNED_BYTE,0L);
            if(GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D,0,GL11C.GL_TEXTURE_WIDTH)!=512 || GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D,0,GL11C.GL_TEXTURE_HEIGHT)!=atlasHeight)
                throw new IllegalStateException("placed-light texture allocation failed");
            GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D,0,0,slot*512,512,512,GL11C.GL_RED,GL11C.GL_UNSIGNED_BYTE,data);
            if(withCoarse) {
                data.position(ModelPlacedLightVolume.CELLS);
                GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D,0,0,512*MAX_FIELDS+slot*32,512,32,GL11C.GL_RED,GL11C.GL_UNSIGNED_BYTE,data);
            }
            coarseAtlas=withCoarse;
            field.uploaded=true;uploadedBytes+=bytes;failure="";
        } catch(RuntimeException e) {
            // A failed first allocation must retry allocation, not repeatedly update undefined storage.
            if(allocate&&texture!=0) {GlStateManager._deleteTexture(texture);texture=0;}
            throw e;
        } finally {
            MemoryUtil.memFree(data);GL11C.glPixelStorei(GL12C.GL_UNPACK_ROW_LENGTH,row);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS,skipX);GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS,skipY);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER,pbo);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,binding);
            ModelIrisUploadBudget.INSTANCE.recordElapsed(System.nanoTime()-started);
        }
    }
    static boolean ready() {for(int i=0;i<MAX_FIELDS;i++)if(origins[i*4+3]>0)return true;return false;}
    static int fieldCount() {return MAX_FIELDS;}
    static int sourceCount() {int count=0;for(int i=0;i<MAX_FIELDS;i++)count+=sourceRanges[i*2+1];return count;}
    static float[] sourceUniforms() {return sources;}
    static float[] originUniforms() {return origins;}
    static float[] occupiedMinUniforms() {return occupiedMin;}
    static float[] occupiedMaxUniforms() {return occupiedMax;}
    static int[] rangeUniforms() {return sourceRanges;}
    static void clear() {
        int old=texture;long bytes=gpuBytes;texture=0;gpuBytes=0;coarseAtlas=false;
        if(old!=0||bytes!=0) {retiredBytes+=bytes;RenderSystem.queueFencedTask(()->{
            if(old!=0)GlStateManager._deleteTexture(old);ModelRenderAsset.residency().releaseTransientGpu(bytes);retiredBytes-=bytes;
        });}
        java.util.Arrays.fill(fields,null);java.util.Arrays.fill(origins,0);java.util.Arrays.fill(sourceRanges,0);
        java.util.Arrays.fill(occupiedMin,0);java.util.Arrays.fill(occupiedMax,0);
        if(snapshotCpuBytes!=0) {ModelRenderAsset.residency().releaseTransientCpu(snapshotCpuBytes);snapshotCpuBytes=0;}
        desired=java.util.List.of();anchors=java.util.List.of();lightTiles=java.util.List.of();eyeTileX=eyeTileY=eyeTileZ=Long.MIN_VALUE;level=null;failure="";updateNanos=0;instanceCount=0;
    }
    static Map<String,Object> report() {
        var out=new LinkedHashMap<String,Object>();var regions=new java.util.ArrayList<Map<String,Object>>();
        int pending=0,unavailable=0;
        for(Field f:fields)if(f!=null) {
            if(!f.uploaded||!f.volume.ready())pending++;
            if(f.volume.state().equals("SOURCE_LIMIT")||f.volume.state().equals("UNLOADED_REGION"))unavailable++;
            var region=new LinkedHashMap<String,Object>(Map.of("center",java.util.List.of(f.tile.x(),f.tile.y(),f.tile.z()),
                    "status",f.uploaded?(f.volume.ready()?"READY_UNVERIFIED":"PARTIAL_CONSERVATIVE"):f.volume.state(),
                    "sources",f.volume.sources.size(),"sourceOverflow",f.volume.sourceOverflow(),"sourceUnavailable",f.volume.sourceUnavailable(),
                    "scanProgress",f.volume.scannedCells(),"reusedCells",f.volume.reusedCells(),
                    "transparentCells",f.volume.transparentCells(),
                    "cpuEmptyBricks",f.volume.emptyBricks(),
                    "occupiedBounds",java.util.List.of(f.volume.occupiedMin(0),f.volume.occupiedMin(1),f.volume.occupiedMin(2),
                            f.volume.occupiedMax(0),f.volume.occupiedMax(1),f.volume.occupiedMax(2))));
            region.put("cpuEmptyParentBricks",f.volume.emptyParentBricks());
            regions.add(region);
        }
        out.put("status",!failure.isEmpty()?"DEGRADED":ready()?pending>0?"PARTIAL_UNVERIFIED":"READY_UNVERIFIED":regions.isEmpty()?"IDLE":unavailable>0?"UNAVAILABLE":"PREPARING");
        out.put("instances",instanceCount);out.put("regions",regions);out.put("sourcesIncludingOverlap",sourceCount());
        out.put("scans",scans);out.put("patchedBlocks",patchedBlocks);out.put("scannedCells",scannedCells);out.put("cpuBytes",snapshotCpuBytes);
        out.put("gpuBytes",gpuBytes);out.put("retiringGpuBytes",retiredBytes);out.put("uploadedBytes",uploadedBytes);
        out.put("coarseEmptyBrickSkipping",Map.of("enabled",texture!=0&&coarseAtlas,
                "brickSize",java.util.List.of(4,1,4),"bricksPerTile",ModelPlacedLightVolume.COARSE_CELLS,
                "parentBrickSize",java.util.List.of(16,1,16),
                "parentBricksPerTile",ModelPlacedLightVolume.MACRO_CELLS,
                "gpuBytes",texture!=0&&coarseAtlas?ModelPlacedLightVolume.COARSE_CELLS*MAX_FIELDS:0,
                "scope","Exact non-air counts, transparent-presence and full-opaque flags per 4x1x4 brick, plus 16x1x16 parent occupancy in the same byte. Empty parent bricks skip farther on the original DDA lattice. Unknown cells remain opaque. Camera rays skip bricks without transparent materials; light rays reject full-opaque bricks. Same texture/upload lifetime as fine voxels; unsupported texture extent retains fine DDA."));
        out.put("worldTraversal","occupied_bounds_and_coarse_voxel_dda");
        out.put("updateNanos",updateNanos);out.put("peakUpdateNanos",peakUpdateNanos);out.put("lastFailure",failure);
        out.put("scope","up to eight reusable world tiles across selected instances; 28-block tile spacing, 64-cube complete source snapshots; nearest-camera tiles prioritized");
        out.put("nodeLightTransmission","shared R8 voxel material palette; RGB Beer-Lambert glass/water; panes use 1/8 equivalent thickness; waterlogged absorption combined; full-cell fluid approximation; old model binary blockers unchanged");
        out.put("limits","16 sources per tile; global 4096 cells/0.5ms scan budget; outside ready tiles original pack light retained; radial linear light-level falloff (not inverse-square); full opaque vanilla voxel blockers; not infinite-world or indirect light propagation");
        return out;
    }
    private ModelPlacedLightField() {}
}
