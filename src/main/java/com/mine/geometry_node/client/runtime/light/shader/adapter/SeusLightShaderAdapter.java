package com.mine.geometry_node.client.runtime.light.shader.adapter;

import static com.mine.geometry_node.client.runtime.light.shader.adapter.LightShaderPatch.*;

public final class SeusLightShaderAdapter implements LightShaderAdapter {
    public String name() { return "SEUS PTGI E12"; }
    public boolean matches(String source) {
        return source.contains("CalculateSunlightVisibility") && source.replaceAll("\\s+", "").contains("FromSH(skySHR")
                || source.replaceAll("\\s+", "").contains("GBufferDataTransparentf=GetGBufferDataTransparent()") && source.contains("UnderwaterFog");
    }
    public String patch(String source) {
        if (source.replaceAll("\\s+", "").contains("GBufferDataTransparentf=GetGBufferDataTransparent()")) {
            String result = replace(source,"float m=length(r.xyz);", "float m=length(r.xyz);\n"
                    // Entities-translucent may fall back to the solid entity gbuffer layout.
                    // Its packed material bytes are not the water/glass normal encoding.
                    + "bool nodeTransparentPayload=false;\n"
                    + "if(nodeLightCount>0 && x){vec2 nodeTransparentNormalCode=(textureLod(colortex1,texcoord.xy,0).zw-vec2(0.6))/0.4;\n"
                    + "nodeTransparentPayload=dot(nodeTransparentNormalCode,nodeTransparentNormalCode)<=1.0;}\n");
            result = replace(result, "M.xyz=pow(textureLod(colortex3,o.xy,0).xyz,vec3(2.2));",
                    "M.xyz=pow(textureLod(colortex3,o.xy,0).xyz,vec3(2.2));\n"
                    + "if(nodeLightCount>0 && nodeTransparentPayload){float nodeBackDepth=textureLod(depthtex1,o.xy,0).x;\n"
                    + "vec3 nodeBack=(gbufferModelViewInverse*vec4(GetViewPosition(o.xy,nodeBackDepth).xyz,1.0)).xyz;\n"
                    + "vec3 nodeFront=(gbufferModelViewInverse*vec4(GetViewPosition(o.xy,textureLod(depthtex0,o.xy,0).x).xyz,1.0)).xyz;\n"
                    + "M.xyz+=0.001*(nodeSourceSegment(nodeBack,nodeBackDepth>=1.0,length(nodeFront-nodeEyeOffset))+nodeVolumeBetweenResult(nodeFront,nodeBack,texcoord.xy));}\n");
            return replace(result, "M.xyz=pow(M.xyz,vec3(1./2.2));",
                    "if(nodeLightCount>0){if(x && nodeTransparentPayload) M.xyz += 0.001*f.albedo.w*nodeTransparentSurface(t.xyz,z,mat3(gbufferModelViewInverse)*f.geoNormal,vec3(0.0),f.albedo.w,0.15,i.water>0.5?0.02:0.04);\n"
                    + "M.xyz+=0.001*(nodeVolumeResult(t.xyz,texcoord.xy)+nodeSourceResult(t.xyz,v.depth>=1.0));\n"
                    + "}\nM.xyz=pow(M.xyz,vec3(1./2.2));");
        }
        String output = output(source, "vec4(t.xyz,1.)");
        if (output == null) return null;
        // Keep one live call site for the sampling/BVH traversal in this program.
        // The receiver and normals do not change before t.xyz=e in SEUS E12.
        String result = replace(source, "if(z.grass>.5) f=vec3(0.,1.,0.);",
                "if(z.grass>.5) f=vec3(0.,1.,0.);\n"
                + "vec3 nodeSurfaceLight=vec3(0.0);\n"
                + "if(nodeLightCount>0 && (nodeLightPass!=0 ? z.sky<=0.5 : z.sky<0.5)) nodeSurfaceLight=nodeSurfacePassGeo(y.xyz,f,faceforward(r,m.xyz,r),texcoord.xy,nodeLightPass!=0);\n"
                + "if(nodeLightPass!=0){"+output+"=vec4(nodeSurfaceLight,length(y.xyz));return;}\n");
        result = replace(result, "t.xyz=e;", "t.xyz=e;\nt += v.albedo.xyz * nodeSurfaceLight;\n");
        return result;
    }
}
