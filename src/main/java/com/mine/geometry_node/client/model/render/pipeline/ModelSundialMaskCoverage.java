package com.mine.geometry_node.client.model.render.pipeline;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Narrow opt-in correction for Sundial's sampled-alpha -> solid Gbuffer material handoff. */
public final class ModelSundialMaskCoverage {
    public static final String UNIFORM = "geometry_node_maskCoverage";
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\r\\n]*", Pattern.DOTALL);
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{");
    private static final Pattern PACK = Pattern.compile("\\bpackUpGbufferDataSolid\\s*\\(\\s*rawData\\s*,\\s*gbufferData0\\s*,\\s*gbufferData1\\s*,\\s*gbufferData2\\s*\\)\\s*;");
    private static final Pattern HANDOFF = Pattern.compile(
            "if\\s*\\(\\s*albedoData\\s*\\.\\s*w\\s*<\\s*max\\s*\\(\\s*0\\.001[fF]?\\s*,\\s*(?:alphaTestRef|iris_currentAlphaTest)\\s*\\)\\s*\\)\\s*(?:discard\\s*;|\\{\\s*discard\\s*;\\s*\\})"
            + "(\\s*)rawData\\s*\\.\\s*albedo\\s*=\\s*albedoData\\s*;");

    public static String patch(String source) {
        if (source == null || source.contains(UNIFORM)) return source;
        // Preserve character offsets and #version placement; commented lookalikes cannot match.
        Matcher comments = COMMENTS.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (comments.find()) comments.appendReplacement(buffer, " ".repeat(comments.end() - comments.start()));
        comments.appendTail(buffer);
        String clean = buffer.toString();
        Matcher main = MAIN.matcher(clean), handoff = HANDOFF.matcher(clean), pack = PACK.matcher(clean);
        if (!main.find() || !handoff.find() || !pack.find()) return source;
        int mainStart = main.start(), mainBody = main.end(), insert = handoff.start(1), handoffEnd = handoff.end(), packAt = pack.start();
        if (insert < mainBody || packAt < handoffEnd || main.find() || handoff.find() || pack.find()) return source;
        String normalized = source.substring(0, insert) + "\nif (" + UNIFORM + " != 0) albedoData.w = 1.0;\n" + source.substring(insert);
        return normalized.substring(0, mainStart) + "uniform int " + UNIFORM + ";\n" + normalized.substring(mainStart);
    }

    public static boolean patched(String source) {
        return source != null && source.contains("uniform int " + UNIFORM + ";")
                && source.contains("if (" + UNIFORM + " != 0) albedoData.w = 1.0;");
    }

    private ModelSundialMaskCoverage() {}
}
