package com.mine.geometry_node.core.engine.system.dialogue;

import com.mine.geometry_node.core.engine.system.dialogue.model.DialoguePagePayload;
import com.mine.geometry_node.core.engine.blueprint.runtime.wait.BlueprintExternalWaitRequest;

import java.util.List;
import java.util.Objects;

/**
 * Request emitted while graph execution waits for a dialogue page sequence.
 * Only a choice on the final page resumes graph execution.
 */
public record DialogueWaitRequest(
        DialogueContext context,
        List<DialoguePagePayload> pages
) implements BlueprintExternalWaitRequest {
    public DialogueWaitRequest {
        Objects.requireNonNull(context, "context");
        pages = List.copyOf(Objects.requireNonNull(pages, "pages"));
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("Dialogue wait request requires at least one page");
        }
    }

    public DialogueWaitRequest(DialogueContext context, DialoguePagePayload page) {
        this(context, List.of(Objects.requireNonNull(page, "page")));
    }

    public DialoguePagePayload page() {
        return pages.get(0);
    }
}
