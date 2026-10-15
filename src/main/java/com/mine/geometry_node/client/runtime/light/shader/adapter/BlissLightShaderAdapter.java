package com.mine.geometry_node.client.runtime.light.shader.adapter;

import static com.mine.geometry_node.client.runtime.light.shader.adapter.LightShaderPatch.*;

public final class BlissLightShaderAdapter implements LightShaderAdapter {
    public String name() { return "BLISS 2.0.4"; }
    public boolean matches(String source) {
        return source.contains("GriAndEminShadowFix") && source.contains("FINAL_COLOR")
                || source.contains("TranslucentShader") && source.contains("bloomyFogMult") && source.contains("refractedCoord")
                || source.contains("Reflections_Final") && source.contains("UnchangedAlpha") && source.contains("DoDirectLighting");
    }
    public String patch(String source) {
        if(source.contains("TranslucentShader") && source.contains("bloomyFogMult")) {
            var out = java.util.regex.Pattern.compile("(gl_FragData\\s*\\[\\s*0\\s*\\]|\\w+)\\.r\\s*=\\s*vl\\.a\\s*\\*\\s*bloomyFogMult").matcher(source);
            if(!out.find())return null;
            String result=replace(source,"vec3 p3 = mat3(gbufferModelViewInverse) * fragpos;",
                    "vec3 p3 = mat3(gbufferModelViewInverse) * fragpos;\n"
                    +"vec3 nodeFront=vec3(0.0),nodeBack=vec3(0.0);\n"
                    +"if(nodeLightCount>0){nodeFront=p3+gbufferModelViewInverse[3].xyz;\n"
                    +"nodeBack=mat3(gbufferModelViewInverse)*fragpos2+gbufferModelViewInverse[3].xyz;}\n");
            String sample=source.contains("texture2D(colortex3")?"texture2D":"texture";
            result=replace(result,"vec3 color = "+sample+"(colortex3,refractedCoord).rgb;",
                    "vec3 color = "+sample+"(colortex3,refractedCoord).rgb;\n"
                    +"if(nodeLightCount>0) color += nodeSourceSegment(nodeBack,z2>=1.0,length(nodeFront-nodeEyeOffset))+nodeVolumeBetween(nodeFront,nodeBack);\n");
            return replace(result,"color *= vl.a;",
                    "if(nodeLightCount>0) color += nodeVolumeResult(nodeFront,gl_FragCoord.xy/nodeOriginalSize)+nodeSourceResult(nodeFront,z>=1.0);\ncolor *= vl.a;");
        }
        if (source.contains("Reflections_Final")) {
            return replace(source, "vec3 FinalColor = (Direct_lighting + Indirect_lighting) * Albedo;",
                    "vec3 FinalColor = (Direct_lighting + Indirect_lighting) * Albedo;\n"
                    + "if(nodeLightCount>0) FinalColor += nodeTransparentSurface(p3,mat3(gbufferModelViewInverse)*normal,mat3(gbufferModelViewInverse)*normal,Albedo,UnchangedAlpha,0.15,iswater>0.9?0.02:0.04);\n");
        }
        String output = output(source, "FINAL_COLOR");
        if (output == null) return null;
        String result = replace(source, "vec2 texcoord = gl_FragCoord.xy*texelSize;",
                "vec2 texcoord = (nodeLightPass==0 ? gl_FragCoord.xy : (gl_FragCoord.xy-nodePassViewport.xy)/nodePassViewport.zw*nodeOriginalSize)*texelSize;");
        result = replace(result, "vec3 FlatNormals = normalAndAO.rgb * 2.0 - 1.0;",
                "vec3 FlatNormals = normalAndAO.rgb * 2.0 - 1.0;\n" + surfacePrepass(output, "p3", "normal",
                        "faceforward(mat3(gbufferModelViewInverse)*FlatNormals, p3-gbufferModelViewInverse[3].xyz, mat3(gbufferModelViewInverse)*FlatNormals)", "z >= 1.0"));
        result = replace(result, "Emission(FINAL_COLOR, albedo, SpecularTex.a);",
                "Emission(FINAL_COLOR, albedo, SpecularTex.a);\nif(nodeLightCount>0) FINAL_COLOR += albedo * nodeSurfaceGeo(p3, normal, faceforward(mat3(gbufferModelViewInverse)*FlatNormals, p3-gbufferModelViewInverse[3].xyz, mat3(gbufferModelViewInverse)*FlatNormals), gl_FragCoord.xy/nodeOriginalSize);\n");
        return result;
    }
}
