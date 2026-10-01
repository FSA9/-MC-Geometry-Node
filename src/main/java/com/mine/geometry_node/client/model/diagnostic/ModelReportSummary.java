package com.mine.geometry_node.client.model.diagnostic;

import java.util.*;
import com.mine.geometry_node.client.model.render.ModelResidencyLedger;

/** Chat-sized views of complete diagnostic snapshots. Never serializes the full JSON into chat. */
final class ModelReportSummary {
    static String status(Map<String,Object> report) {
        Map<?,?> geometry=child(report,"geometry"),source=child(geometry,"uniqueAssets"),memory=child(report,"memory");
        Map<?,?> residency=residency(child(report,"renderer").get("residency"));
        long cpu=number(memory,"cpuGeometryBytes")+number(memory,"sourceCpuGeometryBytes");
        return String.format(Locale.ROOT,
                "Models %d, assets %d; %,d vertices / %,d triangles / %,d primitives; model memory estimates CPU geometry %s, GPU ledger %s (texture subset %s); pending %d, failures %d.",
                size(report.get("instances")),size(report.get("assets")),number(source,"vertices"),number(source,"triangles"),number(source,"primitives"),
                bytes(cpu),bytes(number(residency,"gpuBytes")),bytes(number(residency,"textureBytes")),
                size(report.get("pending")),size(report.get("failures")));
    }
    static String query(Map<String,Object> report) {
        String id=Objects.toString(report.get("id"),"?");String state=Objects.toString(report.get("state"),"UNKNOWN");
        Map<?,?> instance=child(report,"instance"),asset=child(instance,"asset");
        Map<?,?> counts=child(child(asset,"geometry"),"sourceMeshes");
        String detail=report.containsKey("failure")?"; "+report.get("failure"):
                instance.isEmpty()?"":String.format(Locale.ROOT,"; %,d vertices / %,d triangles; visible=%s; GPU base ready=%s",
                        number(counts,"vertices"),number(counts,"triangles"),instance.get("visible"),asset.get("gpuResident"));
        if(instance.containsKey("renderDeferredReason")) detail+="; "+instance.get("renderDeferredReason");
        return "Model "+id+": "+state+detail+". /model report saves full diagnostics.";
    }
    private static Map<?,?> residency(Object value) {
        if(value instanceof ModelResidencyLedger.Snapshot snapshot)return Map.of("gpuBytes",snapshot.gpuBytes(),"textureBytes",snapshot.textureBytes());
        return value instanceof Map<?,?> map?map:Map.of();
    }
    private static Map<?,?> child(Map<?,?> map,String key){return map.get(key) instanceof Map<?,?> value?value:Map.of();}
    private static long number(Map<?,?> map,String key){return map.get(key) instanceof Number number?number.longValue():0;}
    private static int size(Object value){return value instanceof Collection<?> c?c.size():value instanceof Map<?,?> m?m.size():0;}
    private static String bytes(long value){return String.format(Locale.ROOT,"%.2f MiB",value/(1024.0*1024));}
    private ModelReportSummary(){}
}
