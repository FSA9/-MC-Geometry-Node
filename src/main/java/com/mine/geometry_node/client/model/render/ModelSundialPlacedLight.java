package com.mine.geometry_node.client.model.render;

/** Known Sundial block-light term adaptation; shares the model BVH with held light. */
final class ModelSundialPlacedLight {
    static String declarations() {
        return """
                // Per-fragment coverage of explicit direct-light reconstruction, for pack AO separation.
                float geometry_node_directCoverage = 0.0;
                uniform sampler2D geometry_node_blockWorld;
                uniform int geometry_node_placedReady;
                uniform int geometry_node_placedFieldCount;
                uniform ivec2 geometry_node_placedRanges[8];
                uniform vec4 geometry_node_placedSources[128];
                uniform vec4 geometry_node_blockOrigins[8];
                uniform int geometry_node_dynamicCount;
                uniform vec4 geometry_node_dynamicSources[32];
                bool geometry_node_worldBlocked(vec3 receiver, vec3 source, int field) {
                    vec3 geometry_node_blockOrigin = geometry_node_blockOrigins[field].xyz;
                    vec3 a = source - geometry_node_blockOrigin;
                    vec3 b = receiver - geometry_node_blockOrigin;
                    vec3 direction = b - a;
                    ivec3 cell = ivec3(floor(a));
                    ivec3 target = ivec3(floor(b));
                    ivec3 stepCell = ivec3(sign(direction));
                    vec3 delta = vec3(1e20);
                    vec3 next = vec3(1e20);
                    for (int axis = 0; axis < 3; ++axis) {
                        if (abs(direction[axis]) > 1e-8) {
                            delta[axis] = abs(1.0 / direction[axis]);
                            float edge = float(cell[axis]) + (stepCell[axis] > 0 ? 1.0 : 0.0);
                            next[axis] = (edge - a[axis]) / direction[axis];
                        }
                    }
                    // Emissive opaque source blocks must allow their own light to leave.
                    for (int visit = 0; visit < 64; ++visit) {
                        if (all(equal(cell, target))) return false;
                        float t = min(next.x, min(next.y, next.z));
                        if (t >= 1.0) return false;
                        for (int axis = 0; axis < 3; ++axis) {
                            if (next[axis] <= t + 1e-6) { cell[axis] += stepCell[axis]; next[axis] += delta[axis]; }
                        }
                        if (any(lessThan(cell, ivec3(0))) || any(greaterThanEqual(cell, ivec3(64)))) return false;
                        if (all(equal(cell, target))) return false;
                        int address = cell.x + 64 * (cell.y + 64 * cell.z);
                        if (texelFetch(geometry_node_blockWorld, ivec2(address % 512, address / 512 + field * 512), 0).r > 0.5) return true;
                    }
                    return false;
                }
                float geometry_node_worldVisibility(vec3 receiver, vec3 source) {
                    for (int field = 0; field < 8; ++field) {
                        if (field >= geometry_node_placedFieldCount) break;
                        if (geometry_node_placedReady == 0 || geometry_node_blockOrigins[field].w < 0.5) continue;
                        vec3 a = source - geometry_node_blockOrigins[field].xyz;
                        vec3 b = receiver - geometry_node_blockOrigins[field].xyz;
                        if (any(lessThan(a, vec3(0.0))) || any(greaterThanEqual(a, vec3(64.0)))) continue;
                        if (any(lessThan(b, vec3(0.0))) || any(greaterThanEqual(b, vec3(64.0)))) continue;
                        return geometry_node_worldBlocked(receiver, source, field) ? 0.0 : 1.0;
                    }
                    return 1.0;
                }
                float geometry_node_dynamicLight(vec3 receiver, float original) {
                    for (int i = 0; i < 32; ++i) {
                        if (i >= geometry_node_dynamicCount) break;
                        vec4 light = geometry_node_dynamicSources[i];
                        float strength = clamp((light.w - length(receiver - light.xyz)) / 15.0, 0.0, 1.0);
                        if (strength <= original) continue;
                        // A full vanilla blocker already makes this contribution zero; skip the model BVH.
                        if (geometry_node_worldVisibility(receiver, light.xyz) <= 0.0) continue;
                        original = max(original, strength * geometry_node_lightVisibility(receiver, light.xyz));
                    }
                    return original;
                }
                float geometry_node_placedLight(vec3 receiver, float original) {
                    if (geometry_node_lightCount <= 0) return original;
                    float weight = 0.0;
                    int field = -1;
                    for (int f = 0; f < 8; ++f) {
                        if (f >= geometry_node_placedFieldCount) break;
                        if (geometry_node_blockOrigins[f].w < 0.5 || geometry_node_placedReady == 0) continue;
                        vec3 centered = abs(receiver - geometry_node_blockOrigins[f].xyz - vec3(32.0));
                        float edge = max(centered.x, max(centered.y, centered.z));
                        float candidate = 1.0 - smoothstep(14.0, 16.0, edge);
                        if (candidate > weight) { weight = candidate; field = f; }
                    }
                    float result = original;
                    if (field >= 0) {
                        float lightValue = 0.0;
                        ivec2 range = geometry_node_placedRanges[field];
                        for (int i = 0; i < 16; ++i) {
                            if (i >= range.y) break;
                            vec4 light = geometry_node_placedSources[range.x + i];
                            // These are direct point-source rays, not voxel light propagation.
                            // L1 distance creates axis-aligned diamond contours even on a smooth receiver.
                            // Keep the pack's bounded linear light-level approximation, but make it radial.
                            float strength = clamp((light.w - length(receiver - light.xyz)) / 15.0, 0.0, 1.0);
                            if (strength <= lightValue) continue;
                            if (geometry_node_worldBlocked(receiver, light.xyz, field)) continue;
                            lightValue = max(lightValue, strength * geometry_node_lightVisibility(receiver, light.xyz));
                        }
                        result = mix(original, lightValue, weight);
                        geometry_node_directCoverage = weight;
                    }
                    return geometry_node_dynamicLight(receiver, result);
                }
                """;
    }
    private ModelSundialPlacedLight() {}
}
