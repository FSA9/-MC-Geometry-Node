package com.mine.geometry_node.client.runtime.light;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL41C;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Optional driver-specific cache. A missing/rejected binary always falls back to GLSL. */
final class NodeLightProgramCache {
    private static final int MAGIC=0x4e4c5031, MAX_BYTES=16*1024*1024;
    private static long hits, misses, loadNanos, saveNanos;
    private static volatile long saves;
    private static volatile int lastBinaryBytes;
    private static volatile String status="IDLE";
    private static String loadStatus="IDLE", loadFailure="";
    private static volatile String saveStatus="IDLE";
    private NodeLightProgramCache() {}

    static boolean supported() {
        var caps=GL.getCapabilities();
        return (caps.OpenGL41||caps.GL_ARB_get_program_binary)
                &&GL11C.glGetInteger(GL41C.GL_NUM_PROGRAM_BINARY_FORMATS)>0;
    }

    static Path path(String vertex,String fragment) {
        return path("native-volume-v2", vertex, fragment);
    }

    static Path path(String namespace, String... sources) {
        if(!supported()) {status="UNSUPPORTED";return null;}
        try {
            String key=namespace+"\n"+GL11C.glGetString(GL11C.GL_VENDOR)+"\n"
                    +GL11C.glGetString(GL11C.GL_RENDERER)+"\n"+GL11C.glGetString(GL11C.GL_VERSION)+"\n"
                    +GL11C.glGetString(GL20C.GL_SHADING_LANGUAGE_VERSION)+"\n";
            var hashState=MessageDigest.getInstance("SHA-256");
            hashState.update(key.getBytes(StandardCharsets.UTF_8));
            for(String source:sources) {
                hashState.update((source==null?"-1:":source.length()+":").getBytes(StandardCharsets.UTF_8));
                if(source!=null)hashState.update(source.getBytes(StandardCharsets.UTF_8));
            }
            String hash=HexFormat.of().formatHex(hashState.digest());
            return Minecraft.getInstance().gameDirectory.toPath().resolve("geometry_nodes/caches/light/shaders")
                    .resolve(namespace).resolve(hash+".bin");
        } catch(Exception error) {status="KEY_FAILED";return null;}
    }

    static int load(Path path) {
        loadFailure="";
        loadNanos=0;
        loadStatus=path==null?"UNAVAILABLE":"LOADING";
        if(path==null)return 0;
        long started=System.nanoTime();
        int program=0;
        try {
            if(!Files.isRegularFile(path)) {misses++;status=loadStatus="MISS";return 0;}
            long size=Files.size(path);
            if(size<44||size>MAX_BYTES+44L)throw new IllegalStateException("Invalid binary size");
            int format;
            byte[] binary,checksum;
            try(var input=new DataInputStream(Files.newInputStream(path))) {
                if(input.readInt()!=MAGIC)throw new IllegalStateException("Invalid binary header");
                format=input.readInt();int length=input.readInt();
                if(length<=0||length>MAX_BYTES||size!=length+44L)throw new IllegalStateException("Invalid binary length");
                checksum=input.readNBytes(32);binary=input.readNBytes(length);
                if(binary.length!=length||!MessageDigest.isEqual(checksum,digest(binary)))throw new IllegalStateException("Invalid binary checksum");
            }
            int[] formats=new int[GL11C.glGetInteger(GL41C.GL_NUM_PROGRAM_BINARY_FORMATS)];
            GL11C.glGetIntegerv(GL41C.GL_PROGRAM_BINARY_FORMATS,formats);
            boolean recognized=false;
            for(int supported:formats)recognized|=format==supported;
            if(!recognized)throw new IllegalStateException("Unsupported binary format");
            var buffer=MemoryUtil.memAlloc(binary.length);
            try {
                buffer.put(binary).flip();
                program=GL20C.glCreateProgram();
                GL41C.glProgramBinary(program,format,buffer);
                if(GL20C.glGetProgrami(program,GL20C.GL_LINK_STATUS)==0)
                    throw new IllegalStateException("Driver rejected binary: "+GL20C.glGetProgramInfoLog(program,1024).replace('\n',' ').replace('\r',' '));
            } finally {MemoryUtil.memFree(buffer);}
            hits++;status=loadStatus="HIT";return program;
        } catch(Exception error) {
            if(program!=0)GL20C.glDeleteProgram(program);
            misses++;status=loadStatus="REJECTED";
            loadFailure=error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage());
            return 0;
        } finally {loadNanos=System.nanoTime()-started;}
    }

    static void save(Path path,int program) {
        if(path==null)return;
        long started=System.nanoTime();
        try { extract(path,program); }
        catch(RuntimeException error) {saveStatus="EXTRACTION_FAILED";}
        finally {saveNanos=System.nanoTime()-started;}
    }

    private static void extract(Path path,int program) {
        saveStatus="EXTRACTING";
        int capacity=GL20C.glGetProgrami(program,GL41C.GL_PROGRAM_BINARY_LENGTH);
        lastBinaryBytes=capacity;
        if(capacity<=0) {saveStatus="NO_BINARY";return;}
        if(capacity>MAX_BYTES) {saveStatus="BINARY_TOO_LARGE";return;}
        var buffer=MemoryUtil.memAlloc(capacity);
        try(var stack=MemoryStack.stackPush()) {
            var length=stack.mallocInt(1);var format=stack.mallocInt(1);
            GL41C.glGetProgramBinary(program,length,format,buffer);
            int actual=length.get(0),binaryFormat=format.get(0);
            if(actual<=0||actual>capacity) {saveStatus="INVALID_BINARY_LENGTH";return;}
            byte[] bytes=new byte[actual];buffer.get(bytes);
            saveStatus="QUEUED";
            CompletableFuture.runAsync(()->write(path,binaryFormat,bytes));
        } catch(RuntimeException error) {saveStatus="EXTRACTION_FAILED";}
        finally {MemoryUtil.memFree(buffer);}
    }

    private static synchronized void write(Path path,int format,byte[] binary) {
        Path temporary=null;
        try {
            Files.createDirectories(path.getParent());
            temporary=Files.createTempFile(path.getParent(),"native-volume-",".tmp");
            try(var output=new DataOutputStream(Files.newOutputStream(temporary))) {
                output.writeInt(MAGIC);output.writeInt(format);output.writeInt(binary.length);
                output.write(digest(binary));output.write(binary);
            }
            try {Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException error) {Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
            saves++;
            saveStatus="SAVED";
            // Shader updates and driver changes should not accumulate binary files indefinitely.
            try(var entries=Files.list(path.getParent())) {
                var previous=entries.filter(p->!p.equals(path)&&p.getFileName().toString().matches("[0-9a-f]{64}\\.bin"))
                        .sorted(java.util.Comparator.comparingLong(NodeLightProgramCache::modified).reversed()).toList();
                long retained=binary.length+44L;
                for(int i=0;i<previous.size();i++) {
                    Path entry=previous.get(i);
                    retained+=Files.size(entry);
                    if(i>=23 || retained>128L*1024*1024)Files.deleteIfExists(entry);
                }
            }
        } catch(Exception error) {saveStatus="WRITE_FAILED";}
        finally {if(temporary!=null)try {Files.deleteIfExists(temporary);}catch(Exception ignored) {}}
    }

    private static long modified(Path path) {
        try {return Files.getLastModifiedTime(path).toMillis();}
        catch(java.io.IOException error) {return 0;}
    }

    private static byte[] digest(byte[] value) throws java.security.NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(value);
    }

    static String loadStatus() { return loadStatus; }
    static String loadFailure() { return loadFailure; }

    static Map<String,Object> report() {
        return Map.ofEntries(Map.entry("status",status),Map.entry("hits",hits),Map.entry("misses",misses),
                Map.entry("saved",saves),Map.entry("lastBinaryBytes",lastBinaryBytes),Map.entry("loadMs",loadNanos/1_000_000.0),
                Map.entry("loadStatus",loadStatus),Map.entry("loadFailure",loadFailure),
                Map.entry("saveStatus",saveStatus),Map.entry("binaryExtractionMs",saveNanos/1_000_000.0));
    }
}
