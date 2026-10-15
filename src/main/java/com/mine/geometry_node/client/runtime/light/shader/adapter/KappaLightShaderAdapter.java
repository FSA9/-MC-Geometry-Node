package com.mine.geometry_node.client.runtime.light.shader.adapter;

/** Opaque lighting precedes Kappa's translucent rendering and refraction. */
public final class KappaLightShaderAdapter implements LightShaderAdapter {
    @Override public String name() { return "Kappa 5.2"; }

    @Override public boolean matches(String source) {
        return source.contains("GetEmitterLumColor") && source.contains("packReflectionAux")
                || source.contains("blendTranslucencies") && source.contains("fogScattering") && source.contains("scenePos0")
                || source.contains("translucentColor") && source.contains("fakeBounceDiff") && source.contains("waterNormal");
    }

    @Override public String patch(String source) {
        return patch(source, null);
    }

    @Override public String patch(String source, String vertex) {
        String conversion = "mat3(0.6131914784,0.3395120888,0.0473663312,0.0702069045,0.9163358171,0.0134500113,0.0206188714,0.1095672943,0.8696067475)";
        if (source.contains("blendTranslucencies")) {
            String result = LightShaderPatch.replace(source, "out vec3 sceneColor;", "out vec4 nodeKappaOutput;");
            if (result == null) return null;
            var main = java.util.regex.Pattern.compile("\\bvoid\\s+main\\s*\\(").matcher(result);
            if (!main.find()) return null;
            result = result.substring(0, main.start()) + result.substring(main.start()).replaceAll("\\bsceneColor\\b", "nodeKappaOutput.rgb");
            result = LightShaderPatch.replace(result, "bool translucent = sceneDepth.x < sceneDepth.y;",
                    "bool translucent = sceneDepth.x < sceneDepth.y;\nnodeKappaOutput.a=1.0;\n");
            result = LightShaderPatch.replace(result, "if (water && isEyeInWater == 0)",
                    "if(nodeLightCount>0) nodeKappaOutput.rgb += (nodeSourceSegment(scenePos1,sceneDepth.y>=1.0,length(scenePos0-nodeEyeOffset))+nodeVolumeBetween(scenePos0,scenePos1))*" + conversion + ";\n"
                    + "if (water && isEyeInWater == 0)");
            return LightShaderPatch.replace(result, "nodeKappaOutput.rgb = drawbufferClamp(nodeKappaOutput.rgb);",
                    "if(nodeLightCount>0) nodeKappaOutput.rgb += (nodeVolumeResult(scenePos0,uv)+nodeSourceResult(scenePos0,sceneDepth.x>=1.0))*" + conversion + ";\nnodeKappaOutput.rgb = drawbufferClamp(nodeKappaOutput.rgb);");
        }
        if (source.contains("fakeBounceDiff")) {
            return LightShaderPatch.replace(source, "translucentColor = clamp(sceneColor,0.0,65535.0);",
                    "if(nodeLightCount>0) sceneColor.rgb += nodeTransparentSurface(scenePos,sceneNormal,normal,vec3(0.0),sceneColor.a,max(1.0-specularData.x,0.05),matID==102?0.02:0.04)*" + conversion + ";\n"
                    + "translucentColor = clamp(sceneColor,0.0,65535.0);");
        }
        if (vertex == null) return null;
        var scaleMatcher = java.util.regex.Pattern.compile("\\buv\\s*\\*=\\s*([0-9]+(?:\\.[0-9]*)?[fF]?)\\s*\\*\\s*1\\.01[fF]?").matcher(vertex);
        if (!scaleMatcher.find()) return null;
        String scale = scaleMatcher.group(1).replaceAll("[fF]$", "");
        String result = source;
        for (String uniform : new String[]{"gbufferProjectionInverse", "gbufferModelViewInverse"}) {
            if (!java.util.regex.Pattern.compile("\\buniform\\s+mat4\\s+" + uniform + "\\b").matcher(result).find())
                result = LightShaderPatch.replace(result, "void main()", "uniform mat4 " + uniform + ";\nvoid main()");
            if (result == null) return null;
        }
        if (!java.util.regex.Pattern.compile("\\buniform\\s+vec2\\s+taaOffset\\b").matcher(result).find())
            result = LightShaderPatch.replace(result, "void main()", "uniform vec2 taaOffset;\nvoid main()");
        result = LightShaderPatch.replace(result, "void main()", "uniform int nodePackJitter;\nvoid main()");
        result = LightShaderPatch.replace(result, "vec3 albedo = vec3(1.0);", """
                vec3 nodePosition=vec3(0.0),nodeNormal=vec3(0.0),nodeGeo=vec3(0.0);
                if(nodeLightCount>0){
                vec2 nodeScreen = (uv * 2.0 - (nodePackJitter!=0 ? taaOffset : vec2(0.0))) / NODE_KAPPA_SCALE - 1.0;
                vec4 nodeView = gbufferProjectionInverse * vec4(nodeScreen, sceneDepth * 2.0 - 1.0, 1.0);
                nodePosition = (gbufferModelViewInverse * vec4(nodeView.xyz / nodeView.w, 1.0)).xyz;
                nodeNormal = decodeNormal(texture(colortex1,uv).xy);
                nodeGeo = normalize(cross(dFdx(nodePosition),dFdy(nodePosition)));
                nodeGeo = faceforward(nodeGeo,nodePosition-gbufferModelViewInverse[3].xyz,nodeGeo);
                }
                """.replace("NODE_KAPPA_SCALE", scale) + LightShaderPatch.surfacePrepass("sceneColor", "nodePosition", "nodeNormal", "nodeGeo", "sceneDepth>=1.0")
                + "vec3 albedo = vec3(1.0);");
        result = LightShaderPatch.replace(result, "sceneColor.rgb *= directLight + indirectLight + emission;",
                "sceneColor.rgb *= directLight + indirectLight + emission;\nif(nodeLightCount>0) sceneColor.rgb += albedo*(nodeSurfaceGeo(nodePosition,nodeNormal,nodeGeo,gl_FragCoord.xy/nodeOriginalSize)*" + conversion + ");");
        return result;
    }
}
