package com.mine.geometry_node.core.engine.system.model;
import com.mine.geometry_node.core.engine.system.model.io.*;
import com.mine.geometry_node.core.engine.system.model.asset.ModelLimits;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class GlbContainerReaderTest {
    @TempDir Path temp;
    private Path glb(String json,long total) throws Exception {
        byte[] text=json.getBytes(StandardCharsets.UTF_8);int padded=(text.length+3)&~3;
        if(total==0)total=28L+padded;
        ByteBuffer header=ByteBuffer.allocate(28+padded).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(0x46546c67).putInt(2).putInt((int)total).putInt(padded).putInt(0x4e4f534a).put(text);
        while(header.position()<20+padded)header.put((byte)' ');
        header.putInt((int)(total-28-padded)).putInt(0x004e4942).flip();
        Path p=temp.resolve("test.glb");
        try(FileChannel out=FileChannel.open(p,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)) {
            while(header.hasRemaining())out.write(header);
            if(total>out.position())out.write(ByteBuffer.wrap(new byte[]{0}),total-1);
        }
        return p;
    }
    @Test void reads512MiBContainerWithoutReadingBin() throws Exception {
        Path p=glb("{\"asset\":{\"version\":\"2.0\"}}",ModelLimits.MAX_PACKAGE_BYTES);
        try(var source=new FileModelSource(p,ModelLimits.DEFAULT)) {
            var c=new GlbContainerReader().read(source,ModelLimits.DEFAULT,new ModelCancellation());
            assertEquals(ModelLimits.MAX_PACKAGE_BYTES,c.encodedBytes());assertTrue(c.binary().isPresent());
            assertTrue(source.bytesRead()<1024);assertTrue(source.largestRead()<1024);
        }
    }
    @Test void checksDeclaredLengthAndChunkAlignment() throws Exception {
        Path p=glb("{\"asset\":{\"version\":\"2.0\"}}",0);
        try(FileChannel ch=FileChannel.open(p,StandardOpenOption.WRITE)){ch.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(100).flip(),8);}
        try(var s=new FileModelSource(p,ModelLimits.DEFAULT)){assertThrows(ModelProblem.class,()->new GlbContainerReader().read(s,ModelLimits.DEFAULT,new ModelCancellation()));}
    }
    @Test void cancelsBeforeAllocation() throws Exception {
        Path p=glb("{\"asset\":{\"version\":\"2.0\"}}",0);var token=new ModelCancellation();token.cancel();
        try(var s=new FileModelSource(p,ModelLimits.DEFAULT)){assertEquals(ModelProblem.Code.CANCELLED,
                assertThrows(ModelProblem.class,()->new GlbContainerReader().read(s,ModelLimits.DEFAULT,token)).code());assertEquals(0,s.bytesRead());}
    }
    @Test void rejectsTooDeepJsonBeforeTreeConstruction() throws Exception {
        Path p=glb("{\"asset\":{\"version\":\"2.0\"},\"x\":"+"[".repeat(130)+"0"+"]".repeat(130)+"}",0);
        try(var s=new FileModelSource(p,ModelLimits.DEFAULT)){assertThrows(ModelProblem.class,()->new GlbContainerReader().read(s,ModelLimits.DEFAULT,new ModelCancellation()));}
    }
    @Test void unknownRequiredCannotSilentlyFallback() throws Exception {
        Path p=glb("{\"asset\":{\"version\":\"2.0\"},\"extensionsUsed\":[\"EXT_test\"],\"extensionsRequired\":[\"EXT_test\"]}",0);
        try(var s=new FileModelSource(p,ModelLimits.DEFAULT)){
            var c=new GlbContainerReader().read(s,ModelLimits.DEFAULT,new ModelCancellation());
            assertEquals(ModelProblem.Code.UNSUPPORTED_REQUIRED_EXTENSION,assertThrows(ModelProblem.class,()->GlbContainerReader.requireExtensions(c.json(),Set.of())).code());
        }
    }
    @Test void sourceRangesAndSliceBudgetAreChecked() throws Exception {
        Path p=glb("{\"asset\":{\"version\":\"2.0\"}}",0);
        try(var s=new FileModelSource(p,new ModelLimits(1024,32,32,1024,4))){
            assertThrows(ModelProblem.class,()->s.read(Long.MAX_VALUE,10,new ModelCancellation()));
            assertThrows(ModelProblem.class,()->s.read(0,40,new ModelCancellation()));
        }
    }
    @Test void resolverHandlesEncodingButRejectsEscapeAndSchemes() throws Exception {
        Path root=Files.createDirectory(temp.resolve("root"));Path model=Files.writeString(root.resolve("a.gltf"),"{}");
        Path image=Files.writeString(root.resolve("image file.png"),"x");
        assertEquals(image.toRealPath(),ModelSourceResolver.resolve(root,model,"image%20file.png"));
        assertThrows(ModelProblem.class,()->ModelSourceResolver.resolve(root,model,"%2e%2e/escape.bin"));
        assertThrows(ModelProblem.class,()->ModelSourceResolver.resolve(root,model,"https://example.test/a"));
        assertThrows(ModelProblem.class,()->ModelSourceResolver.resolve(root,model,"C:/secret"));
    }
}
