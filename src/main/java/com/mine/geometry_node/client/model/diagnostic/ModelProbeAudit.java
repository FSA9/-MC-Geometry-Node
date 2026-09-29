package com.mine.geometry_node.client.model.diagnostic;
import com.google.gson.*;
import com.mine.geometry_node.core.engine.system.model.asset.ModelLimits;
import com.mine.geometry_node.core.engine.system.model.io.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
/** File-only report work. Called off the client/render thread; never class-loads Iris. */
public final class ModelProbeAudit {
    private ModelProbeAudit() {}
    public static Map<String,Object> inspect(Path file,ModelCancellation token) throws IOException {
        long start=System.nanoTime();
        try(FileModelSource source=new FileModelSource(file,ModelLimits.DEFAULT)) {
            var glb=new GlbContainerReader().read(source,ModelLimits.DEFAULT,token);
            var result=new LinkedHashMap<String,Object>();
            result.put("file",file.toString());result.put("encodedBytes",source.size());
            result.put("bytesRead",source.bytesRead());result.put("largestRead",source.largestRead());
            result.put("milliseconds",(System.nanoTime()-start)/1_000_000.0);
            result.put("binary",glb.binary().orElse(null));
            for(String key:List.of("nodes","meshes","materials","animations","skins"))
                result.put(key,glb.json().has(key)?glb.json().getAsJsonArray(key).size():0);
            result.put("extensionsRequired",glb.json().get("extensionsRequired"));
            result.put("meaning","Container inspection only; not full glTF import or render verification");
            return result;
        }
    }
    public static Path writeReport(Path gameDirectory,Map<String,Object> render,ModelCancellation token) throws IOException {
        var report=new LinkedHashMap<String,Object>();report.put("schemaVersion",1);report.put("stage","P0");
        report.put("createdAt",java.time.Instant.now().toString());report.put("minecraft","26.1.2");
        report.put("neoforge","26.1.2.75");report.put("java",System.getProperty("java.version"));
        report.put("packageLimitBytes",ModelLimits.MAX_PACKAGE_BYTES);report.put("render",render);
        report.put("shaderpacks",inventory(gameDirectory.resolve("shaderpacks"),".zip",token));
        report.put("mods",inventory(gameDirectory.resolve("mods"),".jar",token));
        report.put("compatibilityRule","Every installed pack has independent per-feature UNTESTED/VERIFIED/DEGRADED evidence; no common-lowest feature cap");
        Path output=gameDirectory.resolve("geometry_node/model/p0-report.json");Files.createDirectories(output.getParent());
        token.check();
        try(Writer out=Files.newBufferedWriter(output)) {new GsonBuilder().setPrettyPrinting().create().toJson(report,out);}
        return output;
    }
    private static List<Map<String,Object>> inventory(Path directory,String suffix,ModelCancellation token) throws IOException {
        List<Map<String,Object>> result=new ArrayList<>();if(!Files.isDirectory(directory))return result;
        try(var files=Files.list(directory)) {
            for(Path p:files.filter(f->Files.isRegularFile(f)&&f.getFileName().toString().endsWith(suffix)).sorted().toList()) {
                token.check();var item=new LinkedHashMap<String,Object>();item.put("file",p.getFileName().toString());
                item.put("bytes",Files.size(p));item.put("sha256",hash(p,token));
                var evidence=new LinkedHashMap<String,String>();
                for(var feature:com.mine.geometry_node.core.engine.system.model.capability.ModelCapabilities.Feature.values()) evidence.put(feature.name(),"UNTESTED");
                item.put("featureEvidence",evidence);result.add(item);
            }
        }
        return result;
    }
    private static String hash(Path file,ModelCancellation token) throws IOException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];
            try(InputStream in=Files.newInputStream(file)) {int n;while((n=in.read(b))!=-1){token.check();digest.update(b,0,n);}}
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
