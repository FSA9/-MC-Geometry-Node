package com.mine.geometry_node.client.model.render;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative recognition of observed Iris/pack alpha tests; never rewrites pack source. */
public final class ModelIrisAlphaSource {
    public enum Comparison { GREATER, GREATER_OR_EQUAL, UNSUPPORTED }
    public record Threshold(String uniformName, Comparison comparison, float minimumAlpha) {}
    public record Profile(Comparison comparison, float minimumAlpha, String reason, String uniformName,
                          List<Threshold> thresholds) {
        public Profile { thresholds = List.copyOf(thresholds); }
        public boolean supports(ModelIrisAlphaPolicy policy) {
            return comparison != Comparison.UNSUPPORTED
                    && (policy.mode() == ModelIrisAlphaPolicy.Mode.MASK ? policy.cutoff() : 0) >= minimumAlpha;
        }
    }
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\r\\n]*", Pattern.DOTALL);
    private static final Pattern UNIFORM = Pattern.compile("\\buniform\\s+float\\s+iris_currentAlphaTest\\s*;");
    private static final Pattern REFERENCE = Pattern.compile("\\biris_currentAlphaTest\\b");
    private static final Pattern TEST = Pattern.compile(
            "if\\s*\\(\\s*!\\s*\\(\\s*iris_FragData0\\s*\\.\\s*a\\s*(>=|>)\\s*iris_currentAlphaTest\\s*\\)\\s*\\)\\s*\\{\\s*discard\\s*;\\s*\\}");
    private static final String NUMBER = "(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?[fF]?";
    private static final String THRESHOLD = "(?:iris_currentAlphaTest|max\\s*\\(\\s*(?:" + NUMBER
            + "\\s*,\\s*iris_currentAlphaTest|iris_currentAlphaTest\\s*,\\s*" + NUMBER + ")\\s*\\))";
    private static final String ALPHA = "[A-Za-z_]\\w*\\s*\\.\\s*[aw]\\s*<\\s*(" + THRESHOLD + ")";
    private static final String DISCARD = "(?:discard\\s*;|\\{\\s*discard\\s*;\\s*\\})";
    private static final Pattern CORE_TEST = Pattern.compile("if\\s*\\(\\s*" + ALPHA + "\\s*\\)\\s*" + DISCARD);
    // Sundial Lite 1.1.0 Shadow.frag: preserve both unrelated geometry guards verbatim.
    // Iris AST printing appends f to floating literals; accept only that spelling variation.
    // Do not generalize arbitrary boolean expressions: &&, negation, or different grouping can invert the test.
    private static final Pattern SUNDIAL_SHADOW_TEST = Pattern.compile(
            "if\\s*\\(\\s*any\\s*\\(\\s*greaterThan\\s*\\(\\s*abs\\s*\\(\\s*centerTexelOffset\\s*\\)"
            + "\\s*,\\s*vec2\\s*\\(\\s*realShadowMapResolution\\s*\\*\\s*0\\.25[fF]?\\s*\\)\\s*\\)\\s*\\)"
            + "\\s*\\|\\|\\s*fwidth\\s*\\(\\s*shadowOffset\\s*\\.\\s*x\\s*\\)\\s*>\\s*0\\.0[fF]?"
            + "\\s*\\|\\|\\s*" + ALPHA + "\\s*\\)\\s*" + DISCARD);
    private static final Pattern FLOOR = Pattern.compile(NUMBER);
    private static Profile unknown(String reason) {
        return new Profile(Comparison.UNSUPPORTED, 0, reason, "", List.of());
    }

    public static Comparison comparison(String source) { return profile(source).comparison(); }

    public static Profile profile(String source) {
        if (source == null) return unknown("No fragment source");
        String clean = COMMENTS.matcher(source).replaceAll(" ");
        List<Threshold> thresholds = new ArrayList<>();
        for (String name : List.of("iris_currentAlphaTest", "alphaTestRef")) {
            if (!Pattern.compile("\\b" + name + "\\b").matcher(clean).find()) continue;
            // Analyze each independent threshold separately, including when Iris appended an
            // output test in addition to the pack's sampled-alpha test.
            String canonical = clean;
            if (name.equals("alphaTestRef")) canonical = canonical.replaceAll("\\biris_currentAlphaTest\\b", "otherAlphaThreshold")
                    .replaceAll("\\balphaTestRef\\b", "iris_currentAlphaTest");
            if (!UNIFORM.matcher(canonical).find()) return unknown("Missing scalar declaration: " + name);
            String remaining = UNIFORM.matcher(canonical).replaceAll(" ");
            Comparison comparison = null;
            float floor = 0;
            int uses = 0;
            for (Pattern pattern : List.of(TEST, CORE_TEST, SUNDIAL_SHADOW_TEST)) {
                Matcher match = pattern.matcher(remaining);
                StringBuffer rest = new StringBuffer();
                while (match.find()) {
                    Comparison current = pattern == TEST && ">".equals(match.group(1))
                            ? Comparison.GREATER : Comparison.GREATER_OR_EQUAL;
                    if (comparison != null && comparison != current)
                        return unknown("Mixed comparisons sharing threshold: " + name);
                    comparison = current;
                    if (pattern != TEST && match.group(1).startsWith("max")) {
                        Matcher number = FLOOR.matcher(match.group(1));
                        if (!number.find()) return unknown("Unknown alpha floor: " + name);
                        float value = Float.parseFloat(number.group());
                        if (!Float.isFinite(value) || value < 0) return unknown("Invalid alpha floor: " + name);
                        floor = Math.max(floor, value);
                    }
                    uses++;
                    match.appendReplacement(rest, " ");
                }
                match.appendTail(rest);
                remaining = rest.toString();
            }
            Matcher unrecognized = REFERENCE.matcher(remaining);
            if (unrecognized.find()) {
                String context = remaining.substring(Math.max(0, unrecognized.start() - 180),
                        Math.min(remaining.length(), unrecognized.end() + 120)).replaceAll("\\s+", " ");
                return unknown("Unrecognized threshold use: " + name + "; source=" + context);
            }
            // An unused declaration is harmless and may be removed by the linker.
            if (uses > 0) thresholds.add(new Threshold(name, comparison, floor));
        }
        if (thresholds.isEmpty()) return unknown("No recognized live alpha comparison");
        Threshold first = thresholds.getFirst();
        float floor = 0;
        for (Threshold threshold : thresholds) floor = Math.max(floor, threshold.minimumAlpha());
        return new Profile(first.comparison(), floor, "Recognized independent alpha thresholds: " + thresholds,
                first.uniformName(), thresholds);
    }

    /** glTF keeps alpha == cutoff; Iris's GREATER comparison needs the preceding float. */
    public static float threshold(float cutoff, Comparison comparison) {
        if (!Float.isFinite(cutoff) || cutoff < 0) throw new IllegalArgumentException("Invalid glTF alpha cutoff");
        return switch (comparison) {
            // -1 also avoids flushing nextDown(0)'s subnormal to negative zero on GPUs.
            case GREATER -> cutoff == 0 ? -1.0f : Math.nextDown(cutoff);
            case GREATER_OR_EQUAL -> cutoff;
            case UNSUPPORTED -> throw new IllegalArgumentException("Unknown shader alpha comparison");
        };
    }

    private ModelIrisAlphaSource() {}
}
