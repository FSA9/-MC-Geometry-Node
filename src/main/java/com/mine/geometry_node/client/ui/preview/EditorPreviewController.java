package com.mine.geometry_node.client.ui.preview;

import com.mine.geometry_node.client.runtime.dialogue.ClientDialogueState;
import com.mine.geometry_node.client.runtime.quest.ClientQuestScreenState;
import com.mine.geometry_node.core.engine.system.dialogue.DialogueStyleRegistry;
import com.mine.geometry_node.core.engine.system.dialogue.model.DialogueChoicePayload;
import com.mine.geometry_node.core.engine.system.dialogue.model.DialoguePageFactory;
import com.mine.geometry_node.core.engine.system.dialogue.model.DialoguePagePayload;
import com.mine.geometry_node.core.engine.system.dialogue.model.DialogueText;
import com.mine.geometry_node.core.engine.system.dialogue.model.shop.ShopPagePayload;
import com.mine.geometry_node.core.engine.system.dialogue.model.shop.ShopPagePayloadFactory;
import com.mine.geometry_node.core.engine.system.quest.QuestScreenViewFactory;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionOverview;
import com.mine.geometry_node.core.engine.system.quest.status.QuestStatusRegistry;
import com.mine.geometry_node.core.network.packet.s2c.PacketOpenDialogue;
import com.mine.geometry_node.core.network.packet.s2c.PacketQuestScreenSnapshot;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.nodes.dialogue.OpenShop;
import com.mine.geometry_node.core.node.nodes.dialogue.ShowDialoguePage;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.value.RichTextValue;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Client-only entrypoint for side-effect-free editor previews. */
public final class EditorPreviewController {
    private EditorPreviewController() {
    }

    public static boolean previewDialogue(NodeData node) {
        if (node == null || !ShowDialoguePage.TYPE_ID.equals(node.type)) {
            return false;
        }
        RichTextValue body = RichTextValue.from(input(node, ShowDialoguePage.TEXT));
        List<DialoguePagePayload> pages = DialoguePageFactory.textRounds(
                "preview:dialogue:" + safeNodeId(node),
                body,
                DialogueStyleRegistry.RPG
        );
        return ClientDialogueState.openPreview(toPackets(
                pages,
                Component.translatable("geometry_node.preview.dialogue.speaker")
        ));
    }

    public static boolean previewShop(NodeData node) {
        if (node == null || !OpenShop.TYPE_ID.equals(node.type)) {
            return false;
        }
        String title = stringValue(input(node, OpenShop.TITLE), "");
        String shopId = stringValue(input(node, OpenShop.SHOP_ID), "").trim();
        if (shopId.isEmpty()) {
            shopId = "preview:" + safeNodeId(node);
        }
        Map<String, Boolean> conditions = previewShopConditions(node);
        Object rawShopData = input(node, OpenShop.SHOP_DATA);
        ShopPagePayload shop = ShopPagePayloadFactory.create(
                rawShopData instanceof Map<?, ?> map ? map : Map.of(),
                conditions,
                shopId,
                title,
                ignored -> 0
        );
        DialoguePagePayload page = DialoguePagePayload.shop(
                "preview:shop:" + safeNodeId(node),
                shop,
                List.of(new DialogueChoicePayload(
                        StandardPorts.FLOW_OUT.getId(),
                        DialogueText.EMPTY,
                        new DialogueChoicePayload.ResumePort(StandardPorts.FLOW_OUT.getId()),
                        true,
                        DialogueText.EMPTY
                ))
        );
        return ClientDialogueState.openPreview(toPackets(List.of(page), Component.empty()));
    }

    public static boolean previewQuest(String taskKey, QuestDefinition definition,
                                       QuestConditionOverview conditionOverview) {
        QuestDefinition safeDefinition = definition == null ? QuestDefinition.EMPTY : definition;
        String safeTaskKey = normalizeTaskKey(taskKey);
        PacketQuestScreenSnapshot.QuestView quest = QuestScreenViewFactory.quest(
                safeTaskKey,
                "preview",
                QuestStatusRegistry.IN_PROGRESS.id(),
                false,
                0L,
                safeDefinition,
                conditionOverview,
                null,
                ignored -> 0.0
        );
        return ClientQuestScreenState.openPreview(new PacketQuestScreenSnapshot(
                QuestScreenViewFactory.statuses(),
                List.of(quest),
                true,
                true,
                "",
                ""
        ));
    }

    private static List<PacketOpenDialogue> toPackets(List<DialoguePagePayload> pages, Component speaker) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }
        UUID sessionId = UUID.randomUUID();
        List<PacketOpenDialogue> packets = new ArrayList<>(pages.size());
        for (DialoguePagePayload page : pages) {
            packets.add(PacketOpenDialogue.fromPage(sessionId, speaker, page));
        }
        return List.copyOf(packets);
    }

    private static Map<String, Boolean> previewShopConditions(NodeData node) {
        int count = boundedCount(input(node, StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id()));
        Map<String, Boolean> result = new LinkedHashMap<>();
        // Preview does not evaluate graph data flow; expose every authored offer for layout inspection.
        for (int i = 1; i <= count; i++) {
            String portId = StandardPorts.BOOL.getIdWithIndex(i);
            result.put(portId, true);
        }
        return result;
    }

    private static Object input(NodeData node, String key) {
        return node.inputs == null ? null : node.inputs.get(key);
    }

    private static int boundedCount(Object value) {
        int count = 0;
        if (value instanceof Number number) {
            count = number.intValue();
        } else if (value instanceof String string) {
            try {
                count = Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(0, Math.min(count, OpenShop.MAX_CONDITION_INPUTS));
    }

    private static String stringValue(Object value, String fallback) {
        return value instanceof String string ? string : fallback;
    }

    private static String safeNodeId(NodeData node) {
        return node.id == null || node.id.isBlank() ? "node" : node.id;
    }

    private static String normalizeTaskKey(String taskKey) {
        String value = taskKey == null ? "" : taskKey.trim();
        if (value.toLowerCase(Locale.ROOT).endsWith(".json")) {
            value = value.substring(0, value.length() - 5);
        }
        return value.isBlank() ? "preview" : value;
    }
}
