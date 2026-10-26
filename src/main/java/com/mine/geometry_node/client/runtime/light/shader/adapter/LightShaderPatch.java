package com.mine.geometry_node.client.runtime.light.shader.adapter;

import com.mine.geometry_node.client.runtime.model.render.bvh.ModelPackLightAdapters;
import java.util.regex.Pattern;

/** Token-aware patch operations shared by pack adapters. */
public final class LightShaderPatch {
    private LightShaderPatch() {}

    public static String replace(String source, String anchor, String replacement) {
        return source == null ? null : ModelPackLightAdapters.replaceUnique(source, anchor, replacement);
    }

    /** Volume integration belongs to the pack's separate composite pass. */
    public static String surfacePrepass(String output, String position, String normal, String geometricNormal, String sky) {
        return "if(nodeLightPass!=0) { vec3 nodeP=" + position + "; "
                + output + "=vec4((" + sky + ") ? vec3(0.0) : nodeDirectGeo(nodeLightIndex,nodeP," + normal + "," + geometricNormal
                + "),length(nodeP)); return; }\n";
    }

    public static String output(String source, String value) {
        if (value.equals("FINAL_COLOR")) {
            var m = Pattern.compile("(gl_FragData\\s*\\[\\s*0\\s*\\]|\\w+)\\.rgb\\s*=\\s*FINAL_COLOR\\s*;").matcher(source);
            if (!m.find()) return null;
            String output = m.group(1);
            return m.find() ? null : output;
        }
        String marked = replace(source, value, "NODE_OUTPUT_SENTINEL");
        if (marked == null) return null;
        var m = Pattern.compile("(gl_FragData\\s*\\[\\s*0\\s*\\]|\\w+)\\s*=\\s*NODE_OUTPUT_SENTINEL").matcher(marked);
        return m.find() ? m.group(1) : null;
    }
}
