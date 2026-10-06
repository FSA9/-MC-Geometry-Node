package com.mine.geometry_node.core.engine.system.model;
import com.mine.geometry_node.core.engine.system.model.io.*;
import com.mine.geometry_node.core.engine.system.model.asset.ModelLimits;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GltfAccessorReaderTest {
    @Test void sparseIndicesUseBoundedBatchesAndValidateAcrossBatchBoundaries() throws Exception {
        int count=20_000;
        ByteBuffer bytes=ByteBuffer.allocate(count*8).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<count;i++)bytes.putInt(i);
        for(int i=0;i<count;i++)bytes.putFloat(i*.5f);
        JsonObject doc=JsonParser.parseString("""
                {"bufferViews":[{"buffer":0,"byteLength":80000},
                  {"buffer":0,"byteOffset":80000,"byteLength":80000}],
                 "accessors":[{"componentType":5126,"type":"SCALAR","count":20000,
                  "sparse":{"count":20000,"indices":{"bufferView":0,"componentType":5125},
                  "values":{"bufferView":1}}}]}
                """).getAsJsonObject();
        java.util.concurrent.atomic.AtomicInteger reads=new java.util.concurrent.atomic.AtomicInteger();
        ModelByteSource source=new ModelByteSource() {
            public long size(){return bytes.capacity();}
            public ByteBuffer read(long offset,int length,ModelCancellation token) throws java.io.IOException {
                token.check();assertTrue(length<=64*1024);reads.incrementAndGet();
                return ByteBuffer.wrap(bytes.array(),(int)offset,length).slice().order(ByteOrder.LITTLE_ENDIAN);
            }
            public void close(){}
        };
        GltfAccessorReader reader=new GltfAccessorReader(doc,List.of(source),ModelLimits.DEFAULT,new ModelCancellation());
        double[] result=reader.read(0);
        assertEquals(4,reads.get());
        for(int i=0;i<count;i++)assertEquals(i*.5,result[i]);

        bytes.putInt(64*1024,16383);
        assertThrows(ModelProblem.class,()->reader.read(0));
    }

    private static ModelByteSource source(byte[] data) {
        return new ModelByteSource() {
            public long size(){return data.length;}
            public ByteBuffer read(long offset,int length,ModelCancellation token) throws java.io.IOException {
                token.check();return ByteBuffer.wrap(data,(int)offset,length).slice().order(ByteOrder.LITTLE_ENDIAN);
            }
            public void close(){}
        };
    }
    private static double[] decode(String views,String accessor,byte[] bytes) throws Exception {
        JsonObject doc=new JsonObject(); doc.add("bufferViews",JsonParser.parseString(views));
        JsonArray array=new JsonArray();array.add(JsonParser.parseString(accessor));doc.add("accessors",array);
        return new GltfAccessorReader(doc,List.of(source(bytes)),ModelLimits.DEFAULT,new ModelCancellation()).read(0);
    }
    @Test void interleavedNormalizedBytes() throws Exception {
        assertArrayEquals(new double[]{-1,0,1,1,0,-1},decode("[{\"buffer\":0,\"byteLength\":8,\"byteStride\":4}]",
                "{\"bufferView\":0,\"componentType\":5120,\"normalized\":true,\"type\":\"VEC3\",\"count\":2}",
                new byte[]{-128,0,127,44,127,0,-128,55}));
    }
    @Test void matrixColumnsRespectFourBytePadding() throws Exception {
        assertArrayEquals(new double[]{1,2,3,4,5,6,7,8,9},decode("[{\"buffer\":0,\"byteLength\":12}]",
                "{\"bufferView\":0,\"componentType\":5121,\"type\":\"MAT3\",\"count\":1}",new byte[]{1,2,3,99,4,5,6,99,7,8,9,99}));
    }
    @Test void sparseWithoutBaseStartsAtZero() throws Exception {
        assertArrayEquals(new double[]{0,5,0,9},decode("[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":2},{\"buffer\":0,\"byteOffset\":2,\"byteLength\":2}]",
                "{\"componentType\":5121,\"type\":\"SCALAR\",\"count\":4,\"sparse\":{\"count\":2,\"indices\":{\"bufferView\":0,\"componentType\":5121},\"values\":{\"bufferView\":1}}}",
                new byte[]{1,3,5,9}));
    }
    @Test void sparseIndicesMustBeStrictlyIncreasing() {
        assertThrows(ModelProblem.class,()->decode("[{\"buffer\":0,\"byteLength\":2},{\"buffer\":0,\"byteOffset\":2,\"byteLength\":2}]",
                "{\"componentType\":5121,\"type\":\"SCALAR\",\"count\":4,\"sparse\":{\"count\":2,\"indices\":{\"bufferView\":0,\"componentType\":5121},\"values\":{\"bufferView\":1}}}",new byte[]{2,1,5,9}));
    }
    @Test void unsignedIndicesKeepAll32Bits() throws Exception {
        assertArrayEquals(new double[]{4294967295.0},decode("[{\"buffer\":0,\"byteLength\":4}]",
                "{\"bufferView\":0,\"componentType\":5125,\"type\":\"SCALAR\",\"count\":1}",new byte[]{-1,-1,-1,-1}));
    }
    @Test void rejectsOverflowAndOutOfBoundsBeforeSourceRead() {
        assertThrows(ModelProblem.class,()->decode("[{\"buffer\":0,\"byteLength\":4}]",
                "{\"bufferView\":0,\"byteOffset\":9223372036854775807,\"componentType\":5126,\"type\":\"VEC3\",\"count\":1}",new byte[4]));
        assertThrows(ModelProblem.class,()->decode("[{\"buffer\":0,\"byteLength\":4}]",
                "{\"bufferView\":0,\"componentType\":5126,\"type\":\"VEC3\",\"count\":2147483647}",new byte[4]));
    }
    @Test void rejectsNanAndMisalignment() {
        assertThrows(ModelProblem.class,()->decode("[{\"buffer\":0,\"byteLength\":4}]",
                "{\"bufferView\":0,\"componentType\":5126,\"type\":\"SCALAR\",\"count\":1}",ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(Float.NaN).array()));
        assertThrows(ModelProblem.class,()->decode("[{\"buffer\":0,\"byteLength\":4,\"byteOffset\":1}]",
                "{\"bufferView\":0,\"componentType\":5126,\"type\":\"SCALAR\",\"count\":1}",new byte[5]));
    }
    @Test void matrixFinalPaddingMayBeAbsentButColumnsMustAlign() throws Exception {
        assertArrayEquals(new double[]{1,2,3,4,5,6,7,8,9},decode("[{\"buffer\":0,\"byteLength\":11}]",
                "{\"bufferView\":0,\"componentType\":5121,\"type\":\"MAT3\",\"count\":1}",new byte[]{1,2,3,0,4,5,6,0,7,8,9}));
        assertThrows(ModelProblem.class,()->decode("[{\"buffer\":0,\"byteOffset\":1,\"byteLength\":11}]",
                "{\"bufferView\":0,\"componentType\":5121,\"type\":\"MAT3\",\"count\":1}",new byte[12]));
    }
}
