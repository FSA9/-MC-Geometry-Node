package com.mine.geometry_node.core.engine.system.model.io;
import com.google.gson.*;
import com.mine.geometry_node.core.engine.system.model.asset.ModelLimits;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
/** CPU decoder over explicit buffer sources. Supports normalized integers, sparse and padded matrices.
 * Output is caller-owned; this class never resolves URIs or allocates GPU memory. */
public final class GltfAccessorReader {
    private final JsonObject document;
    private final List<ModelByteSource> buffers;
    private final ModelLimits limits;
    private final ModelCancellation cancellation;
    public GltfAccessorReader(JsonObject document,List<ModelByteSource> buffers,ModelLimits limits,ModelCancellation cancellation) {
        this.document=document; this.buffers=List.copyOf(buffers); this.limits=limits; this.cancellation=cancellation;
    }
    public double[] read(int accessorIndex) throws IOException {
        String location="/accessors/"+accessorIndex;
        cancellation.check();
        try {
            JsonObject a=object("accessors",accessorIndex);
            int count=integer(a,"count",-1), component=integer(a,"componentType",-1);
            if(count<1) throw invalid(location,"count must be positive");
            int width=width(component); String type=a.get("type").getAsString();
            int rows, columns;
            switch(type) {
                case "SCALAR" -> { rows=1; columns=1; }
                case "VEC2" -> { rows=2; columns=1; }
                case "VEC3" -> { rows=3; columns=1; }
                case "VEC4" -> { rows=4; columns=1; }
                case "MAT2" -> { rows=2; columns=2; }
                case "MAT3" -> { rows=3; columns=3; }
                case "MAT4" -> { rows=4; columns=4; }
                default -> throw invalid(location,"unknown accessor type");
            }
            int elements=rows*columns, columnStride=columns>1?align4(rows*width):rows*width;
            int elementBytes=columnStride*columns;
            long values=(long)count*elements;
            ModelLimits.requireWithin(values*Double.BYTES,limits.decodedAccessorBytes(),location);
            if(values>Integer.MAX_VALUE) throw invalid(location,"too many output values");
            boolean normalized=a.has("normalized") && a.get("normalized").getAsBoolean();
            if(normalized && (component==5126 || component==5125)) throw invalid(location,"normalized requires byte/short components");
            double[] out=new double[(int)values];
            if(a.has("bufferView")) {
                JsonObject view=object("bufferViews",a.get("bufferView").getAsInt());
                int stride=integer(view,"byteStride",elementBytes);
                if(stride<elementBytes || stride%width!=0 || (view.has("byteStride")&&(stride<4||stride>252||stride%4!=0)))
                    throw invalid(location,"invalid stride");
                long offset=number(a,"byteOffset",0);
                readElements(view,offset,count,stride,rows,columns,columnStride,width,component,normalized,out,null,location);
            } else if(number(a,"byteOffset",0)!=0) throw invalid(location,"offset without bufferView");
            if(a.has("sparse")) {
                JsonObject s=a.getAsJsonObject("sparse"); int n=integer(s,"count",-1);
                if(n<1||n>count) throw invalid(location,"invalid sparse count");
                JsonObject indices=s.getAsJsonObject("indices"), vals=s.getAsJsonObject("values");
                int it=integer(indices,"componentType",-1);
                if(it!=5121&&it!=5123&&it!=5125) throw invalid(location,"invalid sparse index type");
                int iw=width(it); JsonObject iv=object("bufferViews",indices.get("bufferView").getAsInt());
                if(iv.has("byteStride")) throw invalid(location,"sparse view cannot be interleaved");
                long io=number(indices,"byteOffset",0); range(iv,io,(long)n*iw,iw,location);
                ModelLimits.requireWithin(values*Double.BYTES+(long)n*Integer.BYTES,limits.decodedAccessorBytes(),location+"/sparse");
                int[] targets=new int[n]; long previous=-1;
                for(int i=0;i<n;i++) {
                    if((i&255)==0) cancellation.check();
                    ByteBuffer b=readView(iv,io+(long)i*iw,iw);
                    long v=(long)scalar(b,0,it,false);
                    if(v<=previous||v>=count) throw invalid(location,"sparse indices must be increasing and in range");
                    targets[i]=(int)v; previous=v;
                }
                JsonObject vv=object("bufferViews",vals.get("bufferView").getAsInt());
                if(vv.has("byteStride")) throw invalid(location,"sparse value view cannot be interleaved");
                readElements(vv,number(vals,"byteOffset",0),n,elementBytes,rows,columns,columnStride,width,component,normalized,out,targets,location);
            }
            return out;
        } catch(IllegalStateException | IndexOutOfBoundsException | NullPointerException | ArithmeticException e) {
            throw invalid(location,"malformed accessor: "+e.getMessage());
        }
    }
    private void readElements(JsonObject view,long offset,int count,int stride,int rows,int columns,int colStride,
                              int width,int component,boolean normalized,double[] out,int[] targets,String location) throws IOException {
        // Last column padding may be absent at the end of a view (glTF matrix packing).
        int elementBytes=colStride*(columns-1)+rows*width;
        range(view,offset,(long)(count-1)*stride+elementBytes,columns>1?4:width,location);
        // Small fixed window avoids a read/allocation per vertex and never copies a complete large buffer.
        int batch=Math.max(1,Math.min(64*1024,limits.readSliceBytes())/stride);
        for(int start=0;start<count;start+=batch) {
            cancellation.check(); int n=Math.min(batch,count-start);
            ByteBuffer b=readView(view,offset+(long)start*stride,(n-1)*stride+elementBytes);
            for(int i=0;i<n;i++) for(int c=0;c<columns;c++) for(int r=0;r<rows;r++) {
                double v=scalar(b,i*stride+c*colStride+r*width,component,normalized);
                if(!Double.isFinite(v)) throw invalid(location,"non-finite component");
                int target=targets==null?start+i:targets[start+i];
                out[target*rows*columns+c*rows+r]=v;
            }
        }
    }
    private void range(JsonObject view,long offset,long bytes,int alignment,String location) throws ModelProblem {
        long length=number(view,"byteLength",-1),base=number(view,"byteOffset",0);
        int buffer=integer(view,"buffer",-1);
        if(buffer<0||buffer>=buffers.size()||base<0||length<0||base>buffers.get(buffer).size()||length>buffers.get(buffer).size()-base
            || offset<0||offset>length||bytes>length-offset||offset%alignment!=0||(base+offset)%alignment!=0)
            throw invalid(location,"buffer view range or alignment invalid");
    }
    private ByteBuffer readView(JsonObject view,long offset,int length) throws IOException {
        return buffers.get(view.get("buffer").getAsInt()).read(number(view,"byteOffset",0)+offset,length,cancellation);
    }
    private JsonObject object(String key,int index) { return document.getAsJsonArray(key).get(index).getAsJsonObject(); }
    private static int integer(JsonObject o,String key,int fallback) { return Math.toIntExact(number(o,key,fallback)); }
    private static long number(JsonObject o,String key,long fallback) { return o.has(key)?o.get(key).getAsBigDecimal().longValueExact():fallback; }
    private static int align4(int n) { return (n+3)&~3; }
    private static int width(int component) throws ModelProblem {
        return switch(component) { case 5120,5121 -> 1; case 5122,5123 -> 2; case 5125,5126 -> 4; default -> throw invalid("componentType","unsupported component type"); };
    }
    private static double scalar(ByteBuffer b,int at,int type,boolean norm) {
        return switch(type) {
            case 5120 -> norm?Math.max(-1,b.get(at)/127.0):b.get(at);
            case 5121 -> norm?Byte.toUnsignedInt(b.get(at))/255.0:Byte.toUnsignedInt(b.get(at));
            case 5122 -> norm?Math.max(-1,b.getShort(at)/32767.0):b.getShort(at);
            case 5123 -> norm?Short.toUnsignedInt(b.getShort(at))/65535.0:Short.toUnsignedInt(b.getShort(at));
            case 5125 -> Integer.toUnsignedLong(b.getInt(at));
            case 5126 -> b.getFloat(at);
            default -> throw new IllegalArgumentException("component type");
        };
    }
    private static ModelProblem invalid(String location,String detail) { return new ModelProblem(ModelProblem.Code.INVALID_ACCESSOR,location,detail); }
}
