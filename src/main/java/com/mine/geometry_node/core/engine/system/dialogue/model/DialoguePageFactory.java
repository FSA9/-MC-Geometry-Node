package com.mine.geometry_node.core.engine.system.dialogue.model;

import com.mine.geometry_node.core.engine.system.dialogue.richtext.DialogueRoundParser;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.value.RichTextValue;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Builds immutable dialogue pages from authored values without executing a graph. */
public final class DialoguePageFactory {
    private DialoguePageFactory() {
    }

    public static List<DialoguePagePayload> textRounds(String basePageId,
                                                       RichTextValue body,
                                                       String styleId) {
        List<RichTextValue> rounds = DialogueRoundParser.split(body);
        List<DialoguePagePayload> pages = new ArrayList<>(rounds.size());
        String safeBasePageId = basePageId == null ? "" : basePageId;
        for (int i = 0; i < rounds.size(); i++) {
            String pageId = rounds.size() == 1
                    ? safeBasePageId
                    : safeBasePageId + ":round:" + (i + 1);
            String choiceId = i == rounds.size() - 1
                    ? StandardPorts.FLOW_OUT.getId()
                    : DialogueChoicePayload.continuePageChoiceId(i);
            DialogueChoicePayload.Action action = i == rounds.size() - 1
                    ? new DialogueChoicePayload.ResumePort(StandardPorts.FLOW_OUT.getId())
                    : new DialogueChoicePayload.AdvancePage(i);
            pages.add(DialoguePagePayload.text(
                    pageId,
                    DialogueText.rich(rounds.get(i)),
                    styleId,
                    List.of(continueChoice(choiceId, action))
            ));
        }
        return List.copyOf(pages);
    }

    private static DialogueChoicePayload continueChoice(String choiceId,
                                                         DialogueChoicePayload.Action action) {
        return new DialogueChoicePayload(
                choiceId,
                DialogueText.component(Component.translatable("geometry_node.dialogue.continue")),
                action,
                true,
                DialogueText.EMPTY
        );
    }
}
