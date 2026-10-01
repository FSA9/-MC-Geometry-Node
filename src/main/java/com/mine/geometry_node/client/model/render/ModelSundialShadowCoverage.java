package com.mine.geometry_node.client.model.render;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Atomic, known-source Sundial MASK adaptation: opaque atlas plus per-pixel cutout. */
public final class ModelSundialShadowCoverage {
    public record Sources(String vertex, String fragment) {}
    private static final String FLAG = ModelSundialMaskCoverage.UNIFORM;
    private static final String VERTEX_SWITCH = "if (" + FLAG + " != 0) isTransparent = 0.0;";
    private static final String FRAGMENT_SWITCH = "if (" + FLAG + " != 0) albedo.w = 1.0;";
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\r\\n]*", Pattern.DOTALL);
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{");
    private static final Pattern VERTEX = Pattern.compile("float\\s+isTransparent\\s*=([^;]+);(\\s*)shadowOffset\\s*\\.\\s*x\\s*=\\s*-isTransparent\\s*;\\s*color\\s*\\.\\s*a\\s*\\*=([^;]+);");
    private static final Pattern OFFSET = Pattern.compile("vec2\\s+shadowOffset\\s*=([^;]+);");
    private static final Pattern OUTPUT = Pattern.compile("\\bshadowColor0\\s*=\\s*albedo\\s*;");
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w.])(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?[fF]?(?![\\w.])");
    private static final String CLASSIFICATION = canonical("float(abs(textureLod(gtexture, mc_midTexCoord.st + 1e-6, 0.0).w - 0.5) + 1e-4 < 0.49) * (1.0 - isWaterF)");
    private static final String SIGN = canonical("1.0 - 2.0 * isTransparent * (1.0 - isWaterF)");
    private static final String ATLAS = canonical("vec2(-float(abs(color.a + 0.5) <= 0.5), -float(color.a < -1.0)) * 0.5 * realShadowMapResolution");

    public static Sources patch(String vertex, String fragment) {
        Sources original = new Sources(vertex, fragment);
        if (vertex == null || fragment == null || vertex.contains(FLAG) || fragment.contains(FLAG)) return original;
        String v = uncomment(vertex), f = uncomment(fragment);
        Matcher vm = MAIN.matcher(v), fm = MAIN.matcher(f), classifier = VERTEX.matcher(v);
        Matcher offset = OFFSET.matcher(f), output = OUTPUT.matcher(f);
        if (!vm.find() || !fm.find() || !classifier.find() || !offset.find() || !output.find()) return original;
        if (!canonical(classifier.group(1)).equals(CLASSIFICATION)
                || !canonical(classifier.group(3)).equals(SIGN) || !canonical(offset.group(1)).equals(ATLAS)) return original;
        int vMain = vm.start(), fMain = fm.start(), vInsert = classifier.start(2), fInsert = output.start();
        if (classifier.start() < vm.end() || offset.start() < fm.end() || offset.end() >= fInsert
                || vm.find() || fm.find() || classifier.find() || offset.find() || output.find()) return original;
        var profile = ModelIrisAlphaSource.profile(f);
        if (profile.comparison() == ModelIrisAlphaSource.Comparison.UNSUPPORTED
                || profile.thresholds().stream().noneMatch(t -> t.minimumAlpha() == .01f)) return original;
        String patchedVertex = inject(vertex, vMain, vInsert, VERTEX_SWITCH);
        String patchedFragment = inject(fragment, fMain, fInsert, FRAGMENT_SWITCH);
        return new Sources(patchedVertex, patchedFragment);
    }

    public static boolean patchedFragment(String source) {
        return source != null && source.contains("uniform int " + FLAG + ";") && source.contains(FRAGMENT_SWITCH);
    }

    private static String inject(String source, int main, int insertion, String statement) {
        String result = source.substring(0, insertion) + "\n" + statement + "\n" + source.substring(insertion);
        return result.substring(0, main) + "uniform int " + FLAG + ";\n" + result.substring(main);
    }

    private static String uncomment(String source) {
        Matcher matcher = COMMENTS.matcher(source);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(result, " ".repeat(matcher.end() - matcher.start()));
        return matcher.appendTail(result).toString();
    }

    /** Accept AST numeric spelling differences only; identifiers/operators remain exact. */
    private static String canonical(String source) {
        Matcher matcher = NUMBER.matcher(source);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String number = matcher.group().replaceAll("[fF]$", "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(new BigDecimal(number).stripTrailingZeros().toPlainString()));
        }
        return matcher.appendTail(result).toString().replaceAll("\\s+", "");
    }

    private ModelSundialShadowCoverage() {}
}
