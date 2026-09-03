package com.mine.geometry_node.core.node.nodes.dialogue;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.system.dialogue.ShopTradeUseStore;
import com.mine.geometry_node.core.engine.system.dialogue.DialogueContext;
import com.mine.geometry_node.core.engine.system.dialogue.DialogueStyleRegistry;
import com.mine.geometry_node.core.engine.system.dialogue.model.DialogueChoicePayload;
import com.mine.geometry_node.core.engine.system.dialogue.model.DialoguePagePayload;
import com.mine.geometry_node.core.engine.system.dialogue.model.DialogueText;
import com.mine.geometry_node.core.engine.system.dialogue.DialogueWaitRequest;
import com.mine.geometry_node.core.engine.system.dialogue.DialogueRuntime;
import com.mine.geometry_node.core.engine.system.dialogue.model.shop.ShopPagePayload;
import com.mine.geometry_node.core.engine.system.dialogue.model.shop.ShopPagePayloadFactory;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenShop extends BaseNode {
    public static final String TYPE_ID = "open_shop";
    public static final String BUYER = StandardPorts.BUYER.getId();
    public static final String PLAYER = StandardPorts.PLAYER.getId();
    public static final String TITLE = StandardPorts.TITLE.getId();
    public static final String SHOP_ID = StandardPorts.SHOP_ID.getId();
    public static final String SHOP_DATA = StandardPorts.SHOP_DATA.getId();
    public static final String TEMP_SHOP_DATA = "open_shop_data";

    private static final String ACTION_OPEN_SHOP_EDITOR = "open_shop_editor";
    public static final String ACTION_PREVIEW = "preview_shop";
    private static final Map<String, Object> DEFAULT_SHOP_DATA = Map.of("offers", List.of());
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
                .addPassthroughInput(StandardPorts.BUYER.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.TITLE.toInput(""), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.SHOP_ID.toInput(""), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.SHOP_DATA.toInput(DEFAULT_SHOP_DATA).hiddenPin(), UIHint.BUTTON, null, Map.of(
                                PortMetaKeys.BUTTON_LABEL, "geometry_node.button.edit_shop",
                                PortMetaKeys.BUTTON_ACTION, ACTION_OPEN_SHOP_EDITOR,
                                PortMetaKeys.BUTTON_COLOR, 0xFF3D6EA8,
                                PortMetaKeys.BUTTON_TEXT_COLOR, 0xFFFFFFFF
                        ))
                .addRow(new PortRow(
                        null,
                        null,
                        UIHint.BUTTON,
                        null,
                        Map.of(
                                PortMetaKeys.BUTTON_LABEL, "geometry_node.button.nativepreview",
                                PortMetaKeys.BUTTON_ACTION, ACTION_PREVIEW,
                                PortMetaKeys.BUTTON_COLOR, 0xFF3D6EA8,
                                PortMetaKeys.BUTTON_TEXT_COLOR, 0xFFFFFFFF
                        )
                ));

        for (int i = 1; i <= conditionInputCount; i++) {
            builder.addPassthroughInput(StandardPorts.BOOL.toInputWithIndex(i, false), UIHint.CHECKBOX, null, Map.of(PortMetaKeys.IS_DYNAMIC, true, PortMetaKeys.DYNAMIC_INDEX, i));
        }

        return builder.build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ServerPlayer player = resolvePlayer(context);
        Map<String, Object> shopData = ShopPagePayloadFactory.normalize(getInputDict(context, SHOP_DATA));
        String title = getInput(context, TITLE, String.class);
        String safeTitle = title == null ? "" : title;
        String configuredShopId = getInput(context, SHOP_ID, String.class);
        String fallbackShopId = ShopTradeUseStore.defaultShopId(context.getCurrentNodeStableId(), context.getCurrentNodeId());
        String shopId = configuredShopId == null || configuredShopId.isBlank()
                ? fallbackShopId
                : configuredShopId.trim();
        ShopTradeUseStore.attachShopId(shopData, shopId);

        Map<String, Object> state = new HashMap<>();
        if (player != null) {
            state.put("buyer", player);
        }
        state.put("title", safeTitle);
        state.put("shop_data", shopData);
        context.setTempData(TEMP_SHOP_DATA, state);

        if (player == null) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        DialogueContext dialogueContext = createShopDialogueContext(context, player);
        Map<String, Boolean> conditionValues = evaluateConditionInputs(context);
        ShopPagePayload shop = ShopPagePayloadFactory.create(
                shopData,
                conditionValues,
                shopId,
                safeTitle,
                offerId -> ShopTradeUseStore.getUses(
                        context.getLevel(),
                        context.getEntity(),
                        context.getGraphId(),
                        shopId,
                        offerId)
        );

        DialoguePagePayload page = DialoguePagePayload.shop(
                "shop:" + context.getCurrentNodeId(),
                shop,
                List.of(new DialogueChoicePayload(
                        StandardPorts.FLOW_OUT.getId(),
                        DialogueText.EMPTY,
                        new DialogueChoicePayload.ResumePort(StandardPorts.FLOW_OUT.getId()),
                        true,
                        DialogueText.EMPTY
                ))
        );
        return ExecutionResult.externalWait(DialogueRuntime.ID,
                new DialogueWaitRequest(dialogueContext, page));
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

    private DialogueContext createShopDialogueContext(ExecutionContext context, ServerPlayer player) {
        DialogueContext current = getDialogueContext(context);
        if (current != null) {
            return new DialogueContext(
                    player,
                    current.dialogueEntityId(),
                    DialogueStyleRegistry.SHOP,
                    current.policy()
            );
        }
        Entity owner = context.getEntity();
        Entity dialogueEntity = owner instanceof ServerPlayer ? null : owner;
        return new DialogueContext(
                player,
                dialogueEntity,
                DialogueStyleRegistry.SHOP
        );
    }

    private ServerPlayer resolvePlayer(ExecutionContext context) {
        Entity target = getInput(context, BUYER, Entity.class);
        if (target == null) {
            target = getInput(context, PLAYER, Entity.class);
        }
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

}
