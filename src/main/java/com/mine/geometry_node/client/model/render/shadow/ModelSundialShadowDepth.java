package com.mine.geometry_node.client.model.render.shadow;

import com.mine.geometry_node.client.model.render.bvh.ModelPackLightAdapters;
import java.util.ArrayList;
import java.util.regex.Pattern;

/** Analytic triangle coverage and depth for Sundial's logarithmic shadow projection. */
public final class ModelSundialShadowDepth {
    public record Sources(String vertex, String geometry, String fragment) {
        public Sources(String vertex, String fragment) { this(vertex, null, fragment); }
    }
    private record Varying(String type, String name) {}
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{");
    private static final Pattern OUTPUT = Pattern.compile("\\bout\\s+(float|vec[234])\\s+(\\w+)\\s*;");
    private static final String CLIP = "modelSundialClip";

    public static Sources patch(String vertex, String fragment) {
        Sources original = new Sources(vertex, fragment);
        if (vertex == null || fragment == null || vertex.contains(CLIP)
                || !ModelSundialShadowUv.patched(vertex) || !fragment.contains("centerTexelOffset")) return original;
        if (ModelPackLightAdapters.replaceUnique(vertex,
                "float shadowDistortion = log(distortionStrength / clipLengthInv + 1.0) / log(distortionStrength + 1.0) * 0.5;", "") == null)
            return original;
        boolean nativeDepth = Pattern.compile("offsetLength\\s*\\*\\s*shadowProjection\\s*\\[\\s*2\\s*]\\s*\\.\\s*z").matcher(fragment).find();
        if (!nativeDepth && fragment.contains("gl_FragDepth")) return original;
        var strength = Pattern.compile("\\bconst\\s+float\\s+distortionStrength\\s*=([^;]+);").matcher(vertex);
        if (!strength.find()) return original;
        String distortion = strength.group(1);
        var outputs = new ArrayList<Varying>();
        var matcher = OUTPUT.matcher(vertex);
        while (matcher.find()) outputs.add(new Varying(matcher.group(1), matcher.group(2)));
        if (outputs.stream().noneMatch(o -> o.name.equals("texcoord"))
                || outputs.stream().noneMatch(o -> o.name.equals("color"))) return original;
        String v = ModelPackLightAdapters.replaceUnique(vertex, "gl_Position = iris_ProjMat * viewPos;",
                "gl_Position = iris_ProjMat * viewPos; modelSundialClip = gl_Position.xyz / gl_Position.w;");
        if (v == null) v = ModelPackLightAdapters.replaceUnique(vertex, "gl_Position = gl_ProjectionMatrix * viewPos;",
                "gl_Position = gl_ProjectionMatrix * viewPos; modelSundialClip = gl_Position.xyz / gl_Position.w;");
        if (v == null) return original;
        v = ModelPackLightAdapters.replaceUnique(v, "gl_Position.xy = gl_Position.xy + 0.5 + shadowOffset;",
                "modelSundialAtlasOffset = vec2(0.5) + shadowOffset;\ngl_Position.xy = gl_Position.xy + 0.5 + shadowOffset;");
        if (v == null) return original;
        v = beforeMain(v, "out vec3 modelSundialClip;\nout vec2 modelSundialAtlasOffset;\n");
        if (v == null) return original;
        StringBuilder declarations = new StringBuilder(), pass = new StringBuilder(), payload = new StringBuilder();
        StringBuilder fragmentDeclarations = new StringBuilder(), fragmentLocals = new StringBuilder(), interpolate = new StringBuilder();
        String f = fragment;
        for (Varying output : outputs) {
            String type = output.type, name = output.name;
            v = v.replaceAll("\\b" + name + "\\b", "modelSundialIn_" + name);
            declarations.append("in ").append(type).append(" modelSundialIn_").append(name).append("[];\n");
            var input = Pattern.compile("\\bin\\s+" + type + "\\s+" + name + "\\s*;");
            boolean consumed = input.matcher(f).find();
            String target = consumed ? "modelSundialIncoming_" + name : name;
            declarations.append("out ").append(type).append(' ').append(target).append(";\n");
            pass.append(target).append(" = modelSundialIn_").append(name).append("[i];\n");
            if (!consumed) continue;
            f = input.matcher(f).replaceFirst("in " + type + " " + target + ";");
            declarations.append("flat out ").append(type).append(" modelSundialValues_").append(name).append("[3];\n");
            fragmentDeclarations.append("flat in ").append(type).append(" modelSundialValues_").append(name).append("[3];\n");
            fragmentLocals.append(type).append(' ').append(name).append(" = ").append(target).append(";\n");
            payload.append("for (int j = 0; j < 3; ++j) modelSundialValues_").append(name)
                    .append("[j] = modelSundialIn_").append(name).append("[j];\n");
            interpolate.append(name).append(" = modelSundialValues_").append(name).append("[0] * modelBary.x + modelSundialValues_")
                    .append(name).append("[1] * modelBary.y + modelSundialValues_").append(name).append("[2] * modelBary.z;\n");
        }
        String g = ModelSundialShadowRaster.geometry(distortion, declarations.toString(), pass.toString(), payload.toString());
        fragmentDeclarations.append("""
                flat in vec3 modelSundialBaryA;
                flat in vec3 modelSundialBaryB;
                flat in vec3 modelSundialDepths;
                flat in vec2 modelSundialOffset;
                uniform int geometry_node_repeatShadowUv;
                """);
        String body = fragmentLocals + ModelSundialShadowRaster.fragmentStart(interpolate.toString());
        // Analytic model coverage discards outside the triangle before texture evaluation.
        // The pack's atlas-boundary derivative is only needed by its native raster path.
        f = f.replace("fwidth(shadowOffset.x)", "(geometry_node_repeatShadowUv == 0 ? fwidth(shadowOffset.x) : 0.0)");
        var main = MAIN.matcher(f);
        if (!main.find()) return original;
        f = f.substring(0, main.end()) + body + f.substring(main.end());
        f = ModelPackLightAdapters.replaceUnique(f, "shadowColor0 = albedo;",
                "if (geometry_node_repeatShadowUv != 0) gl_FragDepth = modelSundialDepth;\nshadowColor0 = albedo;");
        if (f == null) return original;
        f = beforeMain(f, fragmentDeclarations.toString());
        return f == null ? original : new Sources(v, g, f);
    }

    public static boolean patched(String geometry) {
        return geometry != null && geometry.contains("flat out vec3 modelSundialBaryA;");
    }

    private static String beforeMain(String source, String declaration) {
        var matcher = MAIN.matcher(source);
        if (!matcher.find()) return null;
        int at = matcher.start();
        if (matcher.find()) return null;
        return source.substring(0, at) + declaration + source.substring(at);
    }
    private ModelSundialShadowDepth() {}
}
