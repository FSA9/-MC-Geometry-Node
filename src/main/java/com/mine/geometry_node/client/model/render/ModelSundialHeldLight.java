package com.mine.geometry_node.client.model.render;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Narrow Sundial adapter for local light; sky and indirect radiance remain pack-owned. */
public final class ModelSundialHeldLight {
    public static final String SAMPLER = "geometry_node_lightBvh";
    public static final String COUNT = "geometry_node_lightCount";
    public static final String INVERSE = "geometry_node_lightInverse";
    public static final String RANGE = "geometry_node_lightRange";
    public static final int MAX_INSTANCES = 8;
    public static final int MAX_NODE_VISITS = 2048;
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\r\\n]*", Pattern.DOTALL);
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)\\s*\\{");
    private static final Pattern RULE = Pattern.compile("\\bgbufferData\\s*\\.\\s*lightmap\\s*\\.\\s*x\\s*=\\s*max\\s*\\([^;]*;");
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w.])(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?[fF]?(?![\\w.])");
    private static final String EXPECTED = canonical("gbufferData.lightmap.x = max(gbufferData.lightmap.x, heldBlockLightValue / 15.0 * clamp(1.0 - eyeRelatedDistance / 15.0, 0.0, 1.0));");

    public static String patch(String source) {
        if (source == null || source.contains(SAMPLER)) return source;
        Matcher comments = COMMENTS.matcher(source);
        StringBuilder clean = new StringBuilder();
        while (comments.find()) comments.appendReplacement(clean, " ".repeat(comments.end() - comments.start()));
        comments.appendTail(clean);
        Matcher main = MAIN.matcher(clean), rule = RULE.matcher(clean);
        if (!main.find()) return source;
        int mainStart = main.start(), mainEnd = main.end();
        if (main.find()) return source;
        int insertion = -1, ruleStart = -1;
        while (rule.find()) {
            if (!canonical(rule.group()).equals(EXPECTED)) continue;
            if (rule.start() < mainEnd) return source;
            // Composite5 has the same attenuation expression but waterWorldPos and different receiver semantics.
            if (!canonical(clean.substring(mainEnd, rule.start())).endsWith(
                    "vec3worldPos=viewToWorldPos(viewPos);floateyeRelatedDistance=length(worldPos+relativeEyePosition);")) continue;
            if (insertion >= 0) return source;
            insertion = clean.lastIndexOf(")", rule.end() - 1);
            ruleStart = rule.start();
        }
        if (insertion < 0) return source;
        String result = source.substring(0, insertion)
                + " * geometry_node_heldVisibility(worldPos, -relativeEyePosition)" + source.substring(insertion);
        result = result.substring(0, ruleStart)
                + "gbufferData.lightmap.x = geometry_node_placedLight(worldPos + mat3(gbufferModelViewInverse) * faceforward(gbufferData.geoNormal, viewPos, gbufferData.geoNormal) * 0.01, gbufferData.lightmap.x);\n"
                + "if (heldBlockLightValue / 15.0 * clamp(1.0 - eyeRelatedDistance / 15.0, 0.0, 1.0) > gbufferData.lightmap.x) "
                + result.substring(ruleStart);
        // Do not alter lightmap.y: a few fixed visibility rays turn diffuse skylight into
        // several fictitious directional lights, creating displaced hard shadows and self-shadow bands.
        // Sky occlusion requires a filtered hemispherical integral in the owning pack's lighting path.
        result = result.substring(0, mainStart) + declarations() + result.substring(mainStart);
        if (source.contains("screenSpaceVisibiliyBitmask")) {
            // Deferred12 uses a POM-shifted depth for screen-space effects. Geometry BVH/DDA
            // endpoints must instead use the actual rasterized surface. Keep the pack's own
            // viewPos intact; hand and LOD keep their special reconstruction paths.
            result = ModelPackLightAdapters.replaceUnique(result,
                    "vec3 worldPos = viewToWorldPos(viewPos);",
                    "vec3 geometry_node_surfaceView = viewPos;\n"
                    + "if (isOriginNotHand > 0.5 && gbufferData.depth >= 0.0) {\n"
                    + "    geometry_node_surfaceView = screenToViewPos(texcoord, textureLod(depthtex1, texcoord, 0.0).x);\n"
                    + "}\nvec3 worldPos = viewToWorldPos(geometry_node_surfaceView);");
            if (result == null) return source;
            // Ambient visibility is not another shadow test for the already ray-tested direct
            // term. Coverage fades to the original pack behavior outside ready local fields.
            result = ModelPackLightAdapters.replaceUnique(result,
                    "lightColor *= (1.0 - currData.w * (1.0 - 0.15 * blendWeight));",
                    "vec3 geometry_node_direct = blockLight * geometry_node_directCoverage;\n"
                    + "lightColor = (lightColor - geometry_node_direct) * (1.0 - currData.w * (1.0 - 0.15 * blendWeight)) + geometry_node_direct;");
            if (result == null) return source;
        }
        return result;
    }

    public static boolean patched(String source) {
        return source != null && source.contains("uniform sampler2D " + SAMPLER + ";")
                && source.contains(" * geometry_node_heldVisibility(worldPos, -relativeEyePosition)");
    }

    /** Shared data contract: three texels/node, then three texels/triangle; indices are local. */
    public static String declarations() {
        return """
                float geometry_node_worldVisibility(vec3 receiver, vec3 source);
                uniform sampler2D geometry_node_lightBvh;
                uniform vec4 geometry_node_heldSource;
                uniform int geometry_node_lightCount;
                uniform mat4 geometry_node_lightInverse[8];
                uniform ivec4 geometry_node_lightRange[8];
                vec4 geometry_node_lightFetch(int address) {
                    int width = textureSize(geometry_node_lightBvh, 0).x;
                    return texelFetch(geometry_node_lightBvh, ivec2(address % width, address / width), 0);
                }
                bool geometry_node_lightBox(vec3 origin, vec3 direction, vec3 lower, vec3 upper, float bias) {
                    float nearT = bias;
                    float farT = 1.0 - bias;
                    for (int axis = 0; axis < 3; ++axis) {
                        if (abs(direction[axis]) < 1e-12) {
                            if (origin[axis] < lower[axis] || origin[axis] > upper[axis]) return false;
                        } else {
                            float a = (lower[axis] - origin[axis]) / direction[axis];
                            float b = (upper[axis] - origin[axis]) / direction[axis];
                            nearT = max(nearT, min(a, b));
                            farT = min(farT, max(a, b));
                            if (nearT > farT) return false;
                        }
                    }
                    return true;
                }
                float geometry_node_lightTriangleHit(vec3 origin, vec3 direction, vec3 a, vec3 b, vec3 c, float bias) {
                    vec3 ab = b - a;
                    vec3 ac = c - a;
                    vec3 p = cross(direction, ac);
                    float determinant = dot(ab, p);
                    float scale = length(ab) * length(p);
                    if (abs(determinant) <= max(1e-30, scale * 1e-7)) return -1.0;
                    float inverseDeterminant = 1.0 / determinant;
                    vec3 s = origin - a;
                    float u = dot(s, p) * inverseDeterminant;
                    if (u < 0.0 || u > 1.0) return -1.0;
                    vec3 q = cross(s, ab);
                    float v = dot(direction, q) * inverseDeterminant;
                    if (v < 0.0 || u + v > 1.0) return -1.0;
                    float t = dot(ac, q) * inverseDeterminant;
                    return t > bias && t < 1.0 - bias ? t : -1.0;
                }
                float geometry_node_lightVisibility(vec3 receiver, vec3 source) {
                    float distanceToLight = length(receiver - source);
                    if (geometry_node_lightCount <= 0 || distanceToLight <= 0.004) return 1.0;
                    float bias = 0.002 / distanceToLight;
                    float transmission = 1.0;
                    for (int instance = 0; instance < 8; ++instance) {
                        if (instance >= geometry_node_lightCount) break;
                        ivec4 range = geometry_node_lightRange[instance];
                        if (range.y <= 0) continue;
                        vec3 origin = (geometry_node_lightInverse[instance] * vec4(source, 1.0)).xyz;
                        vec3 end = (geometry_node_lightInverse[instance] * vec4(receiver, 1.0)).xyz;
                        vec3 direction = end - origin;
                        int node = 0;
                        float hitDistances[16];
                        int hitCount = 0;
                        for (int visit = 0; visit < 2048; ++visit) {
                            if (node < 0 || node >= range.y) break;
                            int address = range.x + node * 3;
                            vec4 lower = geometry_node_lightFetch(address);
                            vec4 upper = geometry_node_lightFetch(address + 1);
                            if (!geometry_node_lightBox(origin, direction, lower.xyz, upper.xyz, bias)) {
                                node = int(lower.w);
                                continue;
                            }
                            int count = int(geometry_node_lightFetch(address + 2).x);
                            if (count > 0) {
                                int first = int(upper.w);
                                for (int triangle = 0; triangle < count; ++triangle) {
                                    int base = range.z + (first + triangle) * 3;
                                    vec4 vertex = geometry_node_lightFetch(base);
                                    float hit = geometry_node_lightTriangleHit(origin, direction, vertex.xyz,
                                            geometry_node_lightFetch(base + 1).xyz,
                                            geometry_node_lightFetch(base + 2).xyz, bias);
                                    if (hit >= 0.0) {
                                        float opacity = clamp(vertex.w, 0.0, 1.0);
                                        if (opacity >= 0.999) return 0.0;
                                        bool duplicate = false;
                                        for (int prior = 0; prior < 16; ++prior) {
                                            if (prior >= hitCount) break;
                                            if (abs(hit - hitDistances[prior]) <= bias * 0.05) duplicate = true;
                                        }
                                        if (!duplicate && hitCount < 16) {
                                            transmission *= 1.0 - opacity;
                                            hitDistances[hitCount++] = hit;
                                            if (transmission <= 0.001) return 0.0;
                                        }
                                    }
                                }
                                node = int(lower.w);
                            } else {
                                ++node;
                            }
                        }
                        // Traversal exhaustion is fail-open; runtime reports this fixed limit.
                    }
                    return transmission;
                }
                float geometry_node_mergedHeldLight(vec3 receiver, float blockLight) {
                    float strength = clamp((geometry_node_heldSource.w - length(receiver - geometry_node_heldSource.xyz)) / 15.0, 0.0, 1.0);
                    if (strength <= blockLight || geometry_node_lightCount <= 0) return blockLight;
                    if (geometry_node_worldVisibility(receiver, geometry_node_heldSource.xyz) <= 0.0) return blockLight;
                    return max(blockLight, strength * geometry_node_lightVisibility(receiver, geometry_node_heldSource.xyz));
                }
                float geometry_node_heldVisibility(vec3 receiver, vec3 source) {
                    if (geometry_node_worldVisibility(receiver, source) <= 0.0) return 0.0;
                    return geometry_node_lightVisibility(receiver, source);
                }
                """ + ModelSundialPlacedLight.declarations();
    }
    private static String canonical(String source) {
        Matcher numbers = NUMBER.matcher(source);
        StringBuilder result = new StringBuilder();
        while (numbers.find()) numbers.appendReplacement(result, Matcher.quoteReplacement(
                new BigDecimal(numbers.group().replaceAll("[fF]$", "")).stripTrailingZeros().toPlainString()));
        return numbers.appendTail(result).toString().replaceAll("\\s+", "");
    }
    private ModelSundialHeldLight() {}
}
