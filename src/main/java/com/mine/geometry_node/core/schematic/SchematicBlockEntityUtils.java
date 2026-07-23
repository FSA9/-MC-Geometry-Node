package com.mine.geometry_node.core.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;
import java.util.Map;

public final class SchematicBlockEntityUtils {
    private static final Map<String, String> LEGACY_IDS = Map.ofEntries(
            Map.entry("airportal", "minecraft:end_portal"),
            Map.entry("banner", "minecraft:banner"),
            Map.entry("beacon", "minecraft:beacon"),
            Map.entry("bed", "minecraft:bed"),
            Map.entry("cauldron", "minecraft:brewing_stand"),
            Map.entry("chest", "minecraft:chest"),
            Map.entry("commandblock", "minecraft:command_block"),
            Map.entry("comparator", "minecraft:comparator"),
            Map.entry("control", "minecraft:command_block"),
            Map.entry("dropper", "minecraft:dropper"),
            Map.entry("enchanttable", "minecraft:enchanting_table"),
            Map.entry("enderchest", "minecraft:ender_chest"),
            Map.entry("endgateway", "minecraft:end_gateway"),
            Map.entry("flowerpot", "minecraft:flower_pot"),
            Map.entry("furnace", "minecraft:furnace"),
            Map.entry("hopper", "minecraft:hopper"),
            Map.entry("jigsaw", "minecraft:jigsaw"),
            Map.entry("mobspawner", "minecraft:mob_spawner"),
            Map.entry("piston", "minecraft:piston"),
            Map.entry("recordplayer", "minecraft:jukebox"),
            Map.entry("sign", "minecraft:sign"),
            Map.entry("skull", "minecraft:skull"),
            Map.entry("structure", "minecraft:structure_block"),
            Map.entry("trap", "minecraft:dispenser")
    );

    private SchematicBlockEntityUtils() {
    }

    public static void normalizeId(CompoundTag tag) {
        if (tag == null) {
            return;
        }

        String rawId = tag.getStringOr("id", "").trim();
        if (rawId.isEmpty() && tag.contains("Id")) {
            rawId = tag.getStringOr("Id", "").trim();
        }
        if (rawId.isEmpty()) {
            return;
        }

        tag.putString("id", normalizeId(rawId));
    }

    public static CompoundTag absoluteTag(CompoundTag source, BlockPos worldPos) {
        CompoundTag tag = source == null ? new CompoundTag() : source.copy();
        normalizeId(tag);
        tag.putInt("x", worldPos.getX());
        tag.putInt("y", worldPos.getY());
        tag.putInt("z", worldPos.getZ());
        tag.putIntArray("Pos", new int[]{worldPos.getX(), worldPos.getY(), worldPos.getZ()});
        return tag;
    }

    public static BlockEntity loadBlockEntity(Level level, BlockPos pos, BlockState state, CompoundTag sourceTag) {
        if (level == null || pos == null || state == null || !state.hasBlockEntity()) {
            return null;
        }

        BlockEntity fallback = createDefaultBlockEntity(pos, state);
        CompoundTag tag = absoluteTag(sourceTag, pos);
        if (!hasId(tag) && fallback != null) {
            Identifier fallbackId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(fallback.getType());
            if (fallbackId != null) {
                tag.putString("id", fallbackId.toString());
            }
        }

        BlockEntity blockEntity = hasId(tag)
                ? BlockEntity.loadStatic(pos, state, tag, level.registryAccess())
                : fallback;
        if (blockEntity == null && fallback != null && (sourceTag == null || sourceTag.isEmpty())) {
            blockEntity = fallback;
        }
        if (blockEntity != null) {
            blockEntity.setLevel(level);
            blockEntity.clearRemoved();
        }
        return blockEntity;
    }

    public static boolean setBlockEntity(Level level, BlockPos pos, BlockState state, CompoundTag sourceTag) {
        if (level == null || pos == null || state == null) {
            return false;
        }
        if (!state.hasBlockEntity()) {
            level.removeBlockEntity(pos);
            return level.getBlockEntity(pos) == null;
        }

        BlockEntity blockEntity = loadBlockEntity(level, pos, state, sourceTag);
        if (blockEntity == null) {
            return false;
        }
        level.setBlockEntity(blockEntity);
        blockEntity.setChanged();
        BlockEntity current = level.getBlockEntity(pos);
        return current != null && current.getType() == blockEntity.getType();
    }

    private static BlockEntity createDefaultBlockEntity(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof EntityBlock entityBlock) {
            return entityBlock.newBlockEntity(pos, state);
        }
        return null;
    }

    private static boolean hasId(CompoundTag tag) {
        return tag != null && !tag.getStringOr("id", "").trim().isEmpty();
    }

    private static String normalizeId(String rawId) {
        String trimmed = rawId.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String legacy = LEGACY_IDS.get(lower);
        if (legacy != null) {
            return legacy;
        }
        Identifier identifier = Identifier.tryParse(trimmed);
        if (identifier != null) {
            return identifier.toString();
        }
        Identifier lowerIdentifier = Identifier.tryParse(lower);
        if (lowerIdentifier != null && trimmed.contains(":")) {
            return lowerIdentifier.toString();
        }
        if (!trimmed.contains(":")) {
            Identifier vanilla = Identifier.tryParse("minecraft:" + lower);
            if (vanilla != null) {
                return vanilla.toString();
            }
        }
        return trimmed;
    }
}
