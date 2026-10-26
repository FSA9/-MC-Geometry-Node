package com.mine.geometry_node.client.runtime.model.render.bvh;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optional SEUS local-light prepass; its full-resolution path retains the pack's GI and sunlight. */
final class ModelBvhLightShader {
    private static final String PLACED = "geometry_node_seusPlacedDirect(y.xyz, f, r, z.grass)";
    private static final String HELD = "geometry_node_heldVisibility(y.xyz + faceforward(r, m.xyz, r) * 0.01, geometry_node_heldSource.xyz)";
    private static final String NORMALS_READY = "if(z.grass>.5) f=vec3(0.,1.,0.);";
    private static final Pattern OUTPUT = Pattern.compile(
            "(?<output>gl_FragData\\s*\\[\\s*0\\s*\\]|[A-Za-z_][A-Za-z_0-9]*)\\s*=\\s*vec4\\s*\\(\\s*t\\s*\\.\\s*xyz\\s*,\\s*1(?:\\.0*)?[fF]?\\s*\\)\\s*;");

    static String patch(String source) {
        if (source == null || source.contains("uniform int bvhLocalPass;")) return source;
        Matcher output = OUTPUT.matcher(source);
        if (!output.find()) return source;
        String target = output.group("output");
        if (output.find()) return source;
        String result = ModelPackLightAdapters.replaceUnique(source, PLACED, "bvhLocalPlaced(y.xyz, f, r, z.grass)");
        if (result == null) return source;
        result = ModelPackLightAdapters.replaceUnique(result, "GBufferData v=GetGBufferData();", """
                vec2 bvhReceiverUv = texcoord.xy;
                GBufferData v;
                if (bvhLocalPass == 1) {
                    bvhReceiverUv = bvhLocalSourceUv(bvhReceiverUv);
                    v = bvhLocalGBuffer(bvhReceiverUv);
                } else {
                    v = GetGBufferData();
                }
                modelLightingSetReceiverUv(bvhReceiverUv);
                """);
        if (result == null) return source;
        result = ModelPackLightAdapters.replaceUnique(result, "GetViewPosition(texcoord.xy,v.depth)",
                "GetViewPosition(bvhReceiverUv,v.depth)");
        if (result == null) return source;
        String held = ModelPackLightAdapters.replaceUnique(result, HELD,
                "bvhLocalHeld(y.xyz + faceforward(r, m.xyz, r) * 0.01, geometry_node_heldSource.xyz)");
        boolean hasHeld = held != null;
        if (hasHeld) result = held;
        boolean conditionalHeld = Pattern.compile("(?m)^\\s*#ifdef\\s+HELD_LIGHT\\b").matcher(source).find();
        result = ModelPackLightAdapters.replaceUnique(result, NORMALS_READY, NORMALS_READY + "\n" + prepass(target, hasHeld, conditionalHeld));
        if (result == null) return source;
        result = ModelPackLightAdapters.replaceUnique(result, "void main()", declarations() + "\nvoid main()");
        return result == null ? source : result;
    }

    private static String prepass(String output, boolean held, boolean conditionalHeld) {
        return """
                if (bvhLocalPass == 1) {
                """ + "if (!bvhLocalSamplePixel(y.xyz)) { " + output + " = vec4(0.0, 0.0, 0.0, -1.0); return; }\n" + """
                    vec3 bvhPlaced = vec3(0.0);
                    float bvhHeld = 1.0;
                    if (z.sky < 0.5 && v.depth < 1.0) {
                        if (s < 78.3333333333) bvhPlaced = geometry_node_seusPlacedDirect(y.xyz, f, r, z.grass);
                """ + (held ? (conditionalHeld ? "#ifdef HELD_LIGHT\n" : "") + """
                        if (geometry_node_heldEnabled != 0 && geometry_node_lightCount > 0
                                && heldBlockLightValue + heldBlockLightValue2 > 0) {
                            bvhHeld = geometry_node_heldVisibility(
                                    y.xyz + faceforward(r, m.xyz, r) * 0.01, geometry_node_heldSource.xyz);
                        }
                """ + (conditionalHeld ? "#endif\n" : "") : "") + """
                    }
                """ + output + " = vec4(bvhPlaced, bvhHeld);\nreturn;\n}\n"
                + "if (bvhLocalPass == 2 && z.sky < 0.5 && v.depth < 1.0) bvhLocalReconstruct(y.xyz, f, r, v.materialID);\n";
    }

    private static String declarations() {
        return """
                uniform int bvhLocalPass;
                uniform sampler2D bvhLocalLight;
                uniform vec2 bvhLocalSize;
                bool bvhLocalValid = false;
                vec4 bvhLocalValue = vec4(0.0, 0.0, 0.0, 1.0);

                bool bvhLocalSamplePixel(vec3 receiver) {
                    float scale = bvhReceiverResolution(receiver);
                    if (scale <= 0.0 || scale >= 1.0) return false;
                    vec2 pixel = floor(gl_FragCoord.xy);
                    vec2 cell = floor((pixel + vec2(0.5)) * scale);
                    vec2 anchor = floor((cell + vec2(0.5)) / scale);
                    return all(equal(pixel, anchor));
                }

                vec2 bvhLocalSourceUv(vec2 uv) {
                    vec2 size = vec2(textureSize(depthtex1, 0));
                    return (clamp(floor(uv * size), vec2(0.0), size - vec2(1.0)) + vec2(0.5)) / size;
                }
                GBufferData bvhLocalGBuffer(vec2 uv) {
                    GBufferData data;
                    vec4 normals = textureLod(colortex2, uv, 0.0);
                    data.depth = textureLod(depthtex1, uv, 0.0).x;
                    data.normal = DecodeNormal(normals.xy);
                    data.geoNormal = DecodeNormal(normals.zw);
                    data.materialID = UnpackTwo8BitFrom16Bit(textureLod(colortex1, uv, 0.0).z).x;
                    // The local prepass exits before any material or GI term consumes these fields.
                    data.albedo = vec4(0.0);
                    data.mcLightmap = vec2(0.0);
                    data.smoothness = data.metalness = data.emissive = data.totalTexGrad = 0.0;
                    return data;
                }

                bool bvhLocalGuide(vec2 uv, vec3 receiver, vec3 normal, vec3 geoNormal, float material, float scale, int samples) {
                    uv = bvhLocalSourceUv(uv);
                    float depth = textureLod(depthtex1, uv, 0.0).x;
                    if (depth >= 1.0) return false;
                    vec4 packedNormals = textureLod(colortex2, uv, 0.0);
                    vec3 sampleNormal = normalize((gbufferModelViewInverse * vec4(DecodeNormal(packedNormals.xy), 0.0)).xyz);
                    vec3 sampleGeoNormal = normalize((gbufferModelViewInverse * vec4(DecodeNormal(packedNormals.zw), 0.0)).xyz);
                    float sampleMaterial = UnpackTwo8BitFrom16Bit(textureLod(colortex1, uv, 0.0).z).x;
                    if (abs(sampleMaterial - material) > 0.0001) return false;
                    MaterialMask mask = CalculateMasks(sampleMaterial);
                    if (mask.sky > 0.5) return false;
                    if (mask.grass > 0.5) sampleNormal = vec3(0.0, 1.0, 0.0);
                    if (dot(normal, sampleNormal) < 0.995 || dot(geoNormal, sampleGeoNormal) < 0.999) return false;
                    vec4 viewPosition = GetViewPosition(uv, depth);
                    vec3 samplePosition = (gbufferModelViewInverse * vec4(viewPosition.xyz, 1.0)).xyz;
                    int receiverSlot = modelLightingReceiverSlotAt(receiver);
                    modelLightingSetReceiverUv(uv);
                    bool sameQuality = modelLightingReceiverSlotAt(samplePosition) == receiverSlot
                            && abs(bvhReceiverResolution(samplePosition) - scale) <= 0.00001
                            && bvhReceiverSamples(samplePosition, bvhManualSamples) == samples;
                    modelLightingSetReceiverUv(texcoord.xy);
                    if (!sameQuality) return false;
                    vec3 delta = samplePosition - receiver;
                    // Reject separate depth layers, including parallel surfaces with matching normals.
                    if (abs(dot(delta, geoNormal)) > 0.003 || abs(dot(delta, sampleGeoNormal)) > 0.003) return false;
                    return true;
                }

                void bvhLocalReconstruct(vec3 receiver, vec3 normal, vec3 geoNormal, float material) {
                    if (any(lessThan(bvhLocalSize, vec2(1.0)))) return;
                    float scale = bvhReceiverResolution(receiver);
                    int samples = bvhReceiverSamples(receiver, bvhManualSamples);
                    if (scale <= 0.0 || scale >= 1.0) return;
                    {
                        ivec2 ownPixel = clamp(ivec2(texcoord.xy * bvhLocalSize), ivec2(0), ivec2(bvhLocalSize) - ivec2(1));
                        vec4 ownValue = texelFetch(bvhLocalLight, ownPixel, 0);
                        if (ownValue.a >= 0.0 && !any(isnan(ownValue)) && !any(isinf(ownValue))) {
                            vec2 ownUv = (vec2(ownPixel) + vec2(0.5)) / bvhLocalSize;
                            if (bvhLocalGuide(ownUv, receiver, normal, geoNormal, material, scale, samples)) {
                                bvhLocalValue = ownValue;
                                bvhLocalValid = true;
                                return;
                            }
                        }
                    }
                    vec2 position = texcoord.xy * bvhLocalSize * scale - vec2(0.5);
                    ivec2 base = ivec2(floor(position));
                    vec2 fraction = fract(position);
                    vec4 weighted = vec4(0.0);
                    vec4 minimum = vec4(1e30), maximum = vec4(-1e30);
                    float total = 0.0;
                    for (int index = 0; index < 4; ++index) {
                        ivec2 offset = ivec2(index % 2, index / 2);
                        ivec2 pixel = ivec2(floor((vec2(base + offset) + vec2(0.5)) / scale));
                        pixel = clamp(pixel, ivec2(0), ivec2(bvhLocalSize) - ivec2(1));
                        vec2 uv = (vec2(pixel) + vec2(0.5)) / bvhLocalSize;
                        vec2 axes = mix(vec2(1.0) - fraction, fraction, vec2(offset));
                        float weight = axes.x * axes.y;
                        if (weight <= 0.0) continue;
                        if (!bvhLocalGuide(uv, receiver, normal, geoNormal, material, scale, samples)) return;
                        vec4 value = texelFetch(bvhLocalLight, pixel, 0);
                        if (value.a < 0.0) return;
                        if (any(isnan(value)) || any(isinf(value))) return;
                        minimum = min(minimum, value);
                        maximum = max(maximum, value);
                        weighted += value * weight;
                        total += weight;
                    }
                    if (total <= 0.0) return;
                    // Preserve sampled shadow boundaries; uncertain pixels trace at full resolution.
                    if (maximum.a - minimum.a > 0.01) return;
                    if (any(greaterThan(maximum.rgb - minimum.rgb, max(vec3(0.002), maximum.rgb * 0.15)))) return;
                    bvhLocalValue = weighted / total;
                    bvhLocalValid = true;
                }

                vec3 bvhLocalPlaced(vec3 receiver, vec3 normal, vec3 geoNormal, float grass) {
                    if (bvhLocalValid) return bvhLocalValue.rgb;
                    return geometry_node_seusPlacedDirect(receiver, normal, geoNormal, grass);
                }
                float bvhLocalHeld(vec3 receiver, vec3 source) {
                    if (bvhLocalValid) return bvhLocalValue.a;
                    return geometry_node_heldVisibility(receiver, source);
                }
                """;
    }

    private ModelBvhLightShader() {}
}
