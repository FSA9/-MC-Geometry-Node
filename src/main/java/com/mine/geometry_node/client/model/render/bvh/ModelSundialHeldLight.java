package com.mine.geometry_node.client.model.render.bvh;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Narrow Sundial adapter for local light; sky and indirect radiance remain pack-owned. */
public final class ModelSundialHeldLight {
    public static final String SAMPLER = "geometry_node_lightBvh";
    public static final String COUNT = "geometry_node_lightCount";
    public static final String INVERSE = "geometry_node_lightInverse";
    public static final String RANGE = "geometry_node_lightRange";
    public static final String REVISION = "geometry_node_bvhRevision";
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

    /** Shared data contract: two texels/node, then three texels/triangle; indices are local. */
    public static String declarations() {
        return declarations(true);
    }

    /** Single-segment adapters have no repeated queries to amortize invocation-local caches. */
    static String declarations(boolean reuseQueries) {
        return """
                float geometry_node_worldVisibility(vec3 receiver, vec3 source);
                uniform sampler2D geometry_node_lightBvh;
                uniform vec4 geometry_node_heldSource;
                uniform int geometry_node_lightCount;
                uniform int geometry_node_bvhEnabled;
                uniform int geometry_node_bvhRevision;
                uniform int geometry_node_heldEnabled;
                uniform int geometry_node_primaryGiEnabled;
                uniform int geometry_node_secondaryGiEnabled;
                uniform int geometry_node_heldOcclusionEnabled;
                uniform mat4 geometry_node_lightInverse[8];
                // Exact local-space root bounds, uploaded alongside each instance range.
                // This is a conservative coarse test: a miss cannot contain an occluding triangle.
                uniform vec4 geometry_node_lightBoundsMin[8];
                uniform vec4 geometry_node_lightBoundsMax[8];
                // Camera-relative world-space bounds. Test these before transforming the ray
                // into instance-local space so unrelated instances avoid inverse-matrix work.
                uniform vec4 geometry_node_lightWorldBoundsMin[8];
                uniform vec4 geometry_node_lightWorldBoundsMax[8];
                uniform ivec4 geometry_node_lightRange[8];
                """ + ModelBvhAutoQuality.declarations() + """
                // ModelIrisHeldLight allocates a fixed 2048-wide atlas.  Keeping the width
                // compile-time constant avoids textureSize, integer division and remainder in
                // every node/triangle fetch.  Addresses are non-negative atlas texel indices.
                const int geometry_node_lightAtlasWidth = 2048;
                const int geometry_node_lightAtlasShift = 11;
                vec4 geometry_node_lightFetch(int address) {
                    return texelFetch(geometry_node_lightBvh,
                            ivec2(address & (geometry_node_lightAtlasWidth - 1),
                                  address >> geometry_node_lightAtlasShift), 0);
                }
                float geometry_node_lightBoxEntry(vec3 origin, vec3 direction, vec3 inverseDirection, vec3 lower, vec3 upper, float bias) {
                    bvec3 parallel = lessThan(abs(direction), vec3(1e-12));
                    bvec3 outside = bvec3(origin.x < lower.x || origin.x > upper.x,
                                         origin.y < lower.y || origin.y > upper.y,
                                         origin.z < lower.z || origin.z > upper.z);
                    if (any(bvec3(parallel.x && outside.x, parallel.y && outside.y, parallel.z && outside.z))) return -1.0;
                    vec3 a = (lower - origin) * inverseDirection;
                    vec3 b = (upper - origin) * inverseDirection;
                    vec3 nearAxis = mix(min(a, b), vec3(bias), parallel);
                    vec3 farAxis = mix(max(a, b), vec3(1.0 - bias), parallel);
                    float nearT = max(bias, max(nearAxis.x, max(nearAxis.y, nearAxis.z)));
                    float farT = min(1.0 - bias, min(farAxis.x, min(farAxis.y, farAxis.z)));
                    return nearT <= farT ? nearT : -1.0;
                }
                bool geometry_node_lightBox(vec3 origin, vec3 direction, vec3 inverseDirection, vec3 lower, vec3 upper, float bias) {
                    return geometry_node_lightBoxEntry(origin, direction, inverseDirection, lower, upper, bias) >= 0.0;
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
                // OPAQUE/MASK scenes need only an any-hit result: no transmission array or duplicate-hit loop.
                // The caller tests the root bounds; every queued child has already passed its box test.
                bool geometry_node_opaqueBlocked(vec3 origin, vec3 direction, vec3 inverseDirection, ivec4 range, float bias) {
                    int node = 0;
                    int pending[GEOMETRY_NODE_STACK_CAPACITY];
                    int pendingCount = 0;
                    // Each node is visited at most once; a dynamic bound avoids driver loop expansion.
                    int visitLimit = min(range.y, 2048);
                    for (int visit = 0; visit < visitLimit; ++visit) {
                        if (node < 0) {
                            if (pendingCount <= 0) break;
                            node = pending[--pendingCount];
                        }
                        if (node >= range.y) break;
                        int address = range.x + node * 2;
                        vec4 upper = geometry_node_lightFetch(address + 1);
                        int metadata = floatBitsToInt(upper.w) & 0x0fffffff;
                        int count = metadata & 15;
                        if (count > 0) {
                            int first = metadata >> 4;
                            for (int triangle = 0; triangle < count; ++triangle) {
                                int base = range.z + (first + triangle) * 3;
                                if (geometry_node_lightTriangleHit(origin, direction,
                                        geometry_node_lightFetch(base).xyz,
                                        geometry_node_lightFetch(base + 1).xyz,
                                        geometry_node_lightFetch(base + 2).xyz, bias) >= 0.0) return true;
                            }
                            node = -1;
                            continue;
                        }
                        int left = node + 1;
                        int right = metadata >> 4;
                        bool leftHit = left >= 0 && left < range.y;
                        bool rightHit = right >= 0 && right < range.y;
                        float leftOrder = 0.0;
                        float rightOrder = 0.0;
                        if (leftHit) {
                            int childAddress = range.x + left * 2;
                            vec4 childLower = geometry_node_lightFetch(childAddress);
                            vec4 childUpper = geometry_node_lightFetch(childAddress + 1);
                            leftOrder = geometry_node_lightBoxEntry(origin, direction, inverseDirection,
                                    childLower.xyz, childUpper.xyz, bias);
                            leftHit = leftOrder >= 0.0;
                        }
                        if (rightHit) {
                            int childAddress = range.x + right * 2;
                            vec4 childLower = geometry_node_lightFetch(childAddress);
                            vec4 childUpper = geometry_node_lightFetch(childAddress + 1);
                            rightOrder = geometry_node_lightBoxEntry(origin, direction, inverseDirection,
                                    childLower.xyz, childUpper.xyz, bias);
                            rightHit = rightOrder >= 0.0;
                        }
                        if (leftHit && rightHit) {
                            int nearChild = leftOrder <= rightOrder ? left : right;
                            int farChild = leftOrder <= rightOrder ? right : left;
                            pending[pendingCount++] = farChild;
                            node = nearChild;
                        } else if (leftHit) {
                            node = left;
                        } else if (rightHit) {
                            node = right;
                        } else {
                            node = -1;
                        }
                    }
                    return false;
                }
                """.replace("GEOMETRY_NODE_STACK_CAPACITY", Integer.toString(ModelLightOcclusionBvh.MAX_TRAVERSAL_STACK)) + (reuseQueries ? """
                // The same receiver is tested against several held/placed/dynamic sources in
                // one fragment.  Transforming it once per source multiplied the dominant
                // mat4 cost by the number of lights.  This cache is invocation-local and exact:
                // a new receiver value replaces the previous entry, with no cross-pixel reuse.
                bool geometry_node_receiverLocalValid = false;
                vec3 geometry_node_receiverLocalKey;
                vec3 geometry_node_receiverLocal[8];
                bool geometry_node_receiverLocalReady[8];
                bool geometry_node_sourceLocalValid = false;
                vec3 geometry_node_sourceLocalKey;
                vec3 geometry_node_sourceLocal[8];
                bool geometry_node_sourceLocalReady[8];
                void geometry_node_prepareReceiver(vec3 receiver) {
                    if (geometry_node_receiverLocalValid
                            && all(equal(geometry_node_receiverLocalKey, receiver))) return;
                    geometry_node_receiverLocalKey = receiver;
                    for (int instance = 0; instance < 8; ++instance) {
                        geometry_node_receiverLocalReady[instance] = false;
                    }
                    geometry_node_receiverLocalValid = true;
                }
                vec3 geometry_node_receiverLocalAt(vec3 receiver, int instance) {
                    geometry_node_prepareReceiver(receiver);
                    if (!geometry_node_receiverLocalReady[instance]) {
                        geometry_node_receiverLocal[instance] =
                                (geometry_node_lightInverse[instance] * vec4(receiver, 1.0)).xyz;
                        geometry_node_receiverLocalReady[instance] = true;
                    }
                    return geometry_node_receiverLocal[instance];
                }
                void geometry_node_prepareSource(vec3 source) {
                    if (geometry_node_sourceLocalValid
                            && all(equal(geometry_node_sourceLocalKey, source))) return;
                    geometry_node_sourceLocalKey = source;
                    for (int instance = 0; instance < 8; ++instance) {
                        geometry_node_sourceLocalReady[instance] = false;
                    }
                    geometry_node_sourceLocalValid = true;
                }
                vec3 geometry_node_sourceLocalAt(vec3 source, int instance) {
                    geometry_node_prepareSource(source);
                    if (!geometry_node_sourceLocalReady[instance]) {
                        geometry_node_sourceLocal[instance] =
                                (geometry_node_lightInverse[instance] * vec4(source, 1.0)).xyz;
                        geometry_node_sourceLocalReady[instance] = true;
                    }
                    return geometry_node_sourceLocal[instance];
                }
                """ : "") + """
                float geometry_node_lightVisibilityUncached(vec3 receiver, vec3 source) {
                    float distanceToLight = length(receiver - source);
                    if (geometry_node_bvhEnabled == 0 || geometry_node_lightCount <= 0 || distanceToLight <= 0.004) return 1.0;
                    float bias = 0.002 / distanceToLight;
                    vec3 worldDirection = receiver - source;
                    vec3 worldInverseDirection = 1.0 / mix(worldDirection, vec3(1.0), lessThan(abs(worldDirection), vec3(1e-12)));
                    float transmission = 1.0;
                    for (int instance = 0; instance < min(geometry_node_lightCount, 8); ++instance) {
                        ivec4 range = geometry_node_lightRange[instance];
                        if (range.y <= 0) continue;
                        if (!geometry_node_lightBox(source, worldDirection, worldInverseDirection,
                                geometry_node_lightWorldBoundsMin[instance].xyz,
                                geometry_node_lightWorldBoundsMax[instance].xyz, bias)) continue;
                """ + (reuseQueries ? """
                        vec3 origin = geometry_node_sourceLocalAt(source, instance);
                        vec3 end = geometry_node_receiverLocalAt(receiver, instance);
                """ : """
                        vec3 origin = (geometry_node_lightInverse[instance] * vec4(source, 1.0)).xyz;
                        vec3 end = (geometry_node_lightInverse[instance] * vec4(receiver, 1.0)).xyz;
                """) + """
                        vec3 direction = end - origin;
                        vec3 inverseDirection = 1.0 / mix(direction, vec3(1.0), lessThan(abs(direction), vec3(1e-12)));
                        // Opaque traversal trusts this root test and tests each child before queuing it.
                        if (!geometry_node_lightBox(origin, direction, inverseDirection,
                                geometry_node_lightBoundsMin[instance].xyz,
                                geometry_node_lightBoundsMax[instance].xyz, bias)) continue;
                        if (range.w != 0) {
                            if (geometry_node_opaqueBlocked(origin, direction, inverseDirection, range, bias)) return 0.0;
                            continue;
                        }
                        int node = 0;
                        float hitDistances[16];
                        int hitCount = 0;
                        int visitLimit = min(range.y, 2048);
                        for (int visit = 0; visit < visitLimit; ++visit) {
                            if (node < 0 || node >= range.y) break;
                            int address = range.x + node * 2;
                            vec4 lower = geometry_node_lightFetch(address);
                            vec4 upper = geometry_node_lightFetch(address + 1);
                            if (!geometry_node_lightBox(origin, direction, inverseDirection, lower.xyz, upper.xyz, bias)) {
                                node = int(lower.w);
                                continue;
                            }
                            int metadata = floatBitsToInt(upper.w) & 0x0fffffff;
                            int count = metadata & 15;
                            if (count > 0) {
                                int first = metadata >> 4;
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
                """ + (reuseQueries ? """
                // Exact same-invocation cache. Fragment invocations are isolated, so this never
                // approximates across pixels or frames; the revision/count key prevents reuse if
                // the bound model atlas or active instance set changes during an invocation.
                const int geometry_node_queryCacheSize = 4;
                bool geometry_node_queryCacheInitialized = false;
                bool geometry_node_queryCacheValid[geometry_node_queryCacheSize];
                vec3 geometry_node_queryCacheReceiver[geometry_node_queryCacheSize];
                vec3 geometry_node_queryCacheSource[geometry_node_queryCacheSize];
                float geometry_node_queryCacheResult[geometry_node_queryCacheSize];
                int geometry_node_queryCacheRevision = -1;
                int geometry_node_queryCacheCount = -1;
                int geometry_node_queryCacheNext = 0;
                void geometry_node_queryCacheReset() {
                    for (int slot = 0; slot < geometry_node_queryCacheSize; ++slot) {
                        geometry_node_queryCacheValid[slot] = false;
                    }
                    geometry_node_queryCacheRevision = geometry_node_bvhRevision;
                    geometry_node_queryCacheCount = geometry_node_lightCount;
                    geometry_node_queryCacheNext = 0;
                    geometry_node_queryCacheInitialized = true;
                }
                float geometry_node_lightVisibility(vec3 receiver, vec3 source) {
                    float distanceToLight = length(receiver - source);
                    if (geometry_node_bvhEnabled == 0 || geometry_node_lightCount <= 0 || distanceToLight <= 0.004) return 1.0;
                    if (!geometry_node_queryCacheInitialized
                            || geometry_node_queryCacheRevision != geometry_node_bvhRevision
                            || geometry_node_queryCacheCount != geometry_node_lightCount) {
                        geometry_node_queryCacheReset();
                    }
                    for (int slot = 0; slot < geometry_node_queryCacheSize; ++slot) {
                        if (geometry_node_queryCacheValid[slot]
                                && geometry_node_queryCacheReceiver[slot].x == receiver.x
                                && geometry_node_queryCacheReceiver[slot].y == receiver.y
                                && geometry_node_queryCacheReceiver[slot].z == receiver.z
                                && geometry_node_queryCacheSource[slot].x == source.x
                                && geometry_node_queryCacheSource[slot].y == source.y
                                && geometry_node_queryCacheSource[slot].z == source.z) {
                            return geometry_node_queryCacheResult[slot];
                        }
                    }
                    float result = geometry_node_lightVisibilityUncached(receiver, source);
                    int slot = geometry_node_queryCacheNext;
                    geometry_node_queryCacheNext = (geometry_node_queryCacheNext + 1) % geometry_node_queryCacheSize;
                    geometry_node_queryCacheValid[slot] = true;
                    geometry_node_queryCacheReceiver[slot] = receiver;
                    geometry_node_queryCacheSource[slot] = source;
                    geometry_node_queryCacheResult[slot] = result;
                    return result;
                }
                """ : """
                float geometry_node_lightVisibility(vec3 receiver, vec3 source) {
                    return geometry_node_lightVisibilityUncached(receiver, source);
                }
                """) + """
                float geometry_node_primaryGiVisibility(vec3 receiver, vec3 source) {
                    if (geometry_node_primaryGiEnabled == 0 || !bvhGiReceiverEnabled(receiver)) return 1.0;
                    return geometry_node_lightVisibility(receiver, source);
                }
                float geometry_node_secondaryGiVisibility(vec3 receiver, vec3 source) {
                    // Shared world-space irradiance probes can feed nearby receivers. Do not
                    // distance-cull their transport using the probe position.
                    if (geometry_node_secondaryGiEnabled == 0) return 1.0;
                    return geometry_node_lightVisibility(receiver, source);
                }
                float geometry_node_mergedHeldLight(vec3 receiver, float blockLight) {
                    float strength = clamp((geometry_node_heldSource.w - length(receiver - geometry_node_heldSource.xyz)) / 15.0, 0.0, 1.0);
                    if (geometry_node_heldEnabled == 0 || strength <= blockLight || geometry_node_lightCount <= 0
                            || !bvhReceiverEnabled(receiver)) return blockLight;
                    if (geometry_node_worldVisibility(receiver, geometry_node_heldSource.xyz) <= 0.0) return blockLight;
                    float visibility = 1.0;
                    if (geometry_node_heldOcclusionEnabled != 0) visibility = geometry_node_lightVisibility(receiver, geometry_node_heldSource.xyz);
                    return max(blockLight, strength * visibility);
                }
                float geometry_node_heldVisibility(vec3 receiver, vec3 source) {
                    // A pack may keep this hook in its shader while the custom held-light
                    // module is disabled.  Preserve pack-owned held-light behavior otherwise.
                    if (geometry_node_heldEnabled == 0 || !bvhReceiverEnabled(receiver)) return 1.0;
                    if (geometry_node_worldVisibility(receiver, source) <= 0.0) return 0.0;
                    if (geometry_node_heldOcclusionEnabled == 0) return 1.0;
                    return geometry_node_lightVisibility(receiver, source);
                }
                """ + ModelSundialPlacedLight.declarations(reuseQueries);
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
