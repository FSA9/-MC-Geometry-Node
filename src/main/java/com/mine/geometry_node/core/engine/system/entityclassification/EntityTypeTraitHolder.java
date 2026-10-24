package com.mine.geometry_node.core.engine.system.entityclassification;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Entity-local view of a native holder: registry identity and data maps remain delegated. */
public final class EntityTypeTraitHolder implements Holder<EntityType<?>> {
    private final Holder<EntityType<?>> nativeType;
    private final Supplier<Map<String, Boolean>> overrides;

    public EntityTypeTraitHolder(Holder<EntityType<?>> nativeType, Supplier<Map<String, Boolean>> overrides) {
        this.nativeType = nativeType; this.overrides = overrides;
    }
    @Override public boolean is(TagKey<EntityType<?>> tag) {
        Boolean override = overrides.get().get(tag.location().toString());
        return tag.isFor(net.minecraft.core.registries.Registries.ENTITY_TYPE) && override != null ? override : nativeType.is(tag);
    }
    @Override public Stream<TagKey<EntityType<?>>> tags() {
        Map<String, Boolean> values = overrides.get();
        return Stream.concat(nativeType.tags().filter(tag -> !values.containsKey(tag.location().toString())),
                values.entrySet().stream().filter(Map.Entry::getValue)
                        .map(entry -> TagKey.<EntityType<?>>create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                                Identifier.parse(entry.getKey()))));
    }
    @Override public EntityType<?> value() { return nativeType.value(); }
    @Override public boolean isBound() { return nativeType.isBound(); }
    @Override public boolean areComponentsBound() { return nativeType.areComponentsBound(); }
    @Override public boolean is(Identifier id) { return nativeType.is(id); }
    @Override public boolean is(ResourceKey<EntityType<?>> key) { return nativeType.is(key); }
    @Override public boolean is(Predicate<ResourceKey<EntityType<?>>> predicate) { return nativeType.is(predicate); }
    @Override public boolean is(Holder<EntityType<?>> holder) { return nativeType.is(holder.getDelegate()); }
    @Override public DataComponentMap components() { return nativeType.components(); }
    @Override public Either<ResourceKey<EntityType<?>>, EntityType<?>> unwrap() { return nativeType.unwrap(); }
    @Override public Optional<ResourceKey<EntityType<?>>> unwrapKey() { return nativeType.unwrapKey(); }
    @Override public Kind kind() { return nativeType.kind(); }
    @Override public boolean canSerializeIn(HolderOwner<EntityType<?>> owner) { return nativeType.canSerializeIn(owner); }
    @Override public Holder<EntityType<?>> getDelegate() { return nativeType.getDelegate(); }
    @Override public HolderLookup.RegistryLookup<EntityType<?>> unwrapLookup() { return nativeType.unwrapLookup(); }
    @Override public <D> D getData(DataMapType<EntityType<?>, D> type) { return nativeType.getData(type); }
    @Override public ResourceKey<EntityType<?>> getKey() { return nativeType.getKey(); }
    @Override public int hashCode() { return nativeType.hashCode(); }
    @Override public boolean equals(Object other) {
        return other == this || nativeType.equals(other instanceof EntityTypeTraitHolder holder ? holder.nativeType : other);
    }
}
