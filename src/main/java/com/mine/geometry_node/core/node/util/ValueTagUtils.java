package com.mine.geometry_node.core.node.util;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.value.DynamicData;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class ValueTagUtils {
    private ValueTagUtils() {
    }

    public static Object unwrap(Object value) {
        return value instanceof DynamicData dynamicData ? dynamicData.value() : value;
    }

    public static boolean hasTag(Object value, String rawTag, ExecutionContext context) {
        Identifier tagId = parseIdentifier(rawTag);
        if (tagId == null) {
            return false;
        }

        for (TagSubject<?> subject : subjects(value, context)) {
            if (subject.is(tagId)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> tags(Object value, ExecutionContext context) {
        Set<String> tags = new TreeSet<>();
        for (TagSubject<?> subject : subjects(value, context)) {
            subject.tags().forEach(tags::add);
        }
        return new ArrayList<>(tags);
    }

    public static Set<String> kindKeys(Object value, ExecutionContext context) {
        value = unwrap(value);
        Set<String> keys = new LinkedHashSet<>();
        for (TagSubject<?> subject : subjects(value, context)) {
            keys.add(subject.registryKind());
        }
        if (!keys.isEmpty()) {
            return keys;
        }

        if (value == null) {
            keys.add("null");
        } else if (value instanceof Number) {
            keys.add("number");
        } else if (value instanceof Boolean) {
            keys.add("boolean");
        } else if (value instanceof String) {
            keys.add("string");
        } else if (value instanceof List<?>) {
            keys.add("list");
        } else if (value instanceof java.util.Map<?, ?>) {
            keys.add("dict");
        } else {
            keys.add(value.getClass().getName());
        }
        return keys;
    }

    public static Set<String> registryIdentities(Object value, ExecutionContext context) {
        Set<String> identities = new LinkedHashSet<>();
        for (TagSubject<?> subject : subjects(value, context)) {
            identities.add(subject.identity());
        }
        return identities;
    }

    public static boolean tagStringsEqual(String a, String b) {
        Identifier left = parseIdentifier(a);
        Identifier right = parseIdentifier(b);
        return left != null && left.equals(right);
    }

    private static List<TagSubject<?>> subjects(Object value, ExecutionContext context) {
        value = unwrap(value);
        if (value == null) {
            return List.of();
        }

        List<TagSubject<?>> subjects = new ArrayList<>();
        if (value instanceof ItemStack stack) {
            addItemStack(subjects, stack);
        } else if (value instanceof Item item) {
            addItem(subjects, item);
        } else if (value instanceof BlockState state) {
            addBlock(subjects, state.getBlock());
        } else if (value instanceof Block block) {
            addBlock(subjects, block);
        } else if (value instanceof Entity entity) {
            addEntityType(subjects, entity.getType());
        } else if (value instanceof EntityType<?> entityType) {
            addEntityType(subjects, entityType);
        } else if (value instanceof DamageSource source) {
            addDamageType(subjects, source.typeHolder());
        } else if (value instanceof String rawId) {
            addByRegistryId(subjects, rawId, context);
        }
        return subjects;
    }

    private static void addByRegistryId(List<TagSubject<?>> subjects, String rawId, ExecutionContext context) {
        Identifier id = parseIdentifier(rawId);
        if (id == null) {
            return;
        }

        BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> addItem(subjects, item));
        BuiltInRegistries.BLOCK.getOptional(id).ifPresent(block -> addBlock(subjects, block));
        BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(entityType -> addEntityType(subjects, entityType));

        if (context != null && context.getLevel() != null) {
            context.getLevel().registryAccess()
                    .lookup(Registries.DAMAGE_TYPE)
                    .flatMap(registry -> registry.get(ResourceKey.create(Registries.DAMAGE_TYPE, id)))
                    .ifPresent(holder -> addDamageType(subjects, holder));
        }
    }

    private static void addItemStack(List<TagSubject<?>> subjects, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        addItemHolder(subjects, stack.typeHolder());
    }

    private static void addItem(List<TagSubject<?>> subjects, Item item) {
        if (item == null) {
            return;
        }
        addItemHolder(subjects, BuiltInRegistries.ITEM.wrapAsHolder(item));
    }

    private static void addItemHolder(List<TagSubject<?>> subjects, Holder<Item> holder) {
        holder.unwrapKey().ifPresent(key -> subjects.add(new TagSubject<>(
                Registries.ITEM,
                holder,
                key.identifier().toString()
        )));
    }

    private static void addBlock(List<TagSubject<?>> subjects, Block block) {
        if (block == null) {
            return;
        }
        Holder<Block> holder = BuiltInRegistries.BLOCK.wrapAsHolder(block);
        holder.unwrapKey().ifPresent(key -> subjects.add(new TagSubject<>(
                Registries.BLOCK,
                holder,
                key.identifier().toString()
        )));
    }

    private static void addEntityType(List<TagSubject<?>> subjects, EntityType<?> entityType) {
        if (entityType == null) {
            return;
        }
        Holder<EntityType<?>> holder = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entityType);
        holder.unwrapKey().ifPresent(key -> subjects.add(new TagSubject<>(
                Registries.ENTITY_TYPE,
                holder,
                key.identifier().toString()
        )));
    }

    private static void addDamageType(List<TagSubject<?>> subjects, Holder<DamageType> holder) {
        if (holder == null) {
            return;
        }
        holder.unwrapKey().ifPresent(key -> subjects.add(new TagSubject<>(
                Registries.DAMAGE_TYPE,
                holder,
                key.identifier().toString()
        )));
    }

    private static Identifier parseIdentifier(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("#")) {
            value = value.substring(1).trim();
        }
        if (value.isEmpty()) {
            return null;
        }
        return Identifier.tryParse(value.toLowerCase(Locale.ROOT));
    }

    private record TagSubject<T>(
            ResourceKey<? extends Registry<T>> registryKey,
            Holder<T> holder,
            String registryId
    ) {
        boolean is(Identifier tagId) {
            return holder.is(TagKey.create(registryKey, tagId));
        }

        List<String> tags() {
            return holder.tags()
                    .map(tag -> "#" + tag.location())
                    .toList();
        }

        String registryKind() {
            return registryKey.identifier().toString();
        }

        String identity() {
            return registryKind() + "|" + registryId;
        }
    }
}
