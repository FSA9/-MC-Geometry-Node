package com.mine.geometry_node.core.node.definition.node;

import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record NodeComment(
        List<String> textKeys,
        List<String> literalTexts,
        List<PortComment> outputs,
        List<PortComment> inputs
) {
    public static final NodeComment EMPTY = new NodeComment(List.of(), List.of(), List.of(), List.of());

    public NodeComment {
        textKeys = copyNonBlank(textKeys);
        literalTexts = copyNonBlank(literalTexts);
        outputs = copyNonEmpty(outputs);
        inputs = copyNonEmpty(inputs);
    }

    /** Preserves the original public constructor used by existing addons. */
    public NodeComment(List<String> textKeys, List<PortComment> outputs, List<PortComment> inputs) {
        this(textKeys, List.of(), outputs, inputs);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String nodeTypeId) {
        String keyPrefix = nodeTypeId == null || nodeTypeId.isBlank()
                ? ""
                : "geometry_node.node." + nodeTypeId.trim() + ".comment.";
        return new Builder(keyPrefix);
    }

    public boolean isEmpty() {
        return textKeys.isEmpty() && literalTexts.isEmpty()
                && outputs.isEmpty() && inputs.isEmpty();
    }

    private static List<String> copyNonBlank(@Nullable List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> copied = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                copied.add(value.trim());
            }
        }
        return copied.isEmpty() ? List.of() : List.copyOf(copied);
    }

    private static List<PortComment> copyNonEmpty(@Nullable List<PortComment> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<PortComment> copied = new ArrayList<>();
        for (PortComment value : values) {
            if (value != null && !value.isEmpty()) {
                copied.add(value);
            }
        }
        return copied.isEmpty() ? List.of() : List.copyOf(copied);
    }

    public record PortComment(String portId, String textKey) {
        public PortComment {
            portId = portId == null ? "" : portId.trim();
            textKey = textKey == null ? "" : textKey.trim();
        }

        public boolean isEmpty() {
            return portId.isBlank() || textKey.isBlank();
        }
    }

    public static final class Builder {
        private final String keyPrefix;
        private final List<String> textKeys = new ArrayList<>();
        private final List<String> literalTexts = new ArrayList<>();
        private final List<PortComment> outputs = new ArrayList<>();
        private final List<PortComment> inputs = new ArrayList<>();

        private Builder() {
            this("");
        }

        private Builder(String keyPrefix) {
            this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
        }

        public Builder text(String translationKey) {
            if (translationKey != null && !translationKey.isBlank()) {
                textKeys.add(resolveKey(translationKey));
            }
            return this;
        }

        /** Adds runtime-authored text, such as a node group's user comment. */
        public Builder literal(String text) {
            if (text != null && !text.isBlank()) {
                literalTexts.add(text.trim());
            }
            return this;
        }

        public Builder output(String portId, String translationKey) {
            outputs.add(new PortComment(portId, resolveKey(translationKey)));
            return this;
        }

        public Builder output(StandardPorts port, String translationKey) {
            return output(port == null ? "" : port.getId(), translationKey);
        }

        public Builder input(String portId, String translationKey) {
            inputs.add(new PortComment(portId, resolveKey(translationKey)));
            return this;
        }

        public Builder input(StandardPorts port, String translationKey) {
            return input(port == null ? "" : port.getId(), translationKey);
        }

        public NodeComment build() {
            return new NodeComment(textKeys, literalTexts, outputs, inputs);
        }

        private String resolveKey(String translationKey) {
            String key = translationKey == null ? "" : translationKey.trim();
            return keyPrefix.isBlank() || key.contains(".") ? key : keyPrefix + key;
        }
    }
}
