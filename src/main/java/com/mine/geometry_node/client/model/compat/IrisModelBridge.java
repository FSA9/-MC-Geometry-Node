package com.mine.geometry_node.client.model.compat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.joml.Matrix4f;
import java.lang.reflect.*;
/** Optional API v0 adapter. Exact public signatures only; no internal Iris classes, globals or mixins. */
public final class IrisModelBridge {
    @FunctionalInterface public interface ShadowDraw { void draw(Matrix4f view,Matrix4f projection,double x,double y,double z,float tick); }
    private Object api;
    private Method active,shadow;
    private String status="Iris not installed";
    private boolean registered;
    private boolean attempted;
    public void initialize(RenderPipeline pipeline,ShadowDraw draw) {
        if(registered || attempted)return;
        attempted=true;
        try {
            Class<?> apiType=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api=apiType.getMethod("getInstance").invoke(null);
            int revision=(int)apiType.getMethod("getMinorApiRevision").invoke(api);
            active=apiType.getMethod("isShaderPackInUse"); shadow=apiType.getMethod("isRenderingShadowPass");
            if(revision<4) { status="Iris API "+revision+": shadow callback unavailable"; return; }
            Class<?> mainType=Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Class<?> shadowType=Class.forName("net.irisshaders.iris.api.v0.IrisShadowProgram");
            Class<?> callbackType=Class.forName("net.irisshaders.iris.api.v0.IrisShadowRenderCallback");
            apiType.getMethod("assignPipeline",RenderPipeline.class,mainType).invoke(api,pipeline,mainType.getField("ENTITIES").get(null));
            apiType.getMethod("assignPipelineShadow",RenderPipeline.class,shadowType).invoke(api,pipeline,shadowType.getField("SHADOW_ENTITIES").get(null));
            Object callback=Proxy.newProxyInstance(callbackType.getClassLoader(),new Class<?>[]{callbackType},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class) return switch(method.getName()) {
                    case "toString" -> "GeometryNode model shadow probe";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy==args[0];
                    default -> null;
                };
                if(method.getName().equals("renderShadow")) draw.draw(new Matrix4f((Matrix4f)args[0]),new Matrix4f((Matrix4f)args[1]),
                        (double)args[2],(double)args[3],(double)args[4],(float)args[5]);
                return null;
            });
            apiType.getMethod("registerShadowRenderCallback",callbackType).invoke(api,callback);
            registered=true; status="Iris API "+revision+": main/shadow mapping and opaque callback registered; visuals UNTESTED";
        } catch(ClassNotFoundException e) { status="Iris absent or required public API class unavailable: "+e.getMessage(); }
        catch(ReflectiveOperationException|LinkageError e) { status="Iris bridge unavailable: "+e; }
    }
    public String status() { return status; }
    public boolean available() { return registered; }
    public boolean active() { return query(active); }
    public boolean shadow() { return query(shadow); }
    private boolean query(Method method) {
        if(api==null||method==null)return false;
        try{return (boolean)method.invoke(api);} catch(ReflectiveOperationException|LinkageError e){status="Iris query failed: "+e;return false;}
    }
}
