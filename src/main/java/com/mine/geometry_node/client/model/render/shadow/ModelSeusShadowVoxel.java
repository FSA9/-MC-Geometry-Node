package com.mine.geometry_node.client.model.render.shadow;

import com.mine.geometry_node.client.model.render.bvh.ModelPackLightAdapters;

/** SEUS E12's block-face voxel encoder cannot encode arbitrary entity/model triangles. */
public final class ModelSeusShadowVoxel {
    private static final String FLAG = ModelSundialShadowUv.UNIFORM;
    public static String patch(String geometry) {
        if (geometry == null || geometry.contains(FLAG)
                || !geometry.contains("RvuBMHyIay") || !geometry.contains("tWOewJyFfe")
                || !geometry.contains("MtqeGbdCLv") || !geometry.contains("PnlUBUYgWr")) return geometry;
        // The first emitted primitive is the ordinary sun shadow. Only gate the second, voxel primitive.
        String result = ModelPackLightAdapters.replaceUnique(geometry, "bool valid = true;",
                "bool valid = " + FLAG + " == 0;");
        if (result == null) return geometry;
        var main = java.util.regex.Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{").matcher(result);
        if (!main.find()) return geometry;
        int start = main.start();
        if (main.find()) return geometry;
        return result.substring(0, start) + "uniform int " + FLAG + ";\n" + result.substring(start);
    }
    public static boolean patched(String geometry) {
        return geometry != null && geometry.contains("bool valid = " + FLAG + " == 0;");
    }
    private ModelSeusShadowVoxel() {}
}
