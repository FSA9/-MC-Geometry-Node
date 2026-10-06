package com.mine.geometry_node.client.model.render.pipeline;

import java.util.regex.Pattern;

/** Keep Sundial atlas wrapping for host geometry, but let standalone model samplers own wrapping. */
public final class ModelSundialMaterialSampling {
    public static final String UNIFORM = "geometry_node_materialSampler";
    private static final Pattern WRAP = Pattern.compile("vec2\\s+clampCoordRange\\s*\\(\\s*vec2\\s+coord\\s*,\\s*vec4\\s+coordRange\\s*\\)\\s*\\{\\s*return\\s+coordRange\\.xy\\s*\\+\\s*fract\\(coord\\)\\s*\\*\\s*coordRange\\.zw\\s*;\\s*}");
    public static String patch(String source) {
        if (source == null || source.contains("? coord : fract(coord)") || !source.contains("packUpGbufferDataSolid")
                || !source.contains("anisotropicFilter") || !source.contains("calcTbnMatrix")) return source;
        var match = WRAP.matcher(source);
        if (!match.find()) return source;
        if(match.find())return source;
        // A preceding normal adapter may declare the shared draw flag below this helper.
        source = source.replace("uniform int " + UNIFORM + ";", "");
        match = WRAP.matcher(source);
        match.find();
        int start=match.start(),end=match.end();
        return source.substring(0,start) + "uniform int " + UNIFORM + ";\n"
                + "vec2 clampCoordRange(vec2 coord, vec4 coordRange) {\n"
                + "    return coordRange.xy + (" + UNIFORM + " != 0 ? coord : fract(coord)) * coordRange.zw;\n}\n"
                + source.substring(end);
    }
    public static boolean patched(String source) { return source != null && source.contains("uniform int " + UNIFORM + ";"); }
    private ModelSundialMaterialSampling() {}
}
