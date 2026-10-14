package com.mine.geometry_node.client.runtime.light.shader.adapter;

/** Photon opaque/forward receivers and camera transmission in its final HDR composite. */
public final class PhotonLightShaderAdapter implements LightShaderAdapter {
    @Override public String name() { return "Photon"; }

    @Override public boolean matches(String source) {
        return source.contains("scene_color") && source.contains("half_res_pos")
                && source.contains("material_from") && source.contains("get_diffuse_lighting")
                || source.contains("scene_color") && source.contains("biome_water_coeff") && source.contains("border_fog")
                || source.contains("scene_color") && source.contains("purkinje_shift") && source.contains("view_back_pos");
    }

    @Override public String patch(String source) {
        if (source.contains("purkinje_shift") && source.contains("view_back_pos")) return composite(source);
        if (source.contains("biome_water_coeff")) {
            return LightShaderPatch.replace(source, "scene_color = vec4(radiance / max(alpha, eps), alpha);",
                    "radiance += (nodeTransparentSurface(scene_pos,normal,tbn[2],vec3(0.0),alpha,material.roughness,is_water?0.02:0.04)*rec709_to_rec2020)*alpha;\n"
                    + "scene_color = vec4(radiance / max(alpha, eps), alpha);");
        }
        boolean mappedNormal = java.util.regex.Pattern.compile("\\bvec3\\s+normal\\s*=\\s*decode_unit_vector\\s*\\(").matcher(source).find();
        String normal = mappedNormal ? "normal" : "flat_normal";
        String result = LightShaderPatch.replace(source, "out vec3 scene_color;", "out vec4 nodePhotonOutput;");
        if (result == null) return null;
        var main = java.util.regex.Pattern.compile("\\bvoid\\s+main\\s*\\(").matcher(result);
        if (!main.find()) return null;
        result = result.substring(0, main.start()) + result.substring(main.start()).replaceAll("\\bscene_color\\b", "nodePhotonOutput.rgb");
        result = LightShaderPatch.replace(result, "ivec2 texel = ivec2(gl_FragCoord.xy);",
                "vec2 nodePixel = nodeLightPass==0 ? gl_FragCoord.xy : (gl_FragCoord.xy-nodePassViewport.xy)/nodePassViewport.zw*nodeOriginalSize;\n"
                + "ivec2 texel = ivec2(nodePixel);\nnodePhotonOutput.a=1.0;");
        result = LightShaderPatch.replace(result, "vec3 world_dir = normalize(scene_pos - gbufferModelViewInverse[3].xyz);",
                "vec3 world_dir = normalize(scene_pos - gbufferModelViewInverse[3].xyz);\n"
                + "vec3 nodeFlat = decode_unit_vector(unpack_unorm_2x8(gbuffer_data_0.z));\n"
                + LightShaderPatch.surfacePrepass("nodePhotonOutput", "scene_pos", mappedNormal ? "decode_unit_vector(gbuffer_data_1.xy)" : "nodeFlat",
                    "faceforward(nodeFlat,world_dir,nodeFlat)", "depth == 1.0"));
        // These anchors occur after material decoding, so normal maps and the material response survive.
        result = LightShaderPatch.replace(result, "nodePhotonOutput.rgb = get_diffuse_lighting(",
                "nodePhotonOutput.rgb = material.albedo * (nodeSurfaceGeo(scene_pos," + normal + ",faceforward(flat_normal,world_dir,flat_normal),nodePixel/nodeOriginalSize)*rec709_to_rec2020) * rcp_pi * mix(1.0,metal_diffuse_amount,float(material.is_metal))\n"
                + "+ get_diffuse_lighting(");
        return result;
    }

    private static String composite(String source) {
        String result = LightShaderPatch.replace(source, "out vec3 scene_color;", "out vec4 nodePhotonComposite;\nvec3 scene_color;");
        if (result == null) return null;
        result = LightShaderPatch.replace(result, "ivec2 texel = ivec2(gl_FragCoord.xy);",
                "vec2 nodePixel=nodeLightPass==0?gl_FragCoord.xy:(gl_FragCoord.xy-nodePassViewport.xy)/nodePassViewport.zw*nodeOriginalSize;\n"
                        + "ivec2 texel=ivec2(nodePixel);\nnodePhotonComposite.a=1.0;");
        result = LightShaderPatch.replace(result, "float depth1 = texelFetch(depthtex1, texel, 0).x;",
                "float depth1 = texelFetch(depthtex1, texel, 0).x;\n"
                        + "vec3 nodeEndpoint=view_to_scene_space(screen_to_view_space(vec3(uv,depth1),true));\n"
                        + "if(nodeLightPass!=0){nodePhotonComposite=vec4(nodeVolumeTransmitted(nodeLightIndex,nodeEndpoint),length(nodeEndpoint));return;}\n");
        // Forward transparency already blended into colortex0. Add the camera-path result
        // here so a pane behind a source cannot tint it, and never apply pack tint twice.
        String lighting = """
                vec3 nodeCameraLight=nodeSourceTransmitted(nodeEndpoint,depth1>=1.0);
                for(int nodeI=0;nodeI<min(nodeLightCount,8);nodeI++) {
                    if(nodeLights[nodeI*12+6].x<0.5)continue;
                    vec3 nodeValue=nodeLightResolved!=0?nodeVolumeReconstruct(nodeI,nodeEndpoint,nodePixel/nodeOriginalSize):vec3(-1.0);
                    nodeCameraLight+=nodeValue.x>=0.0?nodeValue:nodeVolumeTransmitted(nodeI,nodeEndpoint);
                }
                scene_color+=nodeCameraLight*rec709_to_rec2020;
                """;
        result = LightShaderPatch.replace(result, "scene_color = purkinje_shift(scene_color, vec2(0.0, 1.0));",
                lighting + "scene_color = purkinje_shift(scene_color, vec2(0.0, 1.0));\nnodePhotonComposite=vec4(scene_color,1.0);");
        return LightShaderPatch.replace(result, "scene_color = purkinje_shift(scene_color, light_levels);",
                lighting + "scene_color = purkinje_shift(scene_color, light_levels);\nnodePhotonComposite=vec4(scene_color,1.0);");
    }
}
