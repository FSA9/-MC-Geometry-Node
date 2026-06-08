package com.mine.geometry_node.core.engine.dialogue.context;

import com.mine.geometry_node.core.engine.dialogue.session.DialogueSessionPolicy;
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
    private final UUID speakerEntityId;
    @Nullable
    private final UUID targetEntityId;
    private final String speaker;
    private final String styleId;
    private final String graphId;
    private final String entryId;
    private final DialogueSessionPolicy policy;

    public DialogueContext(@Nullable ServerPlayer player, @Nullable String speaker, String styleId) {
        this(player, (UUID) null, null, speaker, styleId, "", "", DialogueSessionPolicy.DEFAULT);
    }

    public DialogueContext(@Nullable ServerPlayer player,
                           @Nullable Entity speakerEntity,
                           @Nullable Entity targetEntity,
                           @Nullable String speaker,
                           String styleId,
                           @Nullable String graphId,
                           @Nullable String entryId) {
        this(player, speakerEntity, targetEntity, speaker, styleId, graphId, entryId, DialogueSessionPolicy.DEFAULT);
    }

    public DialogueContext(@Nullable ServerPlayer player,
                           @Nullable Entity speakerEntity,
                           @Nullable Entity targetEntity,
                           @Nullable String speaker,
                           String styleId,
                           @Nullable String graphId,
                           @Nullable String entryId,
                           DialogueSessionPolicy policy) {
        this(player,
                speakerEntity == null ? null : speakerEntity.getUUID(),
                targetEntity == null ? null : targetEntity.getUUID(),
                speaker,
                styleId,
                graphId,
                entryId,
                policy);
    }

    public DialogueContext(@Nullable ServerPlayer player,
                           @Nullable UUID speakerEntityId,
                           @Nullable UUID targetEntityId,
                           @Nullable String speaker,
                           String styleId,
                           @Nullable String graphId,
                           @Nullable String entryId,
                           DialogueSessionPolicy policy) {
        this.player = player;
        this.speakerEntityId = speakerEntityId;
        this.targetEntityId = targetEntityId;
        this.speaker = speaker == null ? "" : speaker;
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
    public UUID speakerEntityId() {
        return speakerEntityId;
    }

    @Nullable
    public UUID targetEntityId() {
        return targetEntityId;
    }

    public String speaker() {
        return speaker;
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
    public Entity resolveSpeakerEntity(@Nullable ServerLevel level) {
        return resolveEntity(level, speakerEntityId);
    }

    @Nullable
    public Entity resolveTargetEntity(@Nullable ServerLevel level) {
        return resolveEntity(level, targetEntityId);
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
