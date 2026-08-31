package com.mine.geometry_node.core.network.packet.s2c;

import com.mine.geometry_node.core.engine.graph.expression.ExpressionBinding;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PacketSpawnDynamicVisual(
        String effectType,
        int color,
        int durationTicks,
        Map<String, ExpressionData> expressions,
        CompoundTag extraData
) implements CustomPacketPayload {
    private static final int MAX_EXPRESSIONS = 32;
    private static final int MAX_BINDINGS = 128;
    private static final int MAX_KEY_LENGTH = 64;
    private static final int MAX_FORMULA_LENGTH = 4096;

    public static final Type<PacketSpawnDynamicVisual> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("geometry_node", "spawn_dynamic_visual")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSpawnDynamicVisual> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketSpawnDynamicVisual::new
    );

    public PacketSpawnDynamicVisual(RegistryFriendlyByteBuf buf) {
        this(
                buf.readUtf(),
                buf.readInt(),
                buf.readInt(),
                readExpressions(buf),
                (CompoundTag) buf.readNbt()
        );
    }

    public PacketSpawnDynamicVisual {
        expressions = expressions == null ? Map.of() : Map.copyOf(expressions);
        if (expressions.size() > MAX_EXPRESSIONS) {
            throw new IllegalArgumentException("visual expressions exceed limit " + MAX_EXPRESSIONS);
        }
        for (ExpressionData expression : expressions.values()) {
            if (expression.bindings().size() > MAX_BINDINGS) {
                throw new IllegalArgumentException("expression bindings exceed limit " + MAX_BINDINGS);
            }
        }
        extraData = extraData == null ? new CompoundTag() : extraData;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(this.effectType);
        buf.writeInt(this.color);
        buf.writeInt(this.durationTicks);
        writeExpressions(buf, this.expressions);
        buf.writeNbt(this.extraData);
    }

    private static Map<String, ExpressionData> readExpressions(RegistryFriendlyByteBuf buf) {
        int size = readSize(buf, MAX_EXPRESSIONS, "visual expressions");
        Map<String, ExpressionData> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(MAX_KEY_LENGTH);
            String formula = buf.readUtf(MAX_FORMULA_LENGTH);
            int componentCount = readSize(buf, 3, "expression components");
            List<String> components = new ArrayList<>(componentCount);
            for (int component = 0; component < componentCount; component++) {
                components.add(buf.readUtf(MAX_FORMULA_LENGTH));
            }
            int bindingCount = readSize(buf, MAX_BINDINGS, "expression bindings");
            Map<String, ExpressionBinding> bindings = new LinkedHashMap<>();
            for (int binding = 0; binding < bindingCount; binding++) {
                bindings.put(buf.readUtf(MAX_KEY_LENGTH), readBinding(buf));
            }
            map.put(key, new ExpressionData(formula, components, bindings));
        }
        return Map.copyOf(map);
    }

    private static void writeExpressions(RegistryFriendlyByteBuf buf, Map<String, ExpressionData> expressions) {
        buf.writeVarInt(expressions.size());
        for (Map.Entry<String, ExpressionData> entry : expressions.entrySet()) {
            ExpressionData expression = entry.getValue();
            buf.writeUtf(entry.getKey(), MAX_KEY_LENGTH);
            buf.writeUtf(expression.formula(), MAX_FORMULA_LENGTH);
            buf.writeVarInt(expression.components().size());
            for (String component : expression.components()) {
                buf.writeUtf(component, MAX_FORMULA_LENGTH);
            }
            buf.writeVarInt(expression.bindings().size());
            for (Map.Entry<String, ExpressionBinding> binding : expression.bindings().entrySet()) {
                buf.writeUtf(binding.getKey(), MAX_KEY_LENGTH);
                writeBinding(buf, binding.getValue());
            }
        }
    }

    private static ExpressionBinding readBinding(RegistryFriendlyByteBuf buf) {
        return switch (buf.readByte()) {
            case 0 -> new ExpressionBinding.Constant(buf.readDouble());
            case 1 -> {
                String dimensionId = buf.readUtf(128);
                java.util.UUID entityUuid = buf.readUUID();
                int runtimeId = buf.readVarInt();
                ExpressionBinding.Property property = ExpressionBinding.Property.fromId(buf.readUtf(32));
                if (property == null) throw new IllegalArgumentException("Unknown entity expression property");
                yield new ExpressionBinding.EntityProperty(
                        dimensionId, entityUuid, runtimeId, property, buf.readDouble());
            }
            default -> throw new IllegalArgumentException("Unknown expression binding type");
        };
    }

    private static void writeBinding(RegistryFriendlyByteBuf buf, ExpressionBinding binding) {
        switch (binding) {
            case ExpressionBinding.Constant constant -> {
                buf.writeByte(0);
                buf.writeDouble(constant.fallbackValue());
            }
            case ExpressionBinding.EntityProperty entity -> {
                buf.writeByte(1);
                buf.writeUtf(entity.dimensionId(), 128);
                buf.writeUUID(entity.entityUuid());
                buf.writeVarInt(entity.runtimeEntityId());
                buf.writeUtf(entity.property().id(), 32);
                buf.writeDouble(entity.fallbackValue());
            }
        }
    }

    private static int readSize(RegistryFriendlyByteBuf buf, int maximum, String label) {
        int size = buf.readVarInt();
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException(label + " exceeds limit " + maximum);
        }
        return size;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
