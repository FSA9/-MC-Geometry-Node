package com.mine.geometry_node.core.engine.dialogue.model;

/**
 * Immutable server-side payload for one selectable dialogue choice.
 */
public record DialogueChoicePayload(
        String id,
        DialogueText text,
        Action action,
        boolean enabled,
        DialogueText disabledReason
) {
    public DialogueChoicePayload {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Choice id must not be blank");
        }
        text = text == null ? DialogueText.EMPTY : text;
        if (action == null) {
            throw new IllegalArgumentException("Choice action must not be null");
        }
        disabledReason = disabledReason == null ? DialogueText.EMPTY : disabledReason;
    }

    private static final String INTERNAL_CHOICE_NAMESPACE = "geometry_node:internal.";

    public static String continuePageChoiceId(int pageIndex) {
        if (pageIndex < 0) {
            throw new IllegalArgumentException("Page index must be non-negative");
        }
        return INTERNAL_CHOICE_NAMESPACE + "continue_page." + pageIndex;
    }

    /**
     * Server-owned behavior associated with a client-visible choice id.
     */
    public sealed interface Action permits ResumePort, AdvancePage {
    }

    public record ResumePort(String outputPortId) implements Action {
        public ResumePort {
            if (outputPortId == null || outputPortId.isBlank()) {
                throw new IllegalArgumentException("Output port id must not be blank");
            }
        }
    }

    public record AdvancePage(int expectedPageIndex) implements Action {
        public AdvancePage {
            if (expectedPageIndex < 0) {
                throw new IllegalArgumentException("Expected page index must be non-negative");
            }
        }
    }
}
