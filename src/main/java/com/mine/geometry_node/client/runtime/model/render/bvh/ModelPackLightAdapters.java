package com.mine.geometry_node.client.runtime.model.render.bvh;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Version-signature guarded receiver adapters. Unknown sources are left byte-for-byte intact. */
public final class ModelPackLightAdapters {
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)\\s*\\{");
    private static final Map<String, Integer> MATCHES = new LinkedHashMap<>();
    public static synchronized Map<String, Integer> matches() { return Map.copyOf(MATCHES); }
    private static String hook(String source, String anchor, String insertion) {
        // Iris's AST printer changes whitespace but retains identifiers and punctuation.
        String expression = tokenPattern(anchor);
        Matcher match = Pattern.compile(expression).matcher(source);
        int end = -1;
        while (match.find()) {
            if (!canonical(match.group()).equals(canonical(anchor))) continue;
            if (end >= 0) return null;
            end = match.end();
        }
        if (end < 0) return null;
        return source.substring(0, end) + insertion + source.substring(end);
    }
    private static String declarations(String source, boolean reuseQueries) {
        return declarations(source, reuseQueries, "");
    }
    private static String declarations(String source, boolean reuseQueries, String additional) {
        Matcher main = MAIN.matcher(source);
        if (!main.find()) return null;
        int start = main.start();
        if (main.find()) return null;
        return source.substring(0, start) + ModelSundialHeldLight.declarations(reuseQueries) + additional + source.substring(start);
    }
    private static String finish(String source, String pack) {
        return finish(source, pack, true);
    }
    private static String finish(String source, String pack, boolean reuseQueries) {
        return finish(source, pack, reuseQueries, "");
    }
    private static String finish(String source, String pack, boolean reuseQueries, String additional) {
        if (source == null) return null;
        String result = declarations(source, reuseQueries, additional);
        if (result != null) synchronized (ModelPackLightAdapters.class) { MATCHES.merge(pack, 1, Integer::sum); }
        return result;
    }
    public static String patch(String source) {
        if (source == null || source.contains(ModelSundialHeldLight.SAMPLER)) return source;
        String sundial = ModelSundialHeldLight.patch(source);
        if (!source.equals(sundial)) {
            synchronized (ModelPackLightAdapters.class) { MATCHES.merge("Sundial: opaque local visibility", 1, Integer::sum); }
            return sundial;
        }
        String result = null;
        String compact = source.replaceAll("\\s+", "");
        if (compact.contains("floateyeRelatedDistance=length(waterWorldPos+relativeEyePosition);") && source.contains("waterStain") && source.contains("vanillaLight")) {
            result = hook(source, "vec3 vanillaLight = vec3(0.0);", "\ngbufferData.lightmap.x = geometry_node_placedLight(waterWorldPos + faceforward(mat3(gbufferModelViewInverse) * gbufferData.geoNormal, waterWorldDir, mat3(gbufferModelViewInverse) * gbufferData.geoNormal) * 0.01, gbufferData.lightmap.x);\n");
            if (result != null) result = replaceUnique(result,
                "gbufferData.lightmap.x = max(gbufferData.lightmap.x, heldBlockLightValue / 15.0 * clamp(1.0 - eyeRelatedDistance / 15.0, 0.0, 1.0));",
                "gbufferData.lightmap.x = max(gbufferData.lightmap.x, heldBlockLightValue / 15.0 * clamp(1.0 - eyeRelatedDistance / 15.0, 0.0, 1.0) * geometry_node_heldVisibility(waterWorldPos, -relativeEyePosition));");
            result = finish(result, "Sundial: transparent local + held visibility");
        } else if (source.contains("dataUnpacked1") && source.contains("GriAndEminShadowFix") && compact.contains("vec3fragpos_handfix=fragpos;")) {
            result = hook(source, "vec3 normal = decode(dataUnpacked0.yw);", "\nvec3 geometry_node_receiver = p3 + faceforward(normal, p3 - gbufferModelViewInverse[3].xyz, normal) * 0.01;\nlightmap.x = geometry_node_mergedHeldLight(geometry_node_receiver, geometry_node_placedLight(geometry_node_receiver, lightmap.x));\n");
            result = finish(result, "Bliss 2.0.4: opaque local lightmap visibility");
        } else if (source.contains("lowresCoord") && source.contains("indirectLight") && source.contains("sceneNormal") && compact.contains("unpack2x8(tex1.z)")) {
            result = hook(source, "vec2 lightmaps = saturate(unpack2x8(tex1.z));", "\nvec3 geometry_node_receiver = viewToSceneSpace(viewPos) + faceforward(sceneNormal, mat3(gbufferModelViewInverse) * viewPos, sceneNormal) * 0.01;\nlightmaps.x = geometry_node_mergedHeldLight(geometry_node_receiver, geometry_node_placedLight(geometry_node_receiver, lightmaps.x));\n");
            if (result != null) result = kappaIndirect(result);
            result = finish(result, "Kappa 5.2: opaque local lightmap + indirect segment visibility");
        } else if (source.contains("material_from") && source.contains("view_to_scene_space") && source.contains("decode_unit_vector")) {
            result = hook(source, "vec2 light_levels = data[3];", "\nvec3 geometry_node_receiver = scene_pos + faceforward(flat_normal, world_dir, flat_normal) * 0.01;\nlight_levels.x = geometry_node_placedLight(geometry_node_receiver, light_levels.x);\n");
            if (result == null && source.contains("adjusted_light_levels") && source.contains("scene_back_pos")
                    && source.contains("material_mask") && source.contains("tbn")) {
                result = hook(source, "vec2 adjusted_light_levels = light_levels;", "\nvec3 geometry_node_receiver = scene_pos + faceforward(normal, world_dir, normal) * 0.01;\nadjusted_light_levels.x = geometry_node_placedLight(geometry_node_receiver, adjusted_light_levels.x);\n");
            }
            if (result != null && source.contains("get_handheld_lighting")) {
                String anchor = "vec3 get_handheld_lighting(vec3 scene_pos, float ao) {";
                result = hook(result, anchor, "\nvec3 geometry_node_receiver_held = scene_pos;\n");
                if (result != null) result = replaceUnique(result, "return light_color * falloff;", "return light_color * falloff * geometry_node_heldVisibility(geometry_node_receiver_held, geometry_node_receiver_held - scene_pos);");
                if (result != null) result = result.replace("vec3 get_handheld_lighting", "float geometry_node_heldVisibility(vec3 receiver, vec3 source);\nvec3 get_handheld_lighting");
            }
            result = finish(result, "Photon: local lightmap + explicit held visibility");
        } else if (compact.contains("vec3C(vec3v,vec3m,vec3y,vec3s,vec3x,MaterialMaskr,floatz,vec2n,floatt,outfloatc)")
                && source.contains("qconKIZlZt") && source.contains("SkyShading")) {
            // Visibility belongs to individual transport segments, including the voxel irradiance
            // cache. Clipping only the final screen-space sample leaves secondary light leaks.
            result = replaceUnique(source, "return V;", "if (all(equal(V, vec3(0.0)))) return V;\nreturn V * geometry_node_primaryGiVisibility(v + y * 0.01, v + h * min(c, 128.0));");
            if (result != null && (source.contains("GI_SCREEN_SPACE_TRACING") || Pattern.compile(tokenPattern("return W*100.;")).matcher(source).find())) result = replaceUnique(result, "return W*100.;", "if (all(equal(W, vec3(0.0)))) return W*100.;\nreturn W*100. * geometry_node_primaryGiVisibility(v + y * 0.01, S - Fract01(cameraPosition.xyz + .5) - .5);");
            if (result != null) result = replaceUnique(result, "vec3 C(vec3 v,vec3 m,vec3 y,vec3 s,vec3 x,MaterialMask r,float z,vec2 n,float t,out float c)", "float geometry_node_primaryGiVisibility(vec3 receiver, vec3 source);\nfloat geometry_node_secondaryGiVisibility(vec3 receiver, vec3 source);\nvec3 C(vec3 v,vec3 m,vec3 y,vec3 s,vec3 x,MaterialMask r,float z,vec2 n,float t,out float c)");
            if (result != null) result = seusVoxelTransport(result);
            String placedDeclarations = "";
            if (result != null) {
                var placed = ModelSeusPlacedLight.primary(result);
                if (placed != null) { result = placed.source(); placedDeclarations = placed.declarations(); }
            }
            result = finish(result, "SEUS E12: primary and secondary voxel GI segment occlusion", false, placedDeclarations);
        } else if (compact.contains("CalculateSunlightVisibility(i,z,r)") && compact.contains("FromSH(skySHR,skySHG,skySHB,f)")) {
            // Native fixed-block radiance is already in e (voxel GI). Add only explicitly registered
            // provider/model-attached sources, without duplicating placed block light or pack GI.
            // This anchor is outside HELD_LIGHT so providers also work when held lighting is disabled.
            result = hook(source, "t.xyz=e;", "\nt += v.albedo.xyz * colorTorchlight * 0.3 * geometry_node_dynamicLight(y.xyz + faceforward(r, m.xyz, r) * 0.01, 0.0);\n");
            String held = "t+=v.albedo.xyz*G*p*l*colorTorchlight*.3;";
            if (result != null && Pattern.compile(tokenPattern(held)).matcher(result).find()) {
                result = replaceUnique(result, held, """
                        if (G > 0.0) {
                            if (geometry_node_heldEnabled != 0 && geometry_node_lightCount > 0) {
                                vec3 geometry_node_toHeld = geometry_node_heldSource.xyz - y.xyz;
                                float geometry_node_heldDistanceSquared = dot(geometry_node_toHeld, geometry_node_toHeld);
                                vec3 geometry_node_heldDirection = geometry_node_toHeld * inversesqrt(max(geometry_node_heldDistanceSquared, 1e-8));
                                l = OrenNayar(f, -n, geometry_node_heldDirection);
                                p = 1.0 / (geometry_node_heldDistanceSquared + 0.3);
                                vec3 geometry_node_heldContribution = v.albedo.xyz * G * p * l * colorTorchlight * 0.3;
                                if (any(notEqual(geometry_node_heldContribution, vec3(0.0)))) {
                                    t += geometry_node_heldContribution * geometry_node_heldVisibility(
                                            y.xyz + faceforward(r, m.xyz, r) * 0.01, geometry_node_heldSource.xyz);
                                }
                            } else {
                                t += v.albedo.xyz * G * p * l * colorTorchlight * 0.3;
                            }
                        }
                        """);
            }
            String placedDeclarations = "";
            if (result != null) {
                var placed = ModelSeusPlacedLight.direct(result);
                if (placed != null) { result = placed.source(); placedDeclarations = placed.declarations(); }
            }
            result = finish(result, "SEUS E12: explicit held + placed + registered provider direct light", false, placedDeclarations);
            result = ModelBvhLightShader.patch(result);
        }
        return ModelLightingReceiverTag.patch(result == null ? source : result);
    }

    private static String seusVoxelTransport(String source) {
        boolean hasSecondary = Pattern.compile(tokenPattern("vec3 G() {")).matcher(source).find();
        boolean hasPrimaryEmitters = Pattern.compile(tokenPattern("V+=L*u*E*.5;")).matcher(source).find();
        if (!hasSecondary && !hasPrimaryEmitters) return source;
        if (!hasSecondary || !hasPrimaryEmitters) return null;
        String result = hook(source, "vec3 S=vec3(1.),V=vec3(0.);",
                "\nvec3 geometry_node_primaryEmitters = vec3(0.0);\n");
        if (result != null) result = replaceUnique(result, "V+=L*u*E*.5;", """
                vec3 geometry_node_emitter = L * u * E * 0.5;
                if (any(notEqual(geometry_node_emitter, vec3(0.0)))) {
                    vec3 geometry_node_emitterPosition = F.pnOlPKItYq - vec3(float(R) * 0.5)
                            + vec3(1.5) - Fract01(cameraPosition.xyz + 0.5);
                    geometry_node_primaryEmitters += geometry_node_emitter * geometry_node_primaryGiVisibility(
                            v + y * 0.01, geometry_node_emitterPosition);
                }
                """);
        if (result != null) result = replaceUnique(result,
                "if (all(equal(V, vec3(0.0)))) return V;\nreturn V * geometry_node_primaryGiVisibility(v + y * 0.01, v + h * min(c, 128.0));",
                "return V;");
        if (result != null) result = replaceUnique(result,
                "if(isEyeInWater>0) UnderwaterFog(V,Y,h,colorSkyUp,colorSunlight);", """
                if (any(notEqual(V, vec3(0.0)))) {
                    V *= geometry_node_primaryGiVisibility(v + y * 0.01, v + h * min(c, 128.0));
                }
                V += geometry_node_primaryEmitters;
                if (isEyeInWater > 0) UnderwaterFog(V, Y, h, colorSkyUp, colorSunlight);
                """);
        // f(U,y) clamps probes to the voxel volume. Recover the actual ray origin after that
        // clamp, rather than assuming the unclamped probe U is still the transport origin.
        if (result != null) result = hook(result, "Ray D=MakeRay(f(U,y)*y-vec3(1.,1.,1.),h);",
                "\nvec3 geometry_node_secondaryReceiver = D.origin - vec3(float(y) * 0.5)"
                + " + vec3(1.0) - Fract01(cameraPosition.xyz + 0.5);\n");
        if (result != null) result = replaceUnique(result, "a+=F*S*G*(Y==0?.5:1.);", """
                vec3 geometry_node_emitter = F * S * G * (Y == 0 ? 0.5 : 1.0);
                if (any(notEqual(geometry_node_emitter, vec3(0.0)))) {
                    vec3 geometry_node_emitterPosition = P.pnOlPKItYq - vec3(float(y) * 0.5)
                            + vec3(1.5) - Fract01(cameraPosition.xyz + 0.5);
                    a += geometry_node_emitter * geometry_node_secondaryGiVisibility(
                            geometry_node_secondaryReceiver, geometry_node_emitterPosition);
                }
                """);
        if (result != null) result = hook(result,
                "g=distance(P.pnOlPKItYq.xyz,P.pnOlPKItYqOrigin.xyz);",
                "\nvec3 geometry_node_previousSecondary = a;\n");
        if (result != null) result = replaceUnique(result,
                "if(isEyeInWater>0) UnderwaterFog(a,Y,h,colorSkyUp,colorSunlight);", """
                vec3 geometry_node_secondaryContribution = a - geometry_node_previousSecondary;
                if (any(notEqual(geometry_node_secondaryContribution, vec3(0.0)))) {
                    vec3 geometry_node_secondarySource;
                    if (L < 1.0 || L > 254.0) {
                        geometry_node_secondarySource = geometry_node_secondaryReceiver + h * 128.0;
                    } else {
                        geometry_node_secondarySource = geometry_node_seusVoxelEntry(D.origin, D.direction, P.pnOlPKItYq)
                                - vec3(float(y) * 0.5) + vec3(1.0) - Fract01(cameraPosition.xyz + 0.5);
                    }
                    a = geometry_node_previousSecondary + geometry_node_secondaryContribution * geometry_node_secondaryGiVisibility(
                            geometry_node_secondaryReceiver, geometry_node_secondarySource);
                }
                if (isEyeInWater > 0) UnderwaterFog(a, Y, h, colorSkyUp, colorSunlight);
                """);
        if (result != null) result = replaceUnique(result, "vec3 G() {", """
                vec3 geometry_node_seusVoxelEntry(vec3 origin, vec3 direction, vec3 cell) {
                    float entry = 0.0;
                    for (int axis = 0; axis < 3; ++axis) {
                        if (abs(direction[axis]) > 1e-8) {
                            float a = (cell[axis] - origin[axis]) / direction[axis];
                            float b = (cell[axis] + 1.0 - origin[axis]) / direction[axis];
                            entry = max(entry, min(a, b));
                        }
                    }
                    return origin + direction * entry;
                }
                vec3 G() {
                """);
        if (result != null) {
            // C applies this exact zero factor after fog and every voxel contribution. Keep
            // its earlier screen-space return intact, but avoid tracing a zero-weight GI ray.
            String normalWeight = "if(r.grass<.5) V/=saturate(dot(s,h))+.01,V*=saturate(dot(y,h));";
            if (replaceUnique(result, normalWeight, normalWeight) != null) {
                String weighted = replaceUnique(result, "int R=f();",
                        "if (r.grass < 0.5 && dot(y, h) <= 0.0) return vec3(0.0);\nint R=f();");
                if (weighted != null) result = weighted;
            }
        }
        return result;
    }
    private static String kappaIndirect(String source) {
        String signature = "mat3 raytraceIndirectLight(vec3 viewPos, vec3 sceneNormal, vec2 dither, float skyOcclusion, bool hand)";
        if (!Pattern.compile(tokenPattern(signature)).matcher(source).find()) return source; // SSPT disabled.
        String single = "float brdf = clamp(diffuseHammon(viewNormal, -normalize(viewPos), rayDirection, 1.0) / saturate(dot(rayDirection, viewNormal)/pi), 0.0, halfPi);";
        String multiple = "float brdf = clamp(diffuseHammon(hitNormal, -oldDirection, rayDirection, 1.0) / saturate(dot(rayDirection, hitNormal)/pi), 0.0, halfPi);";
        String result = source;
        for (String rule : new String[] {single, multiple}) {
            if (!Pattern.compile(tokenPattern(rule)).matcher(result).find()) continue;
            // Each preprocessed branch has its own local hitPosition and brdf. Misses query a bounded sky ray.
            String normal = rule.equals(single) ? "viewNormal" : "hitNormal";
            String insertion = "\nfloat geometry_node_segment = geometry_node_receiverActive ? geometry_node_lightVisibility(viewToSceneSpace(viewPos + " + normal + " * 0.01), viewToSceneSpace(hitPosition.z < 1.0 ? screenToViewSpace(hitPosition) : viewPos + rayDirection * 96.0)) : 1.0;\nbrdf *= geometry_node_segment;\n";
            result = hook(result, rule, insertion);
            if (result == null) return null;
        }
        if (!result.contains("float geometry_node_segment")) return null;
        // The advanced emitter path omits brdf, so apply visibility explicitly once there too.
        String advanced = "emission += texelFetch(colortex3, ivec2(hitPosition.xy * viewSize * ResolutionScale), 0).rgb * emissionFalloff;";
        Matcher emitter = Pattern.compile(tokenPattern(advanced)).matcher(result);
        StringBuilder patched = new StringBuilder();
        while (emitter.find()) {
            if (!canonical(emitter.group()).equals(canonical(advanced))) continue;
            emitter.appendReplacement(patched, Matcher.quoteReplacement(advanced.substring(0, advanced.length() - 1) + " * geometry_node_segment;"));
        }
        result = emitter.appendTail(patched).toString();
        result = hook(result, signature + " {", "\nbool geometry_node_receiverActive = bvhGiReceiverEnabled(viewToSceneSpace(viewPos));\n");
        if (result == null) return null;
        return replaceUnique(result, signature, "float geometry_node_lightVisibility(vec3 receiver, vec3 source);\n"
                + "bool bvhGiReceiverEnabled(vec3 receiver);\n" + signature);
    }
    public static String replaceUnique(String source, String target, String replacement) {
        Matcher matcher = Pattern.compile(tokenPattern(target)).matcher(source);
        int start = -1, end = -1;
        while (matcher.find()) {
            if (!canonical(matcher.group()).equals(canonical(target))) continue;
            if (start >= 0) return null;
            start = matcher.start(); end = matcher.end();
        }
        if (start < 0) return null;
        return source.substring(0, start) + replacement + source.substring(end);
    }
    private static String tokenPattern(String value) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = Pattern.compile("[A-Za-z_][A-Za-z_0-9]*|[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+|[^\\s]").matcher(value);
        while (matcher.find()) {
            String token = matcher.group();
            result.append(Character.isDigit(token.charAt(0)) || (token.startsWith(".") && token.length() > 1)
                    ? "(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?[fF]?"
                    : Pattern.quote(token)).append("\\s*");
        }
        return result.toString();
    }
    private static String canonical(String source) {
        Matcher numbers = Pattern.compile("(?<![\\w.])(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?[fF]?(?![\\w.])").matcher(source);
        StringBuilder result = new StringBuilder();
        while (numbers.find()) numbers.appendReplacement(result, Matcher.quoteReplacement(new java.math.BigDecimal(numbers.group().replaceAll("[fF]$", "")).stripTrailingZeros().toPlainString()));
        return numbers.appendTail(result).toString().replaceAll("\\s+", "");
    }
    private ModelPackLightAdapters() {}
}
