package com.mine.geometry_node.core.engine.system.model.importer.glb;

import com.mine.geometry_node.core.engine.system.model.domain.ModelAttributeSemantic;

/** Parses glTF attribute names without leaking format syntax into the canonical domain. */
final class GlbAttributeSemanticParser {
    private GlbAttributeSemanticParser() {}

    static ModelAttributeSemantic parse(String value) {
        return switch (value) {
            case "POSITION" -> ModelAttributeSemantic.POSITION;
            case "NORMAL" -> ModelAttributeSemantic.NORMAL;
            case "TANGENT" -> ModelAttributeSemantic.TANGENT;
            default -> parseIndexed(value);
        };
    }

    private static ModelAttributeSemantic parseIndexed(String value) {
        for (ModelAttributeSemantic.Kind kind : new ModelAttributeSemantic.Kind[]{
                ModelAttributeSemantic.Kind.TEXCOORD, ModelAttributeSemantic.Kind.COLOR,
                ModelAttributeSemantic.Kind.JOINTS, ModelAttributeSemantic.Kind.WEIGHTS}) {
            String prefix = kind.name() + "_";
            if (!value.startsWith(prefix)) continue;
            String suffix = value.substring(prefix.length());
            if (suffix.isEmpty() || suffix.length() > 1 && suffix.charAt(0) == '0'
                    || !suffix.chars().allMatch(Character::isDigit)) return null;
            try {
                return ModelAttributeSemantic.indexed(kind, Integer.parseInt(suffix));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
