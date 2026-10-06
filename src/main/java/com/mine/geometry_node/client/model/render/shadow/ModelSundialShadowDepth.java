package com.mine.geometry_node.client.model.render.shadow;

import com.mine.geometry_node.client.model.render.bvh.ModelPackLightAdapters;

/** Correct the depth of planar model triangles after Sundial's nonlinear XY shadow warp. */
public final class ModelSundialShadowDepth {
    public record Sources(String vertex, String fragment) {}
    private static final String POSITION = "geometry_node_shadowPosition";
    private static final java.util.regex.Pattern SUNDIAL_DEPTH = java.util.regex.Pattern.compile(
            "offsetLength\\s*\\*\\s*shadowProjection\\s*\\[\\s*2\\s*]\\s*\\.\\s*z");
    public static Sources patch(String vertex, String fragment) {
        var original = new Sources(vertex, fragment);
        if (vertex == null || fragment == null || vertex.contains(POSITION) || fragment.contains(POSITION)
                || !ModelSundialShadowUv.patched(vertex)
                || !fragment.contains("shadowCoordToWorldPos") || !fragment.contains("centerTexelOffset")) return original;
        boolean hasNativeDepthCorrection = SUNDIAL_DEPTH.matcher(fragment).find();
        // Unknown depth writers are deliberately left untouched; only the verified
        // Sundial correction is safe to compose with the model-only branch.
        if (!hasNativeDepthCorrection && fragment.contains("gl_FragDepth")) return original;
        String v = ModelPackLightAdapters.replaceUnique(vertex,
                "gl_Position = gl_ProjectionMatrix * viewPos;",
                POSITION + " = worldPos;\ngl_Position = gl_ProjectionMatrix * viewPos;");
        // Final Iris source uses its transformed projection name, rather than compatibility GLSL.
        if (v == null) v = ModelPackLightAdapters.replaceUnique(vertex,
                "gl_Position = iris_ProjMat * viewPos;",
                POSITION + " = worldPos;\ngl_Position = iris_ProjMat * viewPos;");
        if (v == null) return original;
        String f;
        if (hasNativeDepthCorrection) {
            // Sundial already writes a depth correction for vanilla geometry. Append the
            // model correction after that block, outside its optional preprocessor guard,
            // so models are corrected even when SHADOW_DISTORTION_FIX is disabled while
            // host draws retain the original path byte-for-byte.
            String modelDepth = """
                    if (geometry_node_repeatShadowUv != 0) {
                        gl_FragDepth = gl_FragCoord.z;
                        vec3 planeNormal = cross(dFdx(geometry_node_shadowPosition), dFdy(geometry_node_shadowPosition));
                        float planeLength2 = dot(planeNormal, planeNormal);
                        if (planeLength2 > 1e-20) {
                            planeNormal *= inversesqrt(planeLength2);
                            float facing = dot(planeNormal, shadowDirection);
                            if (abs(facing) > 1e-5) {
                                vec3 projected = vec3(centerTexelOffset / (realShadowMapResolution * 0.25), gl_FragCoord.z * 10.0 - 5.0);
                                vec3 pixelWorld = shadowCoordToWorldPos(projected);
                                float offset = dot(geometry_node_shadowPosition - pixelWorld, planeNormal) / facing;
                                gl_FragDepth = clamp(gl_FragDepth + offset * shadowProjection[2].z * 0.1, 0.0, 1.0);
                            }
                        }
                    }
                    shadowColor0 = albedo;
                    """;
            f = ModelPackLightAdapters.replaceUnique(fragment, "shadowColor0 = albedo;", modelDepth);
            if (f == null) return original;
        } else {
            f = ModelPackLightAdapters.replaceUnique(fragment, "shadowColor0 = albedo;", """
                // Always initialize depth, including non-model draws using this program.
                gl_FragDepth = gl_FragCoord.z;
                if (geometry_node_repeatShadowUv != 0) {
                    // Geometric plane, never the artist's interpolated shading normal.
                    vec3 planeNormal = cross(dFdx(geometry_node_shadowPosition), dFdy(geometry_node_shadowPosition));
                    float planeLength2 = dot(planeNormal, planeNormal);
                    if (planeLength2 > 1e-20) {
                        planeNormal *= inversesqrt(planeLength2);
                        float facing = dot(planeNormal, shadowDirection);
                        if (abs(facing) > 1e-5) {
                            vec3 projected = vec3(centerTexelOffset / (realShadowMapResolution * 0.25), gl_FragCoord.z * 10.0 - 5.0);
                            vec3 pixelWorld = shadowCoordToWorldPos(projected);
                            float offset = dot(geometry_node_shadowPosition - pixelWorld, planeNormal) / facing;
                            gl_FragDepth = clamp(gl_FragCoord.z + offset * shadowProjection[2].z * 0.1, 0.0, 1.0);
                        }
                    }
                }
                shadowColor0 = albedo;
                """);
        }
        if (f == null) return original;
        v = beforeMain(v, "out vec3 " + POSITION + ";\n");
        f = beforeMain(f, "in vec3 " + POSITION + ";\nuniform int " + ModelSundialShadowUv.UNIFORM + ";\n");
        return v == null || f == null ? original : new Sources(v, f);
    }
    private static String beforeMain(String source, String declaration) {
        var matcher = java.util.regex.Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{").matcher(source);
        if (!matcher.find()) return null;
        int at = matcher.start();
        if (matcher.find()) return null;
        return source.substring(0, at) + declaration + source.substring(at);
    }
    private ModelSundialShadowDepth() {}
}
