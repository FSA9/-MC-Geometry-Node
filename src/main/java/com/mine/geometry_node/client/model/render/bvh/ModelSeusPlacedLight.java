package com.mine.geometry_node.client.model.render.bvh;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Moves only known, nearby SEUS emitter radiance out of its screen-space GI filter. */
final class ModelSeusPlacedLight {
    record Integration(String source, String declarations) {}
    private record Emission(String statement, String intensity) {}
    private static final String NUMBER = "(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?[fF]?";
    private static final String INTENSITY = "(?:GI_LIGHT_BLOCK_INTENSITY|" + NUMBER + ")";
    private static final String PRIMARY = "vec3 C(vec3 v,vec3 m,vec3 y,vec3 s,vec3 x,MaterialMask r,float z,vec2 n,float t,out float c)";

    static Integration primary(String source) {
        Emission emission = emission(source, true);
        String declarations = declarations(source, emission);
        if (declarations == null) return null;
        String result = ModelPackLightAdapters.replaceUnique(source, emission.statement(),
                "V += 0.09 * S * P.xyz * " + emission.intensity() + " * (1.0 - geometry_node_seusManagedWeight("
                + "v, F.pnOlPKItYq - vec3(float(R) * 0.5) + vec3(1.5) - Fract01(cameraPosition.xyz + 0.5), 31.0));");
        if (result != null) result = ModelPackLightAdapters.replaceUnique(result,
                "geometry_node_primaryEmitters += geometry_node_emitter * geometry_node_primaryGiVisibility(v + y * 0.01, geometry_node_emitterPosition);", """
                geometry_node_emitter *= 1.0 - geometry_node_seusManagedWeight(v, geometry_node_emitterPosition, 241.0);
                if (any(notEqual(geometry_node_emitter, vec3(0.0)))) {
                    geometry_node_primaryEmitters += geometry_node_emitter * geometry_node_primaryGiVisibility(
                            v + y * 0.01, geometry_node_emitterPosition);
                }
                """);
        if (result != null) result = ModelPackLightAdapters.replaceUnique(result, PRIMARY,
                "float geometry_node_seusManagedWeight(vec3 receiver, vec3 source, float kind);\n"
                + "bool geometry_node_seusScreenEmitter(vec3 receiver, vec3 surface, vec3 direction);\n" + PRIMARY);
        if (result != null && source.contains("S - Fract01(cameraPosition.xyz + .5) - .5")) {
            result = ModelPackLightAdapters.replaceUnique(result, "if(g) {",
                    "if (g && !geometry_node_seusScreenEmitter(v, S - Fract01(cameraPosition.xyz + .5) - .5, h)) {");
        }
        return result == null ? null : new Integration(result, declarations);
    }

    static Integration direct(String source) {
        for (String anchor : new String[]{"const float h=75.;", "float l=.3;",
                "e=mix(e,R,vec3(saturate(s*l-h*l)));"}) {
            if (ModelPackLightAdapters.replaceUnique(source, anchor, anchor) == null) return null;
        }
        String declarations = declarations(source, emission(source, false));
        if (declarations == null) return null;
        String result = ModelPackLightAdapters.replaceUnique(source, "t.xyz=e;", """
                t.xyz=e;
                float geometry_node_seusViewWeight = 1.0 - saturate(s * 0.3 - h * 0.3);
                if (z.sky < 0.5 && v.depth < 1.0 && geometry_node_seusViewWeight > 0.0) {
                    t += v.albedo.xyz * geometry_node_seusPlacedDirect(y.xyz, f, r, z.grass) * geometry_node_seusViewWeight;
                }
                """);
        return result == null ? null : new Integration(result, declarations);
    }

    private static Emission emission(String source, boolean primary) {
        String prefix = primary
                ? "\\bV\\s*\\+=\\s*(?<factor>" + NUMBER + ")\\s*\\*\\s*S\\s*\\*\\s*P\\s*\\.\\s*xyz\\s*\\*\\s*"
                : "\\bt\\s*\\.\\s*xyz\\s*\\+=\\s*v\\s*\\.\\s*albedo\\s*\\.\\s*xyz\\s*\\*\\s*";
        Matcher matcher = Pattern.compile(prefix + "(?<intensity>" + INTENSITY + ")\\s*;").matcher(source);
        Emission result = null;
        while (matcher.find()) {
            if (primary && new BigDecimal(matcher.group("factor").replaceAll("[fF]$", ""))
                    .compareTo(new BigDecimal("0.09")) != 0) continue;
            if (result != null) return null;
            result = new Emission(matcher.group(), matcher.group("intensity"));
        }
        return result;
    }

    private static String declarations(String source, Emission emission) {
        if (emission == null || !source.replaceAll("\\s+", "").contains("ivec2(2048,2048)")) return null;
        Matcher sampler = Pattern.compile("\\btexture(?:2DLod|Lod|2D)?\\s*\\(\\s*(shadowcolor(?:0)?)\\s*,").matcher(source);
        if (!sampler.find()) return null;
        String atlas = sampler.group(1);
        while (sampler.find()) if (!atlas.equals(sampler.group(1))) return null;
        return "\nuniform int geometry_node_seusPlacedReady;\n"
                + "uniform int bvhAreaSamples;\n"
                + "const float geometry_node_seusBlockRadiance = 0.9 * " + emission.intensity() + ";\n"
                + """
                int geometry_node_seusPlacedField(vec3 receiver, out float coverage) {
                    coverage = 0.0;
                    if (geometry_node_seusPlacedReady == 0 || geometry_node_placedEnabled == 0
                            || geometry_node_placedReady == 0 || geometry_node_lightCount <= 0
                            || !bvhReceiverEnabled(receiver)) return -1;
                    int selected = -1;
                    for (int field = 0; field < 8; ++field) {
                        if (field >= geometry_node_placedFieldCount) break;
                        if (geometry_node_blockOrigins[field].w < 0.5 || geometry_node_placedRanges[field].y <= 0) continue;
                        vec3 centered = abs(receiver - geometry_node_blockOrigins[field].xyz - vec3(32.0));
                        float edge = max(centered.x, max(centered.y, centered.z));
                        float weight = 1.0 - smoothstep(14.0, 16.0, edge);
                        if (weight > coverage) { coverage = weight; selected = field; }
                    }
                    return selected;
                }
                float geometry_node_seusSourceWeight(vec3 receiver, vec3 source, float coverage) {
                    vec3 delta = abs(receiver - source);
                    if (max(delta.x, max(delta.y, delta.z)) <= 0.501) return 0.0;
                    // Four area samples cannot resolve the singular near-face solid angle.
                    // Both paths retain native GI there, with the same smooth handoff weight.
                    float surfaceDistance = length(max(delta - vec3(0.5), vec3(0.0)));
                    return coverage * smoothstep(0.05, 0.25, surfaceDistance)
                            * (1.0 - smoothstep(12.0, 16.0, length(delta)));
                }
                vec4 geometry_node_seusEmitter(vec3 source) {
                    // E12's 2048-square voxel quadrant packs a 159-cube in x,z,y order.
                    vec3 cell = floor(source + Fract01(cameraPosition.xyz + 0.5) + vec3(78.5));
                    if (any(lessThan(cell, vec3(0.0))) || any(greaterThanEqual(cell, vec3(159.0)))) return vec4(0.0);
                    int row = int(cell.x) + int(cell.y) * 159;
                    ivec2 pixel = ivec2(row % 2048, int(cell.z) + (row / 2048) * 159);
                """
                + "if (any(notEqual(textureSize(" + atlas + ", 0), ivec2(4096)))) return vec4(0.0);\n"
                + "vec4 voxelData = texelFetch(" + atlas + ", pixel, 0);\n"
                + """
                    float kind = floor(voxelData.a * 255.0 + 0.5);
                    if (kind == 31.0) return vec4(voxelData.rgb * geometry_node_seusBlockRadiance, kind);
                    if (kind == 241.0) return vec4(voxelData.rgb * 5.0, kind);
                    return vec4(0.0);
                }
                float geometry_node_seusManagedWeight(vec3 receiver, vec3 source, float kind) {
                    float coverage;
                    int field = geometry_node_seusPlacedField(receiver, coverage);
                    if (field < 0) return 0.0;
                    float weight = geometry_node_seusSourceWeight(receiver, source, coverage);
                    if (weight <= 0.0) return 0.0;
                    ivec2 range = geometry_node_placedRanges[field];
                    for (int i = 0; i < 16; ++i) {
                        if (i >= range.y) break;
                        vec4 light = geometry_node_placedSources[range.x + i];
                        if (light.w <= 0.0 || any(greaterThan(abs(light.xyz - source), vec3(0.01)))) continue;
                        vec4 emitter = geometry_node_seusEmitter(light.xyz);
                        if (emitter.a == kind && any(greaterThan(emitter.rgb, vec3(0.0)))) return weight;
                    }
                    return 0.0;
                }
                bool geometry_node_seusScreenEmitter(vec3 receiver, vec3 surface, vec3 direction) {
                    float coverage;
                    if (geometry_node_seusPlacedField(receiver, coverage) < 0) return false;
                    // Step into the hit surface so a face on a block boundary selects that emitter.
                    vec3 cell = floor(surface + direction * 0.01 + Fract01(cameraPosition.xyz + 0.5) + vec3(78.5));
                    vec3 center = cell - vec3(78.0) - Fract01(cameraPosition.xyz + 0.5);
                    vec4 emitter = geometry_node_seusEmitter(center);
                    return emitter.a != 0.0 && geometry_node_seusManagedWeight(receiver, center, emitter.a) > 0.0;
                }
                vec3 geometry_node_seusPlacedDirect(vec3 receiver, vec3 normal, vec3 geoNormal, float grass) {
                    float coverage;
                    int field = geometry_node_seusPlacedField(receiver, coverage);
                    if (field < 0) return vec3(0.0);
                    int areaSamples = bvhReceiverSamples(receiver, bvhAreaSamples);
                    if (areaSamples <= 0) return vec3(0.0);
                    if (grass > 0.5) { normal = vec3(0.0, 1.0, 0.0); geoNormal = normal; }
                    vec3 result = vec3(0.0);
                    ivec2 range = geometry_node_placedRanges[field];
                    for (int i = 0; i < 16; ++i) {
                        if (i >= range.y) break;
                        vec4 light = geometry_node_placedSources[range.x + i];
                        if (light.w <= 0.0) continue;
                        float weight = geometry_node_seusSourceWeight(receiver, light.xyz, coverage);
                        if (weight <= 0.0) continue;
                        vec3 toCenter = light.xyz - receiver;
                        if (dot(normal, toCenter) + 0.5 * dot(abs(normal), vec3(1.0)) <= 0.0
                                || dot(geoNormal, toCenter) + 0.5 * dot(abs(geoNormal), vec3(1.0)) <= 0.0) continue;
                        vec4 emitter = geometry_node_seusEmitter(light.xyz);
                        if (emitter.a == 0.0 || !any(greaterThan(emitter.rgb, vec3(0.0)))) continue;
                        // Every retained area sample keeps its complete visibility query.
                        // Importance chooses a face; its probability remains in the area PDF.
                        vec3 signs = sign(receiver - light.xyz);
                        vec3 importance = vec3(0.0);
                        for (int axis = 0; axis < 3; ++axis) {
                            float faceDistance = abs(receiver[axis] - light[axis]) - 0.5;
                            if (faceDistance <= 0.0) continue;
                            vec3 faceCenter = light.xyz;
                            faceCenter[axis] += signs[axis] * 0.5;
                            float squared = dot(faceCenter - receiver, faceCenter - receiver);
                            importance[axis] = faceDistance / max(squared * sqrt(squared), 1e-8);
                        }
                        float totalImportance = dot(importance, vec3(1.0));
                        if (totalImportance <= 0.0) continue;
                        for (int sampleIndex = 0; sampleIndex < 4; ++sampleIndex) {
                            if (sampleIndex >= areaSamples) break;
                            float choice = (float(sampleIndex) + 0.5) * 0.25 * totalImportance;
                            if (areaSamples != 4) choice = (float(sampleIndex) + 0.5) / float(areaSamples) * totalImportance;
                            int axis = choice < importance.x ? 0 : choice < importance.x + importance.y ? 1 : 2;
                            if (importance[axis] <= 0.0) continue;
                            int u = (axis + 1) % 3;
                            int v = (axis + 2) % 3;
                            vec3 source = light.xyz;
                            source[axis] += signs[axis] * 0.499;
                            if (areaSamples == 4) {
                                source[u] += (sampleIndex % 2 == 0 ? -0.25 : 0.25);
                                source[v] += (sampleIndex < 2 ? -0.25 : 0.25);
                            } else if (areaSamples == 3) {
                                source[u] += sampleIndex == 0 ? 0.0 : sampleIndex == 1 ? -0.2886751346 : 0.2886751346;
                                source[v] += sampleIndex == 0 ? 0.3333333333 : -0.1666666667;
                            } else if (areaSamples == 2) {
                                float offset = sampleIndex == 0 ? -0.25 : 0.25;
                                source[u] += offset;
                                source[v] += offset;
                            }
                            vec3 delta = source - receiver;
                            float distanceSquared = dot(delta, delta);
                            if (distanceSquared <= 1e-8) continue;
                            vec3 direction = delta * inversesqrt(distanceSquared);
                            float geoCosine = max(dot(geoNormal, direction), 0.0);
                            float cosine = grass > 0.5 ? geoCosine : max(dot(normal, direction), 0.0) * geoCosine / (geoCosine + 0.01);
                            float emitterCosine = max(-direction[axis] * signs[axis], 0.0);
                            float contribution = weight * cosine * emitterCosine * totalImportance
                                    / (12.5663706144 * distanceSquared * importance[axis]);
                            if (areaSamples != 4) contribution = weight * cosine * emitterCosine * totalImportance
                                    / (3.1415926536 * float(areaSamples) * distanceSquared * importance[axis]);
                            if (contribution <= 0.0) continue;
                            vec3 surface = receiver + geoNormal * 0.01;
                            if (geometry_node_worldVisibility(surface, source) <= 0.0) continue;
                            float visibility = geometry_node_lightVisibility(surface, source);
                            if (visibility <= 0.0) continue;
                            vec3 radiance = emitter.rgb;
                            if (isEyeInWater > 0) {
                                // Do not call a shaderpack-private UnderwaterFog helper here.  The
                                // composite source may not expose that include to injected functions,
                                // which makes otherwise valid SEUS programs fail to compile.  Keep a
                                // small self-contained attenuation for the model emitter; the pack's
                                // original GI path still owns in-scattering and the final fog pass.
                                radiance *= exp(-0.07 * sqrt(distanceSquared));
                            }
                            result += radiance * (contribution * visibility);
                        }
                    }
                    return result;
                }
                """;
    }

    private ModelSeusPlacedLight() {}
}
