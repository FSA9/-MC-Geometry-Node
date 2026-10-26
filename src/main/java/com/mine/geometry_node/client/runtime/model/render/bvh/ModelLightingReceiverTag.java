package com.mine.geometry_node.client.runtime.model.render.bvh;

import org.lwjgl.opengl.GL20C;

import java.util.HashMap;
import java.util.Map;

/** SEUS E12 reserves the low byte of solid colortex1.w; retain its texture-gradient high byte. */
public final class ModelLightingReceiverTag {
    public static final String UNIFORM = "modelLightingReceiverId";
    public static final String STUB = "int modelLightingReceiverSlotAt(vec3 receiver) { return -1; }";
    public static final String UV_STUB = "void modelLightingSetReceiverUv(vec2 uv) {}";
    private static final Map<Integer, Integer> LOCATIONS = new HashMap<>();
    private static int currentId;
    private static long taggedDraws;
    private static final String SIGNATURE = "void OutputGBufferDataSolid(in GBufferData data, out vec4 target0, out vec4 target1, out vec4 target2, out vec4 target3)";

    public static String patch(String source) {
        if (source == null) return null;
        String result = source;
        if (!source.contains("uniform int " + UNIFORM + ";") && !matches(source, "unpacked1w.y")) {
            String tagged = ModelPackLightAdapters.replaceUnique(source,
                    "PackTwo8BitTo16Bit(data.totalTexGrad, 1.0)",
                    "PackTwo8BitTo16Bit(data.totalTexGrad, modelLightingReceiverId > 0 && modelLightingReceiverId <= 128"
                            + " ? (float(modelLightingReceiverId) + 0.25) / 255.0 : 1.0)");
            if (tagged != null && matches(source, "EncodeNormal(data.geoNormal.xyz)")
                    && matches(source, "PackTwo8BitTo16Bit(data.materialID, data.emissive)")) {
                String declared = ModelPackLightAdapters.replaceUnique(tagged, SIGNATURE,
                        "uniform int " + UNIFORM + ";\n" + SIGNATURE);
                if (declared != null) result = declared;
            }
        }
        // Iris removes unused output helpers from deferred programs. Validate their
        // read schema separately instead of requiring the writer to survive linking.
        if (readerSupported(result)) {
            String decoded = ModelPackLightAdapters.replaceUnique(result, STUB, decoder());
            if (decoded != null) decoded = ModelPackLightAdapters.replaceUnique(decoded, UV_STUB,
                    "void modelLightingSetReceiverUv(vec2 uv) { modelLightingReceiverSlotHint = modelLightingReceiverSlotAtUv(uv); }");
            if (decoded != null) decoded = ModelPackLightAdapters.replaceUnique(decoded, "void main() {",
                    "void main() {\nmodelLightingSetReceiverUv(texcoord.xy);");
            if (decoded != null) result = decoded;
        }
        return result;
    }

    private static boolean matches(String source, String tokens) {
        return source.replaceAll("\\s+", "").contains(tokens.replaceAll("\\s+", ""));
    }

    private static boolean readerSupported(String source) {
        return matches(source, "GBufferData GetGBufferData()")
                && matches(source, "UnpackTwo8BitFrom16Bit(tex1.w)")
                && matches(source, "data.totalTexGrad = unpacked1w.x;")
                && matches(source, "data.materialID = unpacked1z.x;")
                && !matches(source, "unpacked1w.y")
                && source.contains("colortex1");
    }

    private static String decoder() {
        return """
                int modelLightingReceiverSlotHint = -1;
                int modelLightingReceiverSlotAtUv(vec2 uv) {
                    if (any(lessThan(uv, vec2(0.0))) || any(greaterThanEqual(uv, vec2(1.0)))) return -1;
                    ivec2 dimensions = textureSize(colortex1, 0);
                    ivec2 pixel = clamp(ivec2(uv * vec2(dimensions)), ivec2(0), dimensions - ivec2(1));
                    float receiverPacked = texelFetch(colortex1, pixel, 0).w;
                    int tag = int(floor(receiverPacked * 65535.0 + 0.5)) & 255;
                    if (tag < 1 || tag > 128) return -1;
                    return tag - 1;
                }
                int modelLightingReceiverSlotAt(vec3 receiver) { return modelLightingReceiverSlotHint; }
                """;
    }

    public static Scope scope(int slot) {
        if (slot < -1 || slot > 127) throw new IllegalArgumentException("Receiver slot outside [-1,127]");
        int previous = currentId;
        currentId = slot + 1;
        return new Scope(previous);
    }

    public static boolean isActive() { return currentId != 0; }

    public static final class Scope implements AutoCloseable {
        private final int previous;
        private boolean closed;
        private Scope(int previous) { this.previous = previous; }
        public void close() { if (!closed) { currentId = previous; closed = true; } }
    }

    public static void draw(Runnable draw) {
        if (currentId == 0) { draw.run(); return; }
        int program = GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int location = LOCATIONS.computeIfAbsent(program, key -> GL20C.glGetUniformLocation(key, UNIFORM));
        if (location < 0) { draw.run(); return; }
        try {
            GL20C.glUniform1i(location, currentId);
            draw.run();
            taggedDraws++;
        } finally { GL20C.glUniform1i(location, 0); }
    }

    public static void deleted(int program) { LOCATIONS.remove(program); }
    public static Map<String, Object> report() {
        return Map.of("taggedDraws", taggedDraws,
                "writerPrograms", LOCATIONS.values().stream().filter(location -> location >= 0).count(),
                "scope", "SEUS E12 solid receivers; unsupported programs retain global settings");
    }

    private ModelLightingReceiverTag() {}
}
