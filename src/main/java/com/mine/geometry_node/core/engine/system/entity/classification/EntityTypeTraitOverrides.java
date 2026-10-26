package com.mine.geometry_node.core.engine.system.entity.classification;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.LinkedHashMap;
import java.util.Map;

/** Sparse, explicit instance overrides. Missing entries continue to follow datapack defaults. */
public record EntityTypeTraitOverrides(Map<String, Boolean> values) {
    public static final EntityTypeTraitOverrides EMPTY = new EntityTypeTraitOverrides(Map.of());
    public static final MapCodec<EntityTypeTraitOverrides> CODEC = Codec.unboundedMap(Codec.STRING, Codec.BOOL)
            .comapFlatMap(values -> {
                try { return DataResult.success(new EntityTypeTraitOverrides(values)); }
                catch (IllegalArgumentException invalid) { return DataResult.error(invalid::getMessage); }
            }, EntityTypeTraitOverrides::values).fieldOf("values");
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityTypeTraitOverrides> STREAM_CODEC = StreamCodec.of(
            (buffer, overrides) -> {
                buffer.writeVarInt(overrides.values.size());
                overrides.values.forEach((id, enabled) -> { buffer.writeUtf(id, 128); buffer.writeBoolean(enabled); });
            }, buffer -> {
                int count = buffer.readVarInt();
                if (count < 0 || count > EntityTypeTraits.IDS.size()) throw new IllegalArgumentException("Invalid entity type tag count");
                Map<String, Boolean> values = new LinkedHashMap<>();
                for (int i = 0; i < count; i++) {
                    String id = buffer.readUtf(128);
                    if (values.put(id, buffer.readBoolean()) != null) throw new IllegalArgumentException("Duplicate entity type tag");
                }
                return new EntityTypeTraitOverrides(values);
            });
    public EntityTypeTraitOverrides { values = EntityTypeTraits.validate(values); }
}
