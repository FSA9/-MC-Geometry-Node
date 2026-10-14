package com.mine.geometry_node.client.runtime.light;

import com.mine.geometry_node.client.model.render.bvh.ModelSundialHeldLight;
import com.mine.geometry_node.client.runtime.light.shader.adapter.LightShaderAdapter;
import com.mine.geometry_node.client.runtime.light.shader.adapter.LightShaderAdapters;
import com.mine.geometry_node.client.runtime.light.shader.adapter.LightShaderPatch;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Signature-guarded adapters. Shaderpack files are never modified on disk. */
public final class NodeLightShader {
    private static final String SOURCE = load("node_lights.glsl");
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)\\s*\\{");
    private static final Map<String, Integer> MATCHES = new LinkedHashMap<>();
    private static final Map<String, String> FAILURES = new LinkedHashMap<>();
    private NodeLightShader() {}

    public static String declarations() {
        return Declarations.SOURCE;
    }

    public static String nativeDeclarations() {
        return NativeDeclarations.SOURCE;
    }

    private static final class Declarations {
        private static final String SOURCE = buildDeclarations(load("node_light_material.glsl"));
    }

    private static final class NativeDeclarations {
        private static final String SOURCE = buildDeclarations(load("node_light_native_material.glsl"));
    }

    private static String buildDeclarations(String material) {
        // Reuse the asset-local traversal, independently of the old light quality switches.
        String shared = ModelSundialHeldLight.declarations();
        String signature = "float geometry_node_lightVisibilityUncached(vec3 receiver, vec3 source)";
        int start = shared.indexOf(signature);
        if (start < 0) throw new IllegalStateException("Missing shared model visibility traversal");
        int end = shared.indexOf('{', start), depth = 1;
        for (++end; depth > 0; end++) {
            char c = shared.charAt(end);
            if (c == '{') depth++; else if (c == '}') depth--;
        }
        String traversal = replaceTraversal(shared.substring(start, end), signature,
                "float nodeModelVisibilityMasked(vec3 receiver, vec3 source, int instanceMask)");
        traversal = replaceTraversal(traversal, "float distanceToLight = length(receiver - source);",
                "if (instanceMask == 0) return 1.0;\n    float distanceToLight = length(receiver - source);");
        traversal = replaceTraversal(traversal, "ivec4 range = geometry_node_lightRange[instance];",
                "if ((instanceMask & (1 << instance)) == 0) continue;\n"
                        + "        ivec4 range = geometry_node_lightRange[instance];")
                .replace("geometry_node_sourceLocalAt(source, instance)",
                        "(geometry_node_lightInverse[instance] * vec4(source, 1.0)).xyz")
                .replace("geometry_node_receiverLocalAt(receiver, instance)",
                        "(geometry_node_lightInverse[instance] * vec4(receiver, 1.0)).xyz")
                .replace("geometry_node_bvhEnabled == 0 || ", "")
                .replace("geometry_node_lightCount", "nodeOccluderCount");
        return "\nuniform int nodeOccluderCount;\n" + traversal + "\n" + """
                float nodeModelVisibility(vec3 receiver, vec3 source) {
                    return nodeModelVisibilityMasked(receiver, source, 255);
                }
                // All emitter-to-receiver segments lie in the convex hull of the
                // emitting sphere and the clipped camera segment. Its AABB is a
                // conservative once-per-interval rejection, including transparent
                // models. Surviving instances retain the exact original traversal.
                int nodeVolumeModelMask(vec3 source, vec3 first, vec3 last, float radius) {
                    if (nodeOccluderCount <= 0) return 0;
                    vec3 lower = min(source - vec3(radius), min(first, last));
                    vec3 upper = max(source + vec3(radius), max(first, last));
                    vec3 padding = max(vec3(0.0001), max(abs(lower), abs(upper)) * 0.00001);
                    lower -= padding;
                    upper += padding;
                    int mask = 0;
                    for (int instance = 0; instance < min(nodeOccluderCount, 8); ++instance) {
                        if (geometry_node_lightRange[instance].y <= 0) continue;
                        if (any(lessThan(upper, geometry_node_lightWorldBoundsMin[instance].xyz))
                                || any(greaterThan(lower, geometry_node_lightWorldBoundsMax[instance].xyz))) continue;
                        mask |= 1 << instance;
                    }
                    return mask;
                }
                """ + SOURCE + "\n" + material;
    }

    private static String replaceTraversal(String source, String anchor, String replacement) {
        int first = source.indexOf(anchor);
        if (first < 0 || source.indexOf(anchor, first + anchor.length()) >= 0)
            throw new IllegalStateException("Unexpected shared model visibility anchor: " + anchor);
        return source.replace(anchor, replacement);
    }

    public static String patch(String source) {
        return patch(source,null);
    }

    public static String patch(String source,String vertex) {
        if (source == null || source.contains("uniform int nodeLightCount;")) return source;
        LightShaderAdapter selected = null;
        for (LightShaderAdapter adapter : LightShaderAdapters.all()) {
            if (!adapter.matches(source)) continue;
            if (selected != null) return failed(source, "registry", "ambiguous adapters: " + selected.name() + ", " + adapter.name());
            selected = adapter;
        }
        if (selected == null) return source;
        String pack = selected.name();
        String result = selected.patch(source,vertex);
        if (result == null) return failed(source, pack, "lighting insertion anchors");
        // Some packs composite node lights in a different pass from their model lightmap.
        String shared = result.contains("uniform sampler2D geometry_node_lightBvh;")
                ? "" : ModelSundialHeldLight.declarations();
        var main = MAIN.matcher(result);
        if (!main.find()) return failed(source, pack, "main declaration");
        int mainStart = main.start();
        if (main.find()) return failed(source, pack, "ambiguous main declaration");
        String declared = result.substring(0, mainStart) + shared + declarations() + "\n" + result.substring(mainStart);
        synchronized (NodeLightShader.class) {
            MATCHES.merge(pack, 1, Integer::sum);
            FAILURES.remove(pack);
        }
        return result.contains("nodeVolumeResult(") || result.contains("nodeVolumeBetween(")
                || result.contains("nodeVolumeTransmitted(") ? withVolumeDiagnostics(declared) : declared;
    }

    /** Pixel diagnostics replay only pure framebuffer receivers, with all framebuffer writes disabled. */
    static String withVolumeDiagnostics(String source) {
        if (Pattern.compile("\\b(?:imageStore|imageAtomic\\w*|atomicCounter\\w*|atomicAdd|atomicMin|atomicMax|atomicAnd|atomicOr|atomicXor|atomicExchange|atomicCompSwap)\\s*\\(|\\bbuffer\\s+\\w+\\s*\\{")
                .matcher(source).find()) return source;
        var main = MAIN.matcher(source);
        if (!main.find()) return source;
        int first = main.start(), end = main.end();
        String declaration = main.group().replaceFirst("\\bmain\\b", "nodeLightReceiverMain");
        if (main.find()) return source;
        String versioned = source.substring(0, first) + declaration + source.substring(end);
        var version = Pattern.compile("(?m)^\\s*#version[^\\r\\n]*").matcher(versioned);
        if (!version.find()) return source;
        return versioned.substring(0, version.end()) + "\n#define NODE_LIGHT_PIXEL_DIAGNOSTICS\n"
                + versioned.substring(version.end()) + """

                void main() {
                    nodeLightReceiverMain();
                    if(nodeVolumeProbeActive() && (nodeVolumeProbeFlags & nodeVolumeProbeMode)==0)discard;
                }
                """;
    }

    private static synchronized String failed(String source, String pack, String stage) {
        if (!stage.equals(FAILURES.put(pack, stage)))
            LOGGER.warn("Node light shader adapter rejected {} at {}", pack, stage);
        return source;
    }

    public static synchronized Map<String, Object> report() {
        return Map.of("registered", LightShaderAdapters.all().stream().map(LightShaderAdapter::name).toList(),
                "matches", Map.copyOf(MATCHES), "failures", Map.copyOf(FAILURES),
                "builds", NodeLightShaderBuilds.report(), "nativeBuilds", NodeLightNativePrograms.report(),
                "programCache", NodeLightProgramCache.report());
    }

    public static String replace(String source, String anchor, String replacement) {
        return LightShaderPatch.replace(source, anchor, replacement);
    }
    private static String load(String file) {
        try(var stream=NodeLightShader.class.getResourceAsStream("/assets/geometry_node/shaders/include/" + file)) {
            if(stream==null)throw new IOException("Missing node light shader");
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }catch(IOException error){throw new ExceptionInInitializerError(error);}
    }
}
