package com.mine.geometry_node.client.model.render;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sundial's vanilla UV sentinel is not valid for glTF repeat coordinates. */
public final class ModelSundialShadowUv {
    public static final String UNIFORM = "geometry_node_repeatShadowUv";
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\r\\n]*", Pattern.DOTALL);
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{");
    private static final Pattern SENTINEL = Pattern.compile("\\bgl_Position\\s*\\.\\s*z\\s*\\+=([^;]+);");
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w.])(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?[fF]?(?![\\w.])");
    private static final String EXPECTED = canonical("1e+6 * clamp(abs(texcoord.y - 0.5) - 2.0, 0.0, 1.0)");
    public static String patch(String source) {
        if (source == null || source.contains(UNIFORM)) return source;
        Matcher comments = COMMENTS.matcher(source);
        StringBuilder clean = new StringBuilder();
        while (comments.find()) comments.appendReplacement(clean, " ".repeat(comments.end() - comments.start()));
        comments.appendTail(clean);
        Matcher main = MAIN.matcher(clean), sentinel = SENTINEL.matcher(clean);
        if (!main.find()) return source;
        int mainStart = main.start(), mainEnd = main.end();
        if (main.find()) return source;
        int start = -1;
        while (sentinel.find()) {
            if (!canonical(sentinel.group(1)).equals(EXPECTED)) continue;
            if (start >= 0 || sentinel.start() < mainEnd) return source;
            start = sentinel.start();
        }
        if (start < 0) return source;
        String result = source.substring(0, start) + "if (" + UNIFORM + " == 0) " + source.substring(start);
        return result.substring(0, mainStart) + "uniform int " + UNIFORM + ";\n" + result.substring(mainStart);
    }
    public static boolean patched(String source) {
        return source != null && source.contains("uniform int " + UNIFORM + ";")
                && source.contains("if (" + UNIFORM + " == 0)");
    }
    private static String canonical(String source) {
        Matcher numbers = NUMBER.matcher(source);
        StringBuilder result = new StringBuilder();
        while (numbers.find()) numbers.appendReplacement(result, Matcher.quoteReplacement(
                new BigDecimal(numbers.group().replaceAll("[fF]$", "")).stripTrailingZeros().toPlainString()));
        return numbers.appendTail(result).toString().replaceAll("\\s+", "");
    }
    private ModelSundialShadowUv() {}
}
