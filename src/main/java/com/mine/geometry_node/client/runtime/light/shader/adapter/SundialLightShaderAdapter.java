package com.mine.geometry_node.client.runtime.light.shader.adapter;

/** Sundial separates opaque diffuse lighting from the later transparent/atmosphere composite. */
public final class SundialLightShaderAdapter implements LightShaderAdapter {
    @Override public String name() { return "Sundial Lite 1.1.0"; }

    @Override public boolean matches(String source) {
        return source.contains("screenSpaceVisibiliyBitmask") && source.contains("diffuseAbsorptionWeight")
                || source.contains("waterStain") && source.contains("waterViewDepthNoLimit")
                && source.contains("vanillaLight");
    }

    @Override public String patch(String source) {
        // Varying texcoord covers the original image during atlas draws, but integer fetches
        // otherwise sample the atlas viewport coordinates instead of the receiver pixel.
        String result = LightShaderPatch.replace(source, "ivec2 texel = ivec2(gl_FragCoord.st);",
                "ivec2 texel = ivec2(nodeLightPass == 0 ? gl_FragCoord.xy : "
                        + "(gl_FragCoord.xy-nodePassViewport.xy)/nodePassViewport.zw*nodeOriginalSize);");
        if (source.contains("screenSpaceVisibiliyBitmask")) {
            result = LightShaderPatch.replace(result, "vec4 currData = vec4(0.0);", """
                    vec3 nodePosition=vec3(0.0),nodeNormal=vec3(0.0),nodeGeoNormal=vec3(0.0);
                    if(nodeLightCount>0){
                    vec3 nodeSurfaceView = viewPos;
                    if (isOriginNotHand > 0.5 && gbufferData.depth >= 0.0) {
                        nodeSurfaceView = screenToViewPos(texcoord, textureLod(depthtex1, texcoord, 0.0).x);
                    }
                    nodePosition = viewToWorldPos(nodeSurfaceView);
                    nodeNormal = mat3(gbufferModelViewInverse) * gbufferData.normal;
                    nodeGeoNormal = mat3(gbufferModelViewInverse) * faceforward(gbufferData.geoNormal, nodeSurfaceView, gbufferData.geoNormal);
                    }
                    if (nodeLightPass != 0) {
                        texBuffer3 = vec4(abs(gbufferData.depth) < 1.0 ? nodeDirectGeo(nodeLightIndex, nodePosition, nodeNormal, nodeGeoNormal) : vec3(0.0), length(nodePosition));
                        return;
                    }
                    vec4 currData = vec4(0.0);
                    """);
            // The pack's linear diffuse absorption/albedo is applied below this insertion.
            return LightShaderPatch.replace(result, "float diffuseWeight = pow(1.0 - gbufferData.smoothness, 5.0);",
                    "if(nodeLightCount>0) lightColor += nodeSurfaceGeo(nodePosition, nodeNormal, nodeGeoNormal, gl_FragCoord.xy/nodeOriginalSize);\n"
                            + "float diffuseWeight = pow(1.0 - gbufferData.smoothness, 5.0);");
        }
        result = LightShaderPatch.replace(result, "float waterViewDepthNoLimit = length(waterViewPos);", """
                vec3 nodePosition=vec3(0.0),nodeNormal=vec3(0.0),nodeGeoNormal=vec3(0.0);
                if(nodeLightCount>0){
                nodePosition = mat3(gbufferModelViewInverse) * waterViewPos + gbufferModelViewInverse[3].xyz;
                nodeNormal = mat3(gbufferModelViewInverse) * gbufferData.normal;
                nodeGeoNormal = mat3(gbufferModelViewInverse) * faceforward(gbufferData.geoNormal, waterViewPos, gbufferData.geoNormal);
                }
                if (nodeLightPass != 0) {
                    texBuffer0 = vec4(waterDepth < solidDepth ? nodeDirectGeo(nodeLightIndex, nodePosition, nodeNormal, nodeGeoNormal) : vec3(0.0), length(nodePosition));
                    return;
                }
                float waterViewDepthNoLimit = length(waterViewPos);
                """);
        result = LightShaderPatch.replace(result,
                "vec3 rawSolidColor = solidColor.rgb;",
                "if(nodeLightCount>0) solidColor.rgb += nodeSourceSegment(worldPos,solidDepth>=0.999999,length(nodePosition-nodeEyeOffset)) + nodeVolumeBetween(nodePosition,worldPos);\n"
                        + "vec3 rawSolidColor = solidColor.rgb;");
        result = LightShaderPatch.replace(result,
                "float isTargetParticle = 1.0 - float(isTargetNotParticle);",
                "float isTargetParticle = 1.0 - float(isTargetNotParticle);\n"
                        + "if(nodeLightCount>0) solidColor.rgb += nodeTransparentSurface(nodePosition,nodeNormal,nodeGeoNormal,vec3(0.0),gbufferData.albedo.w,max(1.0-gbufferData.smoothness,0.05),0.04)*gbufferData.albedo.w;\n");
        // This is the final HDR composite before bloom/tonemapping; earlier sky lighting
        // would be replaced by the pack's atmospheric sky rendering.
        return LightShaderPatch.replace(result, "texBuffer3 = solidColor;",
                "if(nodeLightCount>0) solidColor.rgb += nodeVolumeResult(nodePosition,gl_FragCoord.xy/nodeOriginalSize) + nodeSourceResult(nodePosition, waterDepth >= 0.999999);\ntexBuffer3 = solidColor;");
    }
}
