package com.mine.geometry_node.client.runtime.model.render.shadow;

/** Shader stages kept separate from the known-pack source matching. */
final class ModelSundialShadowRaster {
    static String geometry(String distortion, String declarations, String pass, String payload) {
        return "#version 330 compatibility\n" + declarations + "const float distortionStrength = " + distortion + ";\n" + """
                layout(triangles) in;
                layout(triangle_strip, max_vertices = 4) out;
                in vec3 modelSundialClip[];
                in vec2 modelSundialAtlasOffset[];
                uniform int geometry_node_repeatShadowUv;
                flat out vec3 modelSundialBaryA;
                flat out vec3 modelSundialBaryB;
                flat out vec3 modelSundialDepths;
                flat out vec2 modelSundialOffset;
                vec2 modelSundialWarp(vec2 p) {
                    float r = length(p);
                    return p * (r > 1e-7 ? log(1.0 + distortionStrength * r) / r
                        : distortionStrength) * (0.5 / log(1.0 + distortionStrength));
                }
                vec4 modelSundialBounds(vec2 lo, vec2 hi) {
                    vec2 nearAxis = clamp(vec2(0.0), lo, hi), farAxis = max(abs(lo), abs(hi));
                    return vec4(modelSundialWarp(vec2(lo.x, lo.x < 0.0 ? nearAxis.y : farAxis.y)).x,
                        modelSundialWarp(vec2(lo.y < 0.0 ? nearAxis.x : farAxis.x, lo.y)).y,
                        modelSundialWarp(vec2(hi.x, hi.x > 0.0 ? nearAxis.y : farAxis.y)).x,
                        modelSundialWarp(vec2(hi.y > 0.0 ? nearAxis.x : farAxis.x, hi.y)).y);
                }
                void main() {
                    if (geometry_node_repeatShadowUv == 0) {
                        for (int i = 0; i < 3; ++i) {
                            gl_Position = gl_in[i].gl_Position;
                            PASS
                            EmitVertex();
                        }
                        EndPrimitive();
                        return;
                    }
                    vec3 a = modelSundialClip[0], b = modelSundialClip[1], c = modelSundialClip[2];
                    vec2 ab = b.xy - a.xy, ac = c.xy - a.xy;
                    float area = ab.x * ac.y - ab.y * ac.x;
                    if (abs(area) < 1e-20) return;
                    vec3 baryA = vec3(vec2(b.y - c.y, c.x - b.x) / area, c.x);
                    vec3 baryB = vec3(vec2(c.y - a.y, a.x - c.x) / area, c.y);
                    // Do not recover this from the pack warp: its origin divides by zero.
                    vec2 offset = modelSundialAtlasOffset[0];
                    vec2 lo = min(a.xy, min(b.xy, c.xy)), hi = max(a.xy, max(b.xy, c.xy));
                    // The radial scale decreases monotonically. These extrema enclose the curved edges.
                    vec4 bounds = modelSundialBounds(lo, hi);
                    vec2 low = bounds.xy + offset, high = bounds.zw + offset;
                    low = max(low - vec2(2e-6), vec2(-1.0));
                    high = min(high + vec2(2e-6), vec2(1.0));
                    if (any(greaterThanEqual(low, high))) return;
                    vec2 axis = vec2(1.0, 0.0), side = vec2(0.0, 1.0);
                    bool oriented = false;
                    float boxArea = (high.x - low.x) * (high.y - low.y);
                    // Long diagonal triangles otherwise shade mostly empty bounding rectangles.
                    if (boxArea > 0.0005 && abs(area) < 0.5 * (hi.x - lo.x) * (hi.y - lo.y)) {
                        vec2 edge = dot(ab, ab) > dot(ac, ac) ? ab : ac;
                        vec2 bc = c.xy - b.xy;
                        if (dot(bc, bc) > dot(edge, edge)) edge = bc;
                        axis = normalize(edge); side = vec2(-axis.y, axis.x);
                        vec2 ra = vec2(dot(axis, a.xy), dot(side, a.xy));
                        vec2 rb = vec2(dot(axis, b.xy), dot(side, b.xy));
                        vec2 rc = vec2(dot(axis, c.xy), dot(side, c.xy));
                        vec2 rlo = min(ra, min(rb, rc)), rhi = max(ra, max(rb, rc));
                        vec2 pad = 3e-5 * (rhi - rlo) + 4e-6 * max(vec2(1.0), max(abs(rlo), abs(rhi)));
                        vec4 rotated = modelSundialBounds(rlo - pad, rhi + pad);
                        rotated.xy -= vec2(4e-6); rotated.zw += vec2(4e-6);
                        vec2 extent = rotated.zw - rotated.xy;
                        if (extent.x * extent.y < boxArea * 0.75) {
                            low = rotated.xy; high = rotated.zw; oriented = true;
                        }
                    }
                    for (int corner = 0; corner < 4; ++corner) {
                        int k = area > 0.0 ? corner : (corner == 1 ? 2 : (corner == 2 ? 1 : corner));
                        vec2 position = vec2((k & 1) == 0 ? low.x : high.x, (k & 2) == 0 ? low.y : high.y);
                        if (oriented) position = axis * position.x + side * position.y + offset;
                        gl_Position = vec4(position, 0.0, 1.0);
                        modelSundialBaryA = baryA; modelSundialBaryB = baryB;
                        modelSundialDepths = vec3(a.z - c.z, b.z - c.z, c.z);
                        modelSundialOffset = offset;
                        int i = 0;
                        PASS
                        PAYLOAD
                        EmitVertex();
                    }
                    EndPrimitive();
                }
                """.replace("PASS", pass).replace("PAYLOAD", payload);
    }

    static String fragmentStart(String interpolate) {
        return """
                gl_FragDepth = gl_FragCoord.z;
                float modelSundialDepth = gl_FragCoord.z;
                if (geometry_node_repeatShadowUv != 0) {
                    vec2 warped = (gl_FragCoord.xy * (2.0 / realShadowMapResolution) - 1.0 - modelSundialOffset) * 2.0;
                    float radius = length(warped);
                    float scale = radius > 1e-6
                        ? (pow(distortionStrength + 1.0, radius) - 1.0) / (distortionStrength * radius)
                        : log(distortionStrength + 1.0) / distortionStrength;
                    vec2 point = warped * scale - vec2(modelSundialBaryA.z, modelSundialBaryB.z);
                    vec3 modelBary;
                    modelBary.xy = vec2(dot(modelSundialBaryA.xy, point), dot(modelSundialBaryB.xy, point));
                    modelBary.z = 1.0 - modelBary.x - modelBary.y;
                    if (any(lessThan(modelBary, vec3(-1e-5)))) discard;
                    modelSundialDepth = (dot(modelBary.xy, modelSundialDepths.xy) + modelSundialDepths.z) * 0.1 + 0.5;
                    if (modelSundialDepth < 0.0 || modelSundialDepth > 1.0) discard;
                    INTERPOLATE
                }
                """.replace("INTERPOLATE", interpolate);
    }
    private ModelSundialShadowRaster() {}
}
