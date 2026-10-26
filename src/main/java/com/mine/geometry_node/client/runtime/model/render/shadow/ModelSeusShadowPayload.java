package com.mine.geometry_node.client.runtime.model.render.shadow;

import java.util.regex.Pattern;

/** Keeps unknown shader input uses on the full-width reconstruction path. */
final class ModelSeusShadowPayload {
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\r\\n]*", Pattern.DOTALL);
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{");
    private static final Pattern SWIZZLE = Pattern.compile("\\s*\\.\\s*([A-Za-z_][A-Za-z_0-9]*)");

    static int components(String fragment, String input, int reducedComponents) {
        if (reducedComponents != 2 && reducedComponents != 3) {
            throw new IllegalArgumentException("Expected two or three payload components");
        }
        String source = COMMENTS.matcher(fragment).replaceAll(" ");
        var main = MAIN.matcher(source);
        if (!main.find()) return 4;
        int bodyStart = main.end();
        if (main.find()) return 4;
        String body = source.substring(bodyStart);
        var uses = Pattern.compile("\\b" + Pattern.quote(input) + "\\b").matcher(body);
        String allowed = reducedComponents == 2 ? "xyrgst" : "xyzrgbstp";
        while (uses.find()) {
            var swizzle = SWIZZLE.matcher(body).region(uses.end(), body.length());
            if (!swizzle.lookingAt()) return 4;
            String fields = swizzle.group(1);
            if (fields.length() > 4) return 4;
            for (int i = 0; i < fields.length(); i++) {
                if (allowed.indexOf(fields.charAt(i)) < 0) return 4;
            }
        }
        return reducedComponents;
    }

    private ModelSeusShadowPayload() {}
}
