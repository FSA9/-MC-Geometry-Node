package com.mine.geometry_node.core.network.packet.s2c;

import com.mine.geometry_node.core.engine.dialogue.model.DialogueText;
import com.mine.geometry_node.core.network.codec.DialogueTextStreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PacketQuestScreenSnapshot(
        List<StatusView> statuses,
        List<QuestView> quests,
        boolean openScreen,
        boolean actionSuccessful,
        String actionResult,
        String actionMessage
) implements CustomPacketPayload {
    private static final int MAX_STRING_LENGTH = 32767;
    private static final int MAX_STATUSES = 256;
    private static final int MAX_QUESTS = 4096;
    private static final int MAX_OBJECTIVES_PER_QUEST = 1024;
    private static final int MAX_REWARDS_PER_QUEST = 1024;

    public static final Type<PacketQuestScreenSnapshot> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "quest_screen_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketQuestScreenSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketQuestScreenSnapshot::new
    );

    public PacketQuestScreenSnapshot {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        quests = quests == null ? List.of() : List.copyOf(quests);
        actionResult = actionResult == null ? "" : actionResult;
        actionMessage = actionMessage == null ? "" : actionMessage;
    }

    public PacketQuestScreenSnapshot(RegistryFriendlyByteBuf buf) {
        this(readStatuses(buf), readQuests(buf), buf.readBoolean(), buf.readBoolean(),
                buf.readUtf(MAX_STRING_LENGTH), buf.readUtf(MAX_STRING_LENGTH));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        writeCount(buf, statuses.size(), MAX_STATUSES, "quest statuses");
        for (StatusView status : statuses) {
            buf.writeUtf(status.id(), MAX_STRING_LENGTH);
            buf.writeUtf(status.translationKey(), MAX_STRING_LENGTH);
            buf.writeInt(status.color());
            buf.writeBoolean(status.graphActive());
        }

        writeCount(buf, quests.size(), MAX_QUESTS, "quests");
        for (QuestView quest : quests) {
            buf.writeUtf(quest.taskKey(), MAX_STRING_LENGTH);
            buf.writeUtf(quest.instanceId(), MAX_STRING_LENGTH);
            buf.writeUtf(quest.statusId(), MAX_STRING_LENGTH);
            DialogueTextStreamCodec.STREAM_CODEC.encode(buf, quest.title());
            DialogueTextStreamCodec.STREAM_CODEC.encode(buf, quest.description());
            writeCount(buf, quest.objectives().size(), MAX_OBJECTIVES_PER_QUEST, "quest objectives");
            for (ObjectiveView objective : quest.objectives()) {
                buf.writeUtf(objective.entryId(), MAX_STRING_LENGTH);
                DialogueTextStreamCodec.STREAM_CODEC.encode(buf, objective.content());
                buf.writeBoolean(objective.quantityEnabled());
                buf.writeBoolean(objective.counterEnabled());
                buf.writeUtf(objective.counterKey(), MAX_STRING_LENGTH);
                buf.writeDouble(objective.currentValue());
                buf.writeDouble(objective.targetValue());
                buf.writeUtf(objective.hintType(), MAX_STRING_LENGTH);
                buf.writeUtf(objective.hintValue(), MAX_STRING_LENGTH);
            }
            writeCount(buf, quest.rewards().size(), MAX_REWARDS_PER_QUEST, "quest rewards");
            for (RewardView reward : quest.rewards()) {
                buf.writeUtf(reward.entryId(), MAX_STRING_LENGTH);
                DialogueTextStreamCodec.STREAM_CODEC.encode(buf, reward.content());
                buf.writeDouble(reward.amount());
                buf.writeUtf(reward.hintType(), MAX_STRING_LENGTH);
                buf.writeUtf(reward.hintValue(), MAX_STRING_LENGTH);
            }
            buf.writeBoolean(quest.acceptEnabled());
            buf.writeLong(quest.updatedAt());
        }

        buf.writeBoolean(openScreen);
        buf.writeBoolean(actionSuccessful);
        buf.writeUtf(actionResult, MAX_STRING_LENGTH);
        buf.writeUtf(actionMessage, MAX_STRING_LENGTH);
    }

    private static List<StatusView> readStatuses(RegistryFriendlyByteBuf buf) {
        int count = readCount(buf, MAX_STATUSES, "quest statuses");
        List<StatusView> statuses = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            statuses.add(new StatusView(
                    buf.readUtf(MAX_STRING_LENGTH),
                    buf.readUtf(MAX_STRING_LENGTH),
                    buf.readInt(),
                    buf.readBoolean()
            ));
        }
        return List.copyOf(statuses);
    }

    private static List<QuestView> readQuests(RegistryFriendlyByteBuf buf) {
        int count = readCount(buf, MAX_QUESTS, "quests");
        List<QuestView> quests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            quests.add(new QuestView(
                    buf.readUtf(MAX_STRING_LENGTH),
                    buf.readUtf(MAX_STRING_LENGTH),
                    buf.readUtf(MAX_STRING_LENGTH),
                    DialogueTextStreamCodec.STREAM_CODEC.decode(buf),
                    DialogueTextStreamCodec.STREAM_CODEC.decode(buf),
                    readObjectives(buf),
                    readRewards(buf),
                    buf.readBoolean(),
                    buf.readLong()
            ));
        }
        return List.copyOf(quests);
    }

    private static List<ObjectiveView> readObjectives(RegistryFriendlyByteBuf buf) {
        int count = readCount(buf, MAX_OBJECTIVES_PER_QUEST, "quest objectives");
        List<ObjectiveView> objectives = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            objectives.add(new ObjectiveView(
                    buf.readUtf(MAX_STRING_LENGTH),
                    DialogueTextStreamCodec.STREAM_CODEC.decode(buf),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readUtf(MAX_STRING_LENGTH),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readUtf(MAX_STRING_LENGTH),
                    buf.readUtf(MAX_STRING_LENGTH)
            ));
        }
        return List.copyOf(objectives);
    }

    private static List<RewardView> readRewards(RegistryFriendlyByteBuf buf) {
        int count = readCount(buf, MAX_REWARDS_PER_QUEST, "quest rewards");
        List<RewardView> rewards = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rewards.add(new RewardView(
                    buf.readUtf(MAX_STRING_LENGTH),
                    DialogueTextStreamCodec.STREAM_CODEC.decode(buf),
                    buf.readDouble(),
                    buf.readUtf(MAX_STRING_LENGTH),
                    buf.readUtf(MAX_STRING_LENGTH)
            ));
        }
        return List.copyOf(rewards);
    }

    private static int readCount(RegistryFriendlyByteBuf buf, int maximum, String label) {
        int count = buf.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid " + label + " count: " + count);
        }
        return count;
    }

    private static void writeCount(RegistryFriendlyByteBuf buf, int count, int maximum, String label) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid " + label + " count: " + count);
        }
        buf.writeVarInt(count);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record StatusView(String id, String translationKey, int color, boolean graphActive) {
        public StatusView {
            id = id == null ? "" : id;
            translationKey = translationKey == null ? "" : translationKey;
        }
    }

    public record QuestView(
            String taskKey,
            String instanceId,
            String statusId,
            DialogueText title,
            DialogueText description,
            List<ObjectiveView> objectives,
            List<RewardView> rewards,
            boolean acceptEnabled,
            long updatedAt
    ) {
        public QuestView {
            taskKey = taskKey == null ? "" : taskKey;
            instanceId = instanceId == null ? "" : instanceId;
            statusId = statusId == null ? "" : statusId;
            title = title == null ? DialogueText.EMPTY : title;
            description = description == null ? DialogueText.EMPTY : description;
            objectives = objectives == null ? List.of() : List.copyOf(objectives);
            rewards = rewards == null ? List.of() : List.copyOf(rewards);
        }
    }

    public record ObjectiveView(
            String entryId,
            DialogueText content,
            boolean quantityEnabled,
            boolean counterEnabled,
            String counterKey,
            double currentValue,
            double targetValue,
            String hintType,
            String hintValue
    ) {
        public ObjectiveView {
            entryId = entryId == null ? "" : entryId;
            content = content == null ? DialogueText.EMPTY : content;
            quantityEnabled = quantityEnabled || counterEnabled;
            counterKey = counterKey == null ? "" : counterKey;
            currentValue = Double.isFinite(currentValue) ? Math.max(0.0, currentValue) : 0.0;
            targetValue = Double.isFinite(targetValue) ? Math.max(0.0, targetValue) : 0.0;
            hintType = hintType == null ? "none" : hintType;
            hintValue = hintValue == null ? "" : hintValue;
        }
    }

    public record RewardView(
            String entryId,
            DialogueText content,
            double amount,
            String hintType,
            String hintValue
    ) {
        public RewardView {
            entryId = entryId == null ? "" : entryId;
            content = content == null ? DialogueText.EMPTY : content;
            amount = Double.isFinite(amount) ? Math.max(0.0, amount) : 0.0;
            hintType = hintType == null ? "none" : hintType;
            hintValue = hintValue == null ? "" : hintValue;
        }
    }
}
