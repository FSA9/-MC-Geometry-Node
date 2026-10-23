package com.mine.geometry_node.core.engine.system.model.gltf;

import com.mine.geometry_node.core.engine.system.model.io.ModelByteSource;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import com.google.gson.*;
import com.google.gson.stream.*;
import com.mine.geometry_node.core.engine.system.model.asset.ModelLimits;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
/** Reads only bounded JSON and chunk headers. BIN remains range-backed, owned by the caller. */
public final class GlbContainerReader {
    private static final int MAGIC=0x46546c67, JSON=0x4e4f534a, BIN=0x004e4942;
    public record Chunk(long offset,long length) {}
    public record Container(JsonObject json, Optional<Chunk> binary, long encodedBytes) {}
    public Container read(ModelByteSource source,ModelLimits limits,ModelCancellation token) throws IOException {
        ModelLimits.requireWithin(source.size(),limits.packageBytes(),"GLB");
        if(source.size()<20) throw problem("header","truncated GLB");
        ByteBuffer h=source.read(0,12,token);
        if(h.getInt()!=MAGIC || h.getInt()!=2) throw problem("header","expected GLB version 2");
        if(Integer.toUnsignedLong(h.getInt())!=source.size()) throw problem("header/length","declared length differs from source");
        long offset=12; int count=0; JsonObject json=null; Chunk bin=null;
        while(offset<source.size()) {
            token.check();
            if(source.size()-offset<8) throw problem("chunks","truncated chunk header");
            ByteBuffer ch=source.read(offset,8,token); long length=Integer.toUnsignedLong(ch.getInt()); int type=ch.getInt();
            offset+=8;
            if((length&3)!=0 || length>source.size()-offset) throw problem("chunks","invalid aligned chunk range");
            if(count==0 && type!=JSON) throw problem("chunks/0","JSON must be first");
            if(type==JSON) {
                if(json!=null) throw problem("chunks","duplicate JSON");
                ModelLimits.requireWithin(length,limits.jsonBytes(),"JSON");
                ByteBuffer bytes=source.read(offset,(int)length,token);
                String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(bytes).toString();
                json=parseJson(text,token);
            } else if(type==BIN) {
                if(bin!=null || count!=1) throw problem("chunks","BIN must be unique second chunk");
                bin=new Chunk(offset,length);
            }
            offset+=length; count++;
        }
        if(json==null) throw problem("JSON","missing document");
        JsonObject asset=GltfJson.optionalObject(json,"asset","");
        if(!"2.0".equals(GltfJson.string(asset,"version","","/asset")))
            throw problem("/asset/version","only glTF 2.0 is supported");
        if(asset.has("minVersion") && !"2.0".equals(GltfJson.string(asset,"minVersion","","/asset")))
            throw problem("/asset/minVersion","unsupported minimum version");
        return new Container(json,Optional.ofNullable(bin),source.size());
    }
    public static void requireExtensions(JsonObject json,Set<String> supported) throws ModelProblem {
        try {
            Set<String> used=new HashSet<>();
            if(json.has("extensionsUsed")) for(JsonElement e:json.getAsJsonArray("extensionsUsed")) used.add(e.getAsString());
            if(json.has("extensionsRequired")) for(JsonElement e:json.getAsJsonArray("extensionsRequired")) {
                String name=e.getAsString();
                if(!used.contains(name)) throw problem("/extensionsRequired","required extension missing from extensionsUsed");
                if(!supported.contains(name)) throw new ModelProblem(ModelProblem.Code.UNSUPPORTED_REQUIRED_EXTENSION,"/extensionsRequired",name);
            }
        } catch(IllegalStateException | UnsupportedOperationException e) { throw problem("/extensionsRequired","invalid extension list"); }
    }
    public static JsonObject parseJson(String text,ModelCancellation token) throws IOException {
        // Validate depth/token budget before Gson's recursive tree construction.
        try(JsonReader r=new JsonReader(new StringReader(text))) {
            r.setStrictness(Strictness.STRICT); int depth=0; long tokens=0;
            while(r.peek()!=JsonToken.END_DOCUMENT) {
                if((++tokens&1023)==0) token.check();
                if(tokens>1_000_000) throw problem("JSON","token limit exceeded");
                switch(r.peek()) {
                    case BEGIN_ARRAY -> { r.beginArray(); depth++; }
                    case END_ARRAY -> { r.endArray(); depth--; }
                    case BEGIN_OBJECT -> { r.beginObject(); depth++; }
                    case END_OBJECT -> { r.endObject(); depth--; }
                    case NAME -> r.nextName();
                    case STRING, NUMBER -> r.nextString();
                    case BOOLEAN -> r.nextBoolean();
                    case NULL -> r.nextNull();
                    default -> throw problem("JSON","invalid JSON token");
                }
                if(depth>128) throw problem("JSON","nesting limit exceeded");
            }
            JsonElement element=JsonParser.parseString(text);
            if(!element.isJsonObject()) throw problem("JSON","root must be object");
            return element.getAsJsonObject();
        } catch(JsonParseException | IllegalStateException e) { throw problem("JSON",e.getMessage()); }
    }
    private static ModelProblem problem(String location,String detail) { return new ModelProblem(ModelProblem.Code.INVALID_CONTAINER,location,detail); }
}
