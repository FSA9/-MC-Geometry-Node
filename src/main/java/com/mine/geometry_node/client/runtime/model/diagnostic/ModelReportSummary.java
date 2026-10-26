package com.mine.geometry_node.client.runtime.model.diagnostic;

import java.util.*;
import com.mine.geometry_node.client.runtime.model.render.resource.ModelResidencyLedger;

/** Chat-sized model statistics and individual query results. */
final class ModelReportSummary {
    static String status(Map<String,Object> report) {
        Map<?,?> geometry=child(report,"geometry"),source=child(geometry,"uniqueAssets"),memory=child(report,"memory");
        Map<?,?> renderer=child(report,"renderer");
        Map<?,?> residency=residency(renderer.get("residency"));
        long cpu=number(memory,"cpuGeometryBytes")+number(memory,"sourceCpuGeometryBytes");
        return String.format(Locale.ROOT,
                "Models %d, assets %d; %,d vertices / %,d triangles / %,d primitives; model memory estimates CPU geometry %s, GPU ledger %s (texture subset %s); pending %d, failures %d; %s.",
                size(report.get("instances")),size(report.get("assets")),number(source,"vertices"),number(source,"triangles"),number(source,"primitives"),
                bytes(cpu),bytes(number(residency,"gpuBytes")),bytes(number(residency,"textureBytes")),
                size(report.get("pending")),size(report.get("failures")),modelLodStatus(renderer));
    }
    static String query(Map<String,Object> report) {
        String id=Objects.toString(report.get("id"),"?");String state=Objects.toString(report.get("state"),"UNKNOWN");
        Map<?,?> instance=child(report,"instance"),asset=child(instance,"asset");
        Map<?,?> counts=child(child(asset,"geometry"),"sourceMeshes");
        String detail=report.containsKey("failure")?"; "+report.get("failure"):
                instance.isEmpty()?"":String.format(Locale.ROOT,"; %,d vertices / %,d triangles; visible=%s; GPU base ready=%s",
                        number(counts,"vertices"),number(counts,"triangles"),instance.get("visible"),asset.get("gpuResident"));
        if(instance.containsKey("renderDeferredReason")) detail+="; "+instance.get("renderDeferredReason");
        Map<?,?> lod=child(asset,"modelLod");
        if(!lod.isEmpty()) detail+=String.format(Locale.ROOT,"; LOD cache %s, extra CPU %s, %d levels",
                Objects.toString(lod.get("cacheStatus"),"UNKNOWN"),bytes(number(lod,"cpuBytes")),size(lod.get("levels")));
        return "Model "+id+": "+state+detail+". /model report saves performance statistics.";
    }
    private static Map<?,?> residency(Object value) {
        if(value instanceof ModelResidencyLedger.Snapshot snapshot)return Map.of("gpuBytes",snapshot.gpuBytes(),"textureBytes",snapshot.textureBytes());
        return value instanceof Map<?,?> map?map:Map.of();
    }
    private static Map<?,?> child(Map<?,?> map,String key){return map.get(key) instanceof Map<?,?> value?value:Map.of();}
    private static String modelLodStatus(Map<?,?> renderer) {
        Map<?,?> lod=child(renderer,"modelLod"),policy=child(lod,"policy");
        if(lod.isEmpty()) return "LOD unreported";
        Map<?,?> frame=child(child(child(lod,"metrics"),"MAIN"),"lastFrame");
        String mode=Objects.toString(policy.get("mode"),"UNKNOWN");
        if(number(frame,"pendingObjects")>0) return String.format(Locale.ROOT,
                "LOD %s; GPU uploading %d objects; main frame %,d triangles",
                mode,number(frame,"pendingObjects"),number(frame,"selectedTriangles"));
        if(number(frame,"instances")==0) return "LOD "+mode+", no main-pass samples";
        return String.format(Locale.ROOT,"LOD %s; last object requested %d, effective %d; main frame %,d -> %,d triangles",
                mode,number(frame,"lastRequestedLevel"),number(frame,"lastEffectiveLevel"),
                number(frame,"sourceTriangles"),number(frame,"selectedTriangles"));
    }
    private static long number(Map<?,?> map,String key){return map.get(key) instanceof Number number?number.longValue():0;}
    private static int size(Object value){return value instanceof Collection<?> c?c.size():value instanceof Map<?,?> m?m.size():0;}
    private static String bytes(long value){return String.format(Locale.ROOT,"%.2f MiB",value/(1024.0*1024));}
    private ModelReportSummary(){}
}
