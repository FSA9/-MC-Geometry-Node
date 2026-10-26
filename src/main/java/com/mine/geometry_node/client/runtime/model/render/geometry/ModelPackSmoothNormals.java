package com.mine.geometry_node.client.runtime.model.render.geometry;

import com.mine.geometry_node.client.runtime.model.render.pipeline.ModelSundialMaterialSampling;

import java.util.regex.Pattern;

/** Preserve interpolated model normals in known packs that reconstruct a flat entity TBN. */
public final class ModelPackSmoothNormals {
    public record Sources(String vertex, String fragment) {}
    private static final String VARYING = "geometry_node_viewNormal";
    private static final String FLAG = ModelSundialMaterialSampling.UNIFORM;
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{");
    private static final Pattern SUNDIAL = Pattern.compile("rawData\\.normal\\s*=\\s*tbnMatrix\\[\\s*2\\s*]\\s*;");
    private static final Pattern SEUS = Pattern.compile("CalculateNormalAndTBN\\s*\\(\\s*viewPos\\.xyz\\s*,\\s*texcoord\\.st\\s*,\\s*N\\s*,\\s*tbn\\s*\\)\\s*;");

    private static final Pattern SUNDIAL_CURVE = Pattern.compile("dot\\s*\\(\\s*viewDir\\s*,\\s*rawData\\.geoNormal\\s*\\)");
    private static final Pattern SEUS_CURVE = Pattern.compile("\\bN\\s*/\\s*\\(\\s*pow\\s*\\(\\s*saturate\\s*\\(\\s*dot\\s*\\(\\s*viewNormal\\s*,\\s*viewDir\\s*\\)");

    public static Sources patch(String vertex, String fragment) {
        var original = new Sources(vertex, fragment);
        if (vertex == null || fragment == null || vertex.contains(VARYING) || fragment.contains(VARYING)) return original;
        // This runs after Iris transformation. Inputs absent from non-entity formats must not be fabricated.
        if (!Pattern.compile("\\bin\\s+vec3\\s+iris_Normal\\s*;").matcher(vertex).find()
                || !Pattern.compile("\\buniform\\s+mat3\\s+iris_NormalMat\\s*;").matcher(vertex).find()) return original;
        boolean sundial = fragment.contains("packUpGbufferDataSolid") && fragment.contains("calcTbnMatrix");
        boolean seus = fragment.contains("CalculateNormalAndTBN") && fragment.contains("gbuffer.normal")
                && fragment.contains("normalMapStrength");
        if (sundial == seus) return original;
        var anchor = (sundial ? SUNDIAL : SEUS).matcher(fragment);
        if (!anchor.find()) return original;
        int insertion = anchor.end();
        if (anchor.find()) return original;
        var curve = (sundial ? SUNDIAL_CURVE : SEUS_CURVE).matcher(fragment);
        if (!curve.find() || curve.start() <= insertion) return original;
        String curveReplacement = sundial
                ? "dot(viewDir, (" + FLAG + " != 0 ? tbnMatrix[2] : rawData.geoNormal))"
                : "(" + FLAG + " != 0 ? tbn[2] : N) / (pow(saturate(dot(viewNormal, viewDir)";
        if (curve.find()) return original;
        // Pack normal-facing correction also needs the smooth basis; geometric G-buffer outputs stay intact.
        fragment = curve.replaceFirst(java.util.regex.Matcher.quoteReplacement(curveReplacement));
        var vm = MAIN.matcher(vertex);
        var fm = MAIN.matcher(fragment);
        if (!vm.find() || !fm.find()) return original;
        int vStart = vm.start(), vBody = vm.end(), fStart = fm.start();
        if (insertion <= fm.end() || vm.find() || fm.find()) return original;
        String v = vertex.substring(0, vBody)
                + "\n" + VARYING + " = iris_NormalMat * iris_Normal;\n" + vertex.substring(vBody);
        v = v.substring(0, vStart) + "out vec3 " + VARYING + ";\n" + v.substring(vStart);
        String apply = sundial
                ? "tbnMatrix = geometry_node_smoothTbn(tbnMatrix); rawData.normal = tbnMatrix[2];"
                : "tbn = geometry_node_smoothTbn(tbn);";
        String f = fragment.substring(0, insertion) + "\nif (" + FLAG + " != 0) { " + apply + " }\n"
                + fragment.substring(insertion);
        // Separate from geometric normals used by each pack for bias and visibility decisions.
        String helper = "in vec3 " + VARYING + ";\n"
                + (fragment.contains("uniform int " + FLAG + ";") ? "" : "uniform int " + FLAG + ";\n")
                + """
                mat3 geometry_node_smoothTbn(mat3 original) {
                    float n2 = dot(geometry_node_viewNormal, geometry_node_viewNormal);
                    if (n2 < 1e-12) return original;
                    vec3 n = geometry_node_viewNormal * inversesqrt(n2);
                    vec3 t = original[0] - n * dot(n, original[0]);
                    if (!(dot(t, t) > 1e-12)) {
                        vec3 axis = abs(n.z) < 0.9 ? vec3(0.0, 0.0, 1.0) : vec3(0.0, 1.0, 0.0);
                        t = cross(axis, n);
                    }
                    t = normalize(t);
                    float handedness = dot(cross(original[0], original[1]), original[2]) < 0.0 ? -1.0 : 1.0;
                    return mat3(t, cross(n, t) * handedness, n);
                }
                """;
        f = f.substring(0, fStart) + helper + f.substring(fStart);
        return new Sources(v, f);
    }
    private ModelPackSmoothNormals() {}
}
