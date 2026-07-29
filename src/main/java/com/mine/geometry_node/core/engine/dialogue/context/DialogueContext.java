package com.mine.geometry_node.core.engine.dialogue.context;

import com.mine.geometry_node.core.engine.dialogue.session.DialogueSessionPolicy;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Per-execution dialogue defaults shared by following dialogue page nodes.
 */
public final class DialogueContext {
    public static final String TEMP_KEY = "geometry_node.dialogue.context";

    @Nullable
    private final ServerPlayer player;
    @Nullable
    private final UUID dialogueEntityId;
    private final String styleId;
    private final String graphId;
    private final String entryId;
    private final DialogueSessionPolicy policy;

    public DialogueContext(@Nullable ServerPlayer player,
                           @Nullable Entity dialogueEntity,
                           String styleId,
                           @Nullable String graphId,
                           @Nullable String entryId) {
        this(player, dialogueEntity, styleId, graphId, entryId, DialogueSessionPolicy.DEFAULT);
    }

    public DialogueContext(@Nullable ServerPlayer player,
                           @Nullable Entity dialogueEntity,
                           String styleId,
                           @Nullable String graphId,
                           @Nullable String entryId,
                           DialogueSessionPolicy policy) {
        this(player,
                dialogueEntity == null ? null : dialogueEntity.getUUID(),
                styleId,
                graphId,
                entryId,
                policy);
    }

    public DialogueContext(@Nullable ServerPlayer player,
                           @Nullable UUID dialogueEntityId,
                           String styleId,
                           @Nullable String graphId,
                           @Nullable String entryId,
                           DialogueSessionPolicy policy) {
        this.player = player;
        this.dialogueEntityId = dialogueEntityId;
        this.styleId = styleId == null || styleId.isBlank() ? "default" : styleId;
        this.graphId = graphId == null ? "" : graphId;
        this.entryId = entryId == null || entryId.isBlank() ? "root" : entryId;
        this.policy = policy == null ? DialogueSessionPolicy.DEFAULT : policy;
    }

    @Nullable
    public ServerPlayer player() {
        return player;
    }

    @Nullable
    public UUID dialogueEntityId() {
        return dialogueEntityId;
    }

    public String styleId() {
        return styleId;
    }

    public String graphId() {
        return graphId;
    }

    public String entryId() {
        return entryId;
    }

    public DialogueSessionPolicy policy() {
        return policy;
    }

    @Nullable
    public Entity resolveDialogueEntity(@Nullable ServerLevel level) {
        return resolveEntity(level, dialogueEntityId);
    }

    public Component resolveDialogueEntityDisplayName() {
        if (player == null) {
            return Component.empty();
        }
        Entity dialogueEntity = resolveDialogueEntity(player.level());
        return dialogueEntity == null ? Component.empty() : dialogueEntity.getDisplayName().copy();
    }

    @Nullable
    private static Entity resolveEntity(@Nullable ServerLevel level, @Nullable UUID entityId) {
        if (level == null || entityId == null) {
            return null;
        }
        Entity entity = level.getEntity(entityId);
        return entity == null || entity.isRemoved() ? null : entity;
    }
}
