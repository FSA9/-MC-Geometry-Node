package com.mine.geometry_node.core.engine.system.entity.appearance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.*;

/** Versioned visual intent only. Contains neither an entity save nor a captured template. */
public record EntityAppearanceSpec(int version, String targetTypeId, Map<String, String> parameters) {
    public static final int CURRENT_VERSION = 1;
    public static final int MAX_PARAMETERS = 16;
    public static final int MAX_KEY_LENGTH = 64;
    public static final int MAX_VALUE_LENGTH = 256;
    public static final EntityAppearanceSpec NONE = new EntityAppearanceSpec(CURRENT_VERSION, "", Map.of());

    private record Fields(int version, String target, Map<String, String> parameters) {}
    public static final MapCodec<EntityAppearanceSpec> CODEC = RecordCodecBuilder.<Fields>mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("Version").forGetter(Fields::version),
            Codec.string(0, EntityAppearanceLimits.MAX_TYPE_LENGTH).fieldOf("Target").forGetter(Fields::target),
            Codec.unboundedMap(Codec.string(1, MAX_KEY_LENGTH), Codec.string(0, MAX_VALUE_LENGTH))
                    .fieldOf("Parameters").forGetter(Fields::parameters)
    ).apply(instance, Fields::new)).flatXmap(fields -> {
        try { return DataResult.success(new EntityAppearanceSpec(fields.version, fields.target, fields.parameters)); }
        catch (IllegalArgumentException invalid) { return DataResult.error(invalid::getMessage); }
    }, value -> DataResult.success(new Fields(value.version, value.targetTypeId, value.parameters)));

    public static final StreamCodec<RegistryFriendlyByteBuf, EntityAppearanceSpec> STREAM_CODEC = StreamCodec.of(
            (buffer, spec) -> {
                buffer.writeVarInt(spec.version); buffer.writeUtf(spec.targetTypeId, EntityAppearanceLimits.MAX_TYPE_LENGTH);
                buffer.writeVarInt(spec.parameters.size());
                spec.parameters.forEach((key, value) -> { buffer.writeUtf(key, MAX_KEY_LENGTH); buffer.writeUtf(value, MAX_VALUE_LENGTH); });
            }, buffer -> {
                int version = buffer.readVarInt(); String target = buffer.readUtf(EntityAppearanceLimits.MAX_TYPE_LENGTH);
                int count = buffer.readVarInt();
                if (count < 0 || count > MAX_PARAMETERS) throw new IllegalArgumentException("Invalid appearance parameter count");
                Map<String, String> parameters = new LinkedHashMap<>();
                for (int i = 0; i < count; i++) {
                    String key = buffer.readUtf(MAX_KEY_LENGTH), value = buffer.readUtf(MAX_VALUE_LENGTH);
                    if (parameters.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Duplicate appearance parameter");
                }
                return new EntityAppearanceSpec(version, target, parameters);
            });

    public EntityAppearanceSpec {
        Objects.requireNonNull(targetTypeId); Objects.requireNonNull(parameters);
        if (version != CURRENT_VERSION) throw new IllegalArgumentException("Unsupported appearance spec version");
        if (targetTypeId.length() > EntityAppearanceLimits.MAX_TYPE_LENGTH
                || !targetTypeId.isEmpty() && (!targetTypeId.contains(":") || Identifier.tryParse(targetTypeId) == null))
            throw new IllegalArgumentException("Invalid appearance target registry ID");
        if (Set.of("minecraft:player", "minecraft:interaction", "minecraft:mannequin").contains(targetTypeId))
            throw new IllegalArgumentException("Unsupported appearance target");
        if (parameters.size() > MAX_PARAMETERS || targetTypeId.isEmpty() && !parameters.isEmpty())
            throw new IllegalArgumentException("Invalid appearance parameters");
        TreeMap<String, String> copy = new TreeMap<>();
        parameters.forEach((key, value) -> {
            if (key == null || !key.matches("[a-z][a-z0-9_.:-]{0,63}") || value == null || value.length() > MAX_VALUE_LENGTH
                    || value.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Invalid appearance parameter");
            copy.put(key, value);
        });
        parameters = Collections.unmodifiableMap(copy);
    }
    public static EntityAppearanceSpec of(String targetTypeId) { return of(targetTypeId, Map.of()); }
    public static EntityAppearanceSpec of(String targetTypeId, Map<String, String> parameters) {
        return new EntityAppearanceSpec(CURRENT_VERSION, targetTypeId, parameters);
    }
    public boolean isNone() { return targetTypeId.isEmpty(); }
    /** Upper bound used by the nonauthoritative registration snapshot budget. */
    public int estimatedBytes() {
        return 64 + 3 * targetTypeId.length() + parameters.entrySet().stream()
                .mapToInt(entry -> 32 + 3 * (entry.getKey().length() + entry.getValue().length())).sum();
    }
}
