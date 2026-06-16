package com.mine.geometry_node.core.node.nodes.dialogue;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.dialogue.context.DialogueContext;
import com.mine.geometry_node.core.engine.dialogue.payload.DialogueChoicePayload;
import com.mine.geometry_node.core.engine.dialogue.payload.DialoguePagePayload;
import com.mine.geometry_node.core.engine.dialogue.payload.DialogueWaitRequest;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenShop extends BaseNode {
    public static final String TYPE_ID = "open_shop";
    public static final String PLAYER = StandardPorts.PLAYER.getId();
    public static final String TITLE = StandardPorts.TITLE.getId();
    public static final String SHOP_DATA = StandardPorts.SHOP_DATA.getId();
    public static final String TEMP_SHOP_DATA = "open_shop_data";

    private static final String STYLE_SHOP = "shop";
    private static final String ACTION_OPEN_SHOP_EDITOR = "open_shop_editor";
    private static final Map<String, Object> DEFAULT_SHOP_DATA = Map.of("offers", List.of());
    private static final String OFFERS = "offers";
    private static final String MAX_USES = "max_uses";
    private static final String USES = "uses";
    private static final String CONSUME_SELLER_ITEMS = "consume_seller_items";
    private static final String SELLER_RECEIVES_PAYMENT = "seller_receives_payment";
    private static final String VISIBLE_CONDITION = "visible_condition";
    private static final String ENABLED_CONDITION = "enabled_condition";
    private static final String DISABLED_REASON = "disabled_reason";
    public static final int MAX_CONDITION_INPUTS = 16;

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDef(0);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        return buildDef(resolveConditionInputCount(instanceData));
    }

    private NodeDef buildDef(int conditionInputCount) {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.DIALOGUE, Component.translatable("geometry_node.node.open_shop"))
                .addMeta(SchemaKeys.MIN_DYNAMIC_INPUT, 0)
                .addMeta(SchemaKeys.MAX_DYNAMIC_INPUT, MAX_CONDITION_INPUTS)
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.PLAYER.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.TITLE.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(
                        StandardPorts.SHOP_DATA.toInput(DEFAULT_SHOP_DATA).hiddenPin(),
                        null,
                        UIHint.BUTTON,
                        null,
                        Map.of(
                                PortMetaKeys.BUTTON_LABEL, "geometry_node.button.edit_shop",
                                PortMetaKeys.BUTTON_ACTION, ACTION_OPEN_SHOP_EDITOR,
                                PortMetaKeys.BUTTON_COLOR, 0xFF3D6EA8,
                                PortMetaKeys.BUTTON_TEXT_COLOR, 0xFFFFFFFF
                        )
        ));

        for (int i = 1; i <= conditionInputCount; i++) {
            builder.addRow(new PortRow(
                    StandardPorts.BOOL.toInputWithIndex(i, false),
                    null,
                    UIHint.CHECKBOX,
                    null,
                    Map.of(PortMetaKeys.IS_DYNAMIC, true, PortMetaKeys.DYNAMIC_INDEX, i)
            ));
        }

        return builder.build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ServerPlayer player = resolvePlayer(context);
        Map<String, Object> shopData = normalizeMap(getInputDict(context, SHOP_DATA));
        String title = getInput(context, TITLE, String.class);
        String safeTitle = title == null ? "" : title;

        Map<String, Object> state = new HashMap<>();
        if (player != null) {
            state.put("player", player);
        }
        state.put("title", safeTitle);
        state.put("shop_data", shopData);
        context.setTempData(TEMP_SHOP_DATA, state);

        if (player == null) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        DialogueContext dialogueContext = createShopDialogueContext(context, player, safeTitle);
        Map<String, Boolean> conditionValues = evaluateConditionInputs(context);
        Map<String, Object> displayShopData = resolveDisplayShopData(shopData, conditionValues);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", safeTitle);
        metadata.put("shop_data", displayShopData);

        DialoguePagePayload page = new DialoguePagePayload(
                "shop:" + context.getCurrentNodeId(),
                safeTitle,
                "",
                STYLE_SHOP,
                List.of(new DialogueChoicePayload(
                        StandardPorts.FLOW_OUT.getId(),
                        "",
                        null,
                        true,
                        null,
                        Map.of("role", "continue")
                )),
                metadata
        );
        return ExecutionResult.externalWait(GraphKind.DIALOGUE, new DialogueWaitRequest(dialogueContext, page));
    }

    private Map<String, Boolean> evaluateConditionInputs(ExecutionContext context) {
        int count = resolveConditionInputCount(context.getStaticInput(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id()));
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (int i = 1; i <= count; i++) {
            String portId = StandardPorts.BOOL.getIdWithIndex(i);
            Boolean value = getInput(context, portId, Boolean.class);
            result.put(portId, value != null && value);
        }
        return result;
    }

    private static Map<String, Object> resolveDisplayShopData(Map<String, Object> shopData, Map<String, Boolean> conditionValues) {
        Map<String, Object> display = normalizePlainMap(shopData);
        Object offersObj = shopData.get(OFFERS);
        if (!(offersObj instanceof List<?> offers)) {
            display.put(OFFERS, List.of());
            return display;
        }

        List<Object> displayOffers = new ArrayList<>();
        for (Object offerObj : offers) {
            if (!(offerObj instanceof Map<?, ?> rawOffer)) {
                continue;
            }
            Map<String, Object> offer = normalizePlainMap(rawOffer);
            String visibleCondition = stringValue(offer.get(VISIBLE_CONDITION), "");
            if (!visibleCondition.isBlank() && !Boolean.TRUE.equals(conditionValues.get(visibleCondition))) {
                continue;
            }

            String enabledCondition = stringValue(offer.get(ENABLED_CONDITION), "");
            boolean enabled = enabledCondition.isBlank() || Boolean.TRUE.equals(conditionValues.get(enabledCondition));
            offer.put("enabled", enabled);
            if (!enabled) {
                offer.put(DISABLED_REASON, stringValue(offer.get(DISABLED_REASON), ""));
            }
            displayOffers.add(offer);
        }
        display.put(OFFERS, displayOffers);
        return display;
    }

    private DialogueContext createShopDialogueContext(ExecutionContext context, ServerPlayer player, String title) {
        DialogueContext current = getDialogueContext(context);
        String speaker = !title.isBlank()
                ? title
                : current != null ? current.speaker() : "";
        if (current != null) {
            return new DialogueContext(
                    player,
                    current.speakerEntityId(),
                    current.targetEntityId(),
                    speaker,
                    STYLE_SHOP,
                    current.graphId(),
                    current.entryId(),
                    current.policy()
            );
        }
        return new DialogueContext(
                player,
                null,
                context.getEntity(),
                speaker,
                STYLE_SHOP,
                context.getGraphId(),
                "root"
        );
    }

    private ServerPlayer resolvePlayer(ExecutionContext context) {
        Entity target = getInput(context, PLAYER, Entity.class);
        if (target instanceof ServerPlayer player) {
            return player;
        }
        Object dialogueContext = context.getTempData(DialogueContext.TEMP_KEY);
        if (dialogueContext instanceof DialogueContext contextValue && contextValue.player() != null) {
            return contextValue.player();
        }
        Object triggerEntity = context.getEventData(StandardPorts.TRIGGER_ENTITY.getId());
        if (triggerEntity instanceof ServerPlayer player) {
            return player;
        }
        Object eventEntity = context.getEventData(StandardPorts.ENTITY.getId());
        if (eventEntity instanceof ServerPlayer player) {
            return player;
        }
        Entity owner = context.getEntity();
        return owner instanceof ServerPlayer player ? player : null;
    }

    private DialogueContext getDialogueContext(ExecutionContext context) {
        Object value = context.getTempData(DialogueContext.TEMP_KEY);
        return value instanceof DialogueContext dialogueContext ? dialogueContext : null;
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> raw) {
        if (raw == null || raw.isEmpty()) {
            return new LinkedHashMap<>(DEFAULT_SHOP_DATA);
        }
        Map<String, Object> result = normalizePlainMap(raw);
        result.put(OFFERS, normalizeOffers(result.get(OFFERS)));
        return result;
    }

    private static List<Object> normalizeOffers(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        int index = 1;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> offer = normalizePlainMap(map);
            offer.putIfAbsent("id", "trade_" + index);
            offer.putIfAbsent("title", "");
            offer.putIfAbsent("costs", List.of());
            offer.putIfAbsent("rewards", List.of());
            offer.put(MAX_USES, Math.max(0, intValue(offer.get(MAX_USES), 0)));
            offer.put(USES, Math.max(0, intValue(offer.get(USES), 0)));
            offer.put(CONSUME_SELLER_ITEMS, boolValue(offer.get(CONSUME_SELLER_ITEMS), false));
            offer.put(SELLER_RECEIVES_PAYMENT, boolValue(offer.get(SELLER_RECEIVES_PAYMENT), false));
            offer.put(VISIBLE_CONDITION, stringValue(offer.get(VISIBLE_CONDITION), ""));
            offer.put(ENABLED_CONDITION, stringValue(offer.get(ENABLED_CONDITION), ""));
            offer.put(DISABLED_REASON, stringValue(offer.get(DISABLED_REASON), ""));
            result.add(offer);
            index++;
        }
        return result;
    }

    private static int resolveConditionInputCount(NodeData instanceData) {
        if (instanceData == null || instanceData.inputs == null) {
            return 0;
        }
        return resolveConditionInputCount(instanceData.inputs.get(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id()));
    }

    private static int resolveConditionInputCount(Object countObj) {
        int count = 0;
        if (countObj instanceof Number number) {
            count = number.intValue();
        } else if (countObj instanceof String string) {
            try {
                count = Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(0, Math.min(count, MAX_CONDITION_INPUTS));
    }

    private static Map<String, Object> normalizePlainMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
            }
        }
        return result;
    }

    private static List<Object> normalizeList(List<?> raw) {
        List<Object> result = new ArrayList<>();
        for (Object value : raw) {
            if (value != null) {
                result.add(normalizeValue(value));
            }
        }
        return result;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return normalizePlainMap(map);
        }
        if (value instanceof List<?> list) {
            return normalizeList(list);
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return String.valueOf(value);
    }

    private static String stringValue(Object value, String fallback) {
        if (value instanceof String string) {
            return string;
        }
        return fallback;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static boolean boolValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            if ("true".equalsIgnoreCase(string) || "1".equals(string)) {
                return true;
            }
            if ("false".equalsIgnoreCase(string) || "0".equals(string)) {
                return false;
            }
        }
        return fallback;
    }

}
