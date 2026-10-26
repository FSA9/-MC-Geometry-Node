package com.mine.geometry_node.client.runtime.model.render.shadow;

import java.util.regex.Pattern;
import java.math.BigDecimal;

/** Analytic triangle coverage and depth for SEUS E12's nonlinear radial shadow projection. */
public final class ModelSeusShadowDepth {
    public static final String VIEWPORT = "modelShadowViewport";
    public static final String TOLERANCE = "modelShadowTolerance";
    private static final String PLANE = "modelShadowPlane";
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{");
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w.])(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?[fF]?(?![\\w.])");
    private static final Pattern BIAS = Pattern.compile("m=sqrt\\(gl_Position\\.x\\*gl_Position\\.x\\+gl_Position\\.y\\*gl_Position\\.y\\),e=1-(SHADOW_MAP_BIAS|0(?:\\.\\d+)?)\\+m\\*\\1;");
    public record Sources(String geometry, String fragment) {}

    public static Sources patch(String vertex, String geometry, String fragment) {
        var original = new Sources(geometry, fragment);
        if (vertex == null || geometry == null || fragment == null || patched(geometry)
                || !ModelSeusShadowVoxel.patched(geometry) || fragment.contains("gl_FragDepth")
                || !compact(vertex).contains("gl_Position.xy*=0.95/e;gl_Position.xy*=0.5;gl_Position.xy+=0.5;")
                || !compact(vertex).contains("gl_Position.z=mix(gl_Position.z,0.5,0.8);")
                || !compact(geometry).contains("viewPos=vViewPos[i];")
                || !compact(fragment).contains("if(PnlUBUYgWr<0.5)")) return original;
        var bias = BIAS.matcher(compact(vertex));
        if (!bias.find()) return original;
        String biasExpression = bias.group(1);
        if (bias.find()) return original;
        if (biasExpression.equals("SHADOW_MAP_BIAS")) {
            var definition = Pattern.compile("(?m)^\\s*#define\\s+SHADOW_MAP_BIAS\\s+(0\\.\\d+)\\s*$").matcher(vertex);
            if (!definition.find()) return original;
            biasExpression = definition.group(1);
            if (definition.find()) return original;
        }
        String projection = compact(geometry).contains("uniformmat4shadowProjection;") ? "" : "uniform mat4 shadowProjection;\n";
        if (!compact(geometry).contains("texcoord=vTexcoord[i];")
                || !compact(geometry).contains("color=vColor[i];")) return original;
        int uvComponents = ModelSeusShadowPayload.components(fragment, "texcoord", 2);
        int viewComponents = ModelSeusShadowPayload.components(fragment, "viewPos", 3);
        String g = inject(geometry, projection + "flat out vec4 " + PLANE + ";\n" + """
                flat out vec3 modelShadowBaryA;
                flat out vec3 modelShadowBaryB;
                flat out vec4 modelShadowUv[3];
                flat out vec4 modelShadowColor[3];
                flat out vec4 modelShadowView[3];
                uniform vec4 modelShadowViewport;
                uniform float modelShadowTolerance;
                vec2 modelShadowWarp(vec2 p) {
                    return p * (0.475 / (1.0 - SHADOW_MAP_BIAS + SHADOW_MAP_BIAS * length(p))) + 0.5;
                }
                vec4 modelShadowBounds(vec2 lo, vec2 hi) {
                    vec2 nearAxis = clamp(vec2(0.0), lo, hi);
                    vec2 farAxis = max(abs(lo), abs(hi));
                    return vec4(
                        modelShadowWarp(vec2(lo.x, lo.x < 0.0 ? nearAxis.y : farAxis.y)).x,
                        modelShadowWarp(vec2(lo.y < 0.0 ? nearAxis.x : farAxis.x, lo.y)).y,
                        modelShadowWarp(vec2(hi.x, hi.x > 0.0 ? nearAxis.y : farAxis.y)).x,
                        modelShadowWarp(vec2(hi.y > 0.0 ? nearAxis.x : farAxis.x, hi.y)).y);
                }
                """.replace("SHADOW_MAP_BIAS", biasExpression), """
                if (geometry_node_repeatShadowUv != 0) {
                vec4 modelClipA = shadowProjection * vViewPos[0];
                vec4 modelClipB = shadowProjection * vViewPos[1];
                vec4 modelClipC = shadowProjection * vViewPos[2];
                vec3 modelA = modelClipA.xyz / modelClipA.w;
                vec3 modelB = modelClipB.xyz / modelClipB.w;
                vec3 modelC = modelClipC.xyz / modelClipC.w;
                vec3 modelNormal = cross(modelB - modelA, modelC - modelA);
                if (abs(modelNormal.z) < 1e-20) return;
                vec3 modelBaryA = vec3(modelB.y - modelC.y, modelC.x - modelB.x,
                    modelB.x * modelC.y - modelC.x * modelB.y) / modelNormal.z;
                vec3 modelBaryB = vec3(modelC.y - modelA.y, modelA.x - modelC.x,
                    modelC.x * modelA.y - modelA.x * modelC.y) / modelNormal.z;
                vec2 modelLo = min(modelA.xy, min(modelB.xy, modelC.xy));
                vec2 modelHi = max(modelA.xy, max(modelB.xy, modelC.xy));
                vec2 modelNearAxis = clamp(vec2(0.0), modelLo, modelHi);
                vec2 modelScreenA = vec2(0.0), modelScreenB = vec2(0.0), modelScreenC = vec2(0.0);
                bool modelFast = false;
                if (modelShadowTolerance > 0.0) {
                    modelScreenA = modelShadowWarp(modelA.xy);
                    modelScreenB = modelShadowWarp(modelB.xy);
                    modelScreenC = modelShadowWarp(modelC.xy);
                    vec2 modelAB = modelB.xy - modelA.xy, modelAC = modelC.xy - modelA.xy;
                    vec2 modelBC = modelC.xy - modelB.xy;
                    float modelEdgeSquared = max(dot(modelAB, modelAB), max(dot(modelAC, modelAC), dot(modelBC, modelBC)));
                    float modelDivisor = 1.0 - SHADOW_MAP_BIAS + SHADOW_MAP_BIAS * length(modelNearAxis);
                    // ||D2 warp|| <= 5*c*b/d^2; weighted vertex variance <= maxEdge^2/3.
                    // This bounds the entire triangle, including curved edges, in shadow texels.
                    float modelError = (5.0 * 0.475 * SHADOW_MAP_BIAS / 12.0) * modelEdgeSquared
                        * max(modelShadowViewport.z, modelShadowViewport.w) / (modelDivisor * modelDivisor);
                    vec2 modelScreenAB = modelScreenB - modelScreenA, modelScreenAC = modelScreenC - modelScreenA;
                    float modelScreenArea = modelScreenAB.x * modelScreenAC.y - modelScreenAB.y * modelScreenAC.x;
                    modelFast = modelError <= modelShadowTolerance && modelScreenArea * modelNormal.z > 0.0;
                }
                vec2 modelBoundsLo = vec2(1e30), modelBoundsHi = vec2(-1e30);
                vec2 modelAxis = vec2(1.0, 0.0), modelSide = vec2(0.0, 1.0);
                bool modelOriented = false;
                if (!modelFast) {
                vec4 modelBox = modelShadowBounds(modelLo, modelHi);
                modelBoundsLo = max(modelBox.xy, vec2(-1.0));
                modelBoundsHi = min(modelBox.zw, vec2(1.0));
                if (any(greaterThanEqual(modelBoundsLo, modelBoundsHi))) return;
                vec2 modelExtent = modelBoundsHi - modelBoundsLo;
                vec2 modelSourceExtent = modelHi - modelLo;
                float modelBoxArea = modelExtent.x * modelExtent.y;
                // Additional geometry work is only worthwhile for large, sparse footprints.
                if (modelBoxArea * modelShadowViewport.z * modelShadowViewport.w > 1024.0
                        && abs(modelNormal.z) < 0.5 * modelSourceExtent.x * modelSourceExtent.y) {
                    vec2 modelEdge = modelB.xy - modelA.xy;
                    vec2 modelAC = modelC.xy - modelA.xy, modelBC = modelC.xy - modelB.xy;
                    if (dot(modelAC, modelAC) > dot(modelEdge, modelEdge)) modelEdge = modelAC;
                    if (dot(modelBC, modelBC) > dot(modelEdge, modelEdge)) modelEdge = modelBC;
                    float modelEdgeLength = length(modelEdge);
                    if (modelEdgeLength > 1e-10 && modelEdgeLength < 1e10) {
                        modelAxis = modelEdge / modelEdgeLength;
                        modelSide = vec2(-modelAxis.y, modelAxis.x);
                        vec2 modelRotA = vec2(dot(modelAxis, modelA.xy), dot(modelSide, modelA.xy));
                        vec2 modelRotB = vec2(dot(modelAxis, modelB.xy), dot(modelSide, modelB.xy));
                        vec2 modelRotC = vec2(dot(modelAxis, modelC.xy), dot(modelSide, modelC.xy));
                        vec2 modelRotLo = min(modelRotA, min(modelRotB, modelRotC));
                        vec2 modelRotHi = max(modelRotA, max(modelRotB, modelRotC));
                        // Include accepted negative barycentrics and float rotation error.
                        vec2 modelPad = 3e-5 * (modelRotHi - modelRotLo)
                            + 4e-6 * max(vec2(1.0), max(abs(modelRotLo), abs(modelRotHi)));
                        // Radial warp commutes with rotation about its origin, before +0.5.
                        vec4 modelRotBox = modelShadowBounds(modelRotLo - modelPad, modelRotHi + modelPad);
                        float modelPixelPad = max(4e-6, 2.0 / min(modelShadowViewport.z, modelShadowViewport.w));
                        modelRotBox.xy -= vec2(modelPixelPad);
                        modelRotBox.zw += vec2(modelPixelPad);
                        vec2 modelRotExtent = modelRotBox.zw - modelRotBox.xy;
                        if (all(greaterThan(modelRotExtent, vec2(0.0)))
                                && modelRotExtent.x * modelRotExtent.y < 0.75 * modelBoxArea) {
                            modelBoundsLo = modelRotBox.xy;
                            modelBoundsHi = modelRotBox.zw;
                            modelOriented = true;
                        }
                    }
                }
                }
                vec4 modelPlane = vec4(modelNormal.xy, -dot(modelNormal, modelA), 1.0 / modelNormal.z);
                vec4 modelUvDeltaA = vTexcoord[0] - vTexcoord[2];
                vec4 modelUvDeltaB = vTexcoord[1] - vTexcoord[2];
                vec4 modelColorDeltaA = vColor[0] - vColor[2];
                vec4 modelColorDeltaB = vColor[1] - vColor[2];
                vec4 modelViewDeltaA = vViewPos[0] - vViewPos[2];
                vec4 modelViewDeltaB = vViewPos[1] - vViewPos[2];
                for (int modelCorner = 0; modelCorner < (modelFast ? 3 : 4); ++modelCorner) {
                    int modelIndex = modelNormal.z > 0.0 ? modelCorner
                        : (modelCorner == 1 ? 2 : (modelCorner == 2 ? 1 : modelCorner));
                    vec2 modelPosition = modelFast
                        ? (modelCorner == 0 ? modelScreenA : (modelCorner == 1 ? modelScreenB : modelScreenC))
                        : vec2((modelIndex & 1) == 0 ? modelBoundsLo.x : modelBoundsHi.x,
                            (modelIndex & 2) == 0 ? modelBoundsLo.y : modelBoundsHi.y);
                    if (modelOriented) modelPosition = vec2(0.5)
                        + modelAxis * (modelPosition.x - 0.5) + modelSide * (modelPosition.y - 0.5);
                    gl_Position = vec4(modelPosition, 0.4, 1.0);
                    modelShadowPlane = modelPlane;
                    modelShadowBaryA = modelBaryA;
                    modelShadowBaryB = modelBaryB;
                    for (int modelAttribute = 0; modelAttribute < 3; ++modelAttribute) {
                        modelShadowUv[modelAttribute] = modelAttribute == 2 ? vTexcoord[2] : (modelAttribute == 0 ? modelUvDeltaA : modelUvDeltaB);
                        modelShadowColor[modelAttribute] = modelAttribute == 2 ? vColor[2] : (modelAttribute == 0 ? modelColorDeltaA : modelColorDeltaB);
                        modelShadowView[modelAttribute] = modelAttribute == 2 ? vViewPos[2] : (modelAttribute == 0 ? modelViewDeltaA : modelViewDeltaB);
                    }
                    color = vColor[0]; texcoord = vTexcoord[0]; viewPos = vViewPos[0];
                    materialIDs = vMaterialIDs[0]; xnnPLOZALC = KWQDGvXhoA[0];
                    fragDepth = fdUKYBKbny[0]; mcEntity = vMCEntity[0]; PnlUBUYgWr = 0.0;
                    MODEL_OPTIONAL_OUTPUTS
                    EmitVertex();
                }
                EndPrimitive();
                return;
                }
                """.replace("MODEL_OPTIONAL_OUTPUTS", optionalOutputs(geometry)).replace("SHADOW_MAP_BIAS", biasExpression));
        if (g == null) return original;
        String fragmentBody = renameMainInputs(fragment);
        if (fragmentBody == null) return original;
        String f = inject(fragmentBody, "flat in vec4 " + PLANE + ";\nuniform vec4 " + VIEWPORT
                + ";\nuniform int " + ModelSundialShadowUv.UNIFORM + ";\n" + """
                flat in vec3 modelShadowBaryA;
                flat in vec3 modelShadowBaryB;
                flat in vec4 modelShadowUv[3];
                flat in vec4 modelShadowColor[3];
                flat in vec4 modelShadowView[3];
                """, """
                vec4 modelSampleUv = texcoord;
                vec4 modelSampleColor = color;
                vec4 modelSampleView = viewPos;
                gl_FragDepth = gl_FragCoord.z;
                if (geometry_node_repeatShadowUv != 0 && PnlUBUYgWr < 0.5) {
                    vec2 modelNdc = (gl_FragCoord.xy - modelShadowViewport.xy) / modelShadowViewport.zw * 2.0 - 1.0;
                    vec2 modelWarped = (modelNdc - 0.5) * (2.0 / 0.95);
                    float modelDenominator = 1.0 - SHADOW_MAP_BIAS * length(modelWarped);
                    if (modelDenominator > 1e-6) {
                        vec2 modelProjected = modelWarped * ((1.0 - SHADOW_MAP_BIAS) / modelDenominator);
                        vec3 modelBary;
                        modelBary.xy = vec2(dot(modelShadowBaryA, vec3(modelProjected, 1.0)),
                            dot(modelShadowBaryB, vec3(modelProjected, 1.0)));
                        modelBary.z = 1.0 - modelBary.x - modelBary.y;
                        if (any(lessThan(modelBary, vec3(-1e-5)))) discard;
                        float modelZ = -(dot(modelShadowPlane.xy, modelProjected) + modelShadowPlane.z) * modelShadowPlane.w;
                        float modelDepth = modelZ * 0.1 + 0.7;
                        if (modelDepth < 0.0 || modelDepth > 1.0) discard;
                        gl_FragDepth = modelDepth;
                        modelSampleUv = modelShadowUv[2] + modelShadowUv[0] * modelBary.x + modelShadowUv[1] * modelBary.y;
                        modelSampleColor = modelShadowColor[2] + modelShadowColor[0] * modelBary.x + modelShadowColor[1] * modelBary.y;
                        modelSampleView = modelShadowView[2] + modelShadowView[0] * modelBary.x + modelShadowView[1] * modelBary.y;
                    } else discard;
                }
                """.replace("SHADOW_MAP_BIAS", biasExpression));
        if (f == null) return original;
        g = narrowPayload(g, "Uv", "vTexcoord", "modelSampleUv", uvComponents);
        f = narrowPayload(f, "Uv", "vTexcoord", "modelSampleUv", uvComponents);
        g = narrowPayload(g, "View", "vViewPos", "modelSampleView", viewComponents);
        f = narrowPayload(f, "View", "vViewPos", "modelSampleView", viewComponents);
        return new Sources(g, f);
    }

    private static String narrowPayload(String source, String attribute, String input, String sample, int components) {
        if (components == 4) return source;
        String suffix = components == 2 ? ".xy" : ".xyz";
        return source.replace("vec4 modelShadow" + attribute + "[3];", "vec" + components + " modelShadow" + attribute + "[3];")
                .replace("vec4 model" + attribute + "DeltaA = " + input + "[0] - " + input + "[2];",
                        "vec" + components + " model" + attribute + "DeltaA = " + input + "[0]" + suffix + " - " + input + "[2]" + suffix + ";")
                .replace("vec4 model" + attribute + "DeltaB = " + input + "[1] - " + input + "[2];",
                        "vec" + components + " model" + attribute + "DeltaB = " + input + "[1]" + suffix + " - " + input + "[2]" + suffix + ";")
                .replace("modelShadow" + attribute + "[modelAttribute] = modelAttribute == 2 ? " + input
                                + "[2] : ",
                        "modelShadow" + attribute + "[modelAttribute] = modelAttribute == 2 ? " + input
                                + "[2]" + suffix + " : ")
                .replace(sample + " = modelShadow", sample + suffix + " = modelShadow");
    }

    public static boolean patched(String geometry) {
        return geometry != null && geometry.contains("flat out vec4 " + PLANE + ";");
    }

    private static String renameMainInputs(String source) {
        var main = MAIN.matcher(source);
        if (!main.find()) return null;
        int end = main.end();
        if (main.find()) return null;
        // SEUS puts main last, including after Iris preprocessing. Only its reads
        // are replaced: shader inputs remain available to initialize vanilla draws.
        String body = source.substring(end).replaceAll("\\btexcoord\\b", "modelSampleUv")
                .replaceAll("\\bcolor\\b", "modelSampleColor")
                .replaceAll("\\bviewPos\\b", "modelSampleView");
        return source.substring(0, end) + body;
    }

    private static String optionalOutputs(String geometry) {
        StringBuilder result = new StringBuilder();
        for (String name : new String[]{"isWater", "isStainedGlass", "invalidForVolume"}) {
            if (Pattern.compile("(?m)^\\s*out\\s+float\\s+" + name + "\\s*;").matcher(geometry).find()) {
                result.append(name).append(" = 0.0;\n");
            }
        }
        if (Pattern.compile("(?m)^\\s*out\\s+vec3\\s+voxelSpacePos\\s*;").matcher(geometry).find()) {
            result.append("voxelSpacePos = vec3(0.0);\n");
        }
        return result.toString();
    }

    private static String inject(String source, String declarations, String body) {
        var main = MAIN.matcher(source);
        if (!main.find()) return null;
        int start = main.start(), end = main.end();
        if (main.find()) return null;
        return source.substring(0, start) + declarations + source.substring(start, end) + "\n" + body + source.substring(end);
    }

    private static String compact(String source) {
        return NUMBER.matcher(source).replaceAll(m -> new BigDecimal(m.group().replaceAll("[fF]$", ""))
                .stripTrailingZeros().toPlainString()).replaceAll("\\s+", "");
    }
    private ModelSeusShadowDepth() {}
}
