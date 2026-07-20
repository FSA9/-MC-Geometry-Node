package com.mine.geometry_node.core.schematic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class SchematicReader {
    private static final long MAX_NBT_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_DECODED_BLOCKS = 8_000_000;

    private SchematicReader() {
    }

    public static SchematicData read(Path path, int maxBlocks) throws IOException {
        return read(path, maxBlocks, false);
    }

    public static SchematicData read(Path path, int maxBlocks, boolean includeAir) throws IOException {
        if (maxBlocks <= 0) {
            return new SchematicData(0, 0, 0, java.util.List.of(), java.util.List.of(), java.util.List.of(), true);
        }
        CompoundTag root = readRoot(path);
        if (looksLikeSponge(root)) {
            return readSponge(root, maxBlocks, includeAir);
        }
        if (looksLikeLegacy(root)) {
            return readLegacy(root, maxBlocks, includeAir);
        }
        throw new IOException("Unsupported schematic format: " + path.getFileName());
    }

    private static CompoundTag readRoot(Path path) throws IOException {
        IOException compressedError = null;
        try {
            return NbtIo.readCompressed(path, NbtAccounter.create(MAX_NBT_BYTES));
        } catch (IOException e) {
            compressedError = e;
        }

        try {
            return NbtIo.read(path);
        } catch (IOException e) {
            e.addSuppressed(compressedError);
            throw e;
        }
    }

    private static boolean looksLikeSponge(CompoundTag root) {
        if (root.contains("Palette") && root.contains("BlockData")) {
            return true;
        }
        return root.contains("Blocks")
                && root.getCompoundOrEmpty("Blocks").contains("Palette");
    }

    private static boolean looksLikeLegacy(CompoundTag root) {
        return root.contains("Blocks")
                && root.contains("Data")
                && root.contains("Width")
                && root.contains("Height")
                && root.contains("Length");
    }

    private static SchematicData readSponge(CompoundTag root, int maxBlocks, boolean includeAir) throws IOException {
        CompoundTag nestedBlocks = root.getCompoundOrEmpty("Blocks");
        CompoundTag blocksRoot = nestedBlocks.contains("Palette") || nestedBlocks.contains("BlockData") || nestedBlocks.contains("Data")
                ? nestedBlocks
                : root;
        int width = positive(firstInt(root, blocksRoot, "Width"));
        int height = positive(firstInt(root, blocksRoot, "Height"));
        int length = positive(firstInt(root, blocksRoot, "Length"));
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException("Invalid schematic size");
        }

        CompoundTag palette = blocksRoot.contains("Palette")
                ? blocksRoot.getCompoundOrEmpty("Palette")
                : root.getCompoundOrEmpty("Palette");
        byte[] blockData = blocksRoot.contains("Data")
                ? byteArrayOrEmpty(blocksRoot, "Data")
                : byteArrayOrEmpty(blocksRoot, "BlockData");
        if (blockData.length == 0 && root.contains("BlockData")) {
            blockData = byteArrayOrEmpty(root, "BlockData");
        }
        if (palette.isEmpty() || blockData.length == 0) {
            throw new IOException("Empty schematic palette");
        }

        Map<Integer, String> paletteById = new HashMap<>();
        for (String key : palette.keySet()) {
            paletteById.put(intOrZero(palette, key), key);
        }

        int volume = safeVolume(width, height, length);
        int decodeLimit = Math.min(volume, MAX_DECODED_BLOCKS);
        ArrayList<SchematicData.Block> blocks = new ArrayList<>(Math.min(1024, maxBlocks));
        boolean truncated = volume > decodeLimit;
        int index = 0;
        int offset = 0;
        int layer = width * length;
        while (offset < blockData.length && index < decodeLimit) {
            VarIntResult result = readVarInt(blockData, offset);
            if (!result.valid()) {
                truncated = true;
                break;
            }
            offset = result.nextOffset();
            String state = paletteById.get(result.value());
            int color = SchematicBlockColor.forBlockState(state);
            if (includeAir || color != 0) {
                int y = index / layer;
                int rem = index - y * layer;
                int z = rem / width;
                int x = rem - z * width;
                blocks.add(new SchematicData.Block(x, y, z, state, color));
                if (blocks.size() >= maxBlocks) {
                    truncated = index + 1 < volume;
                    break;
                }
            }
            index++;
        }
        if (index < volume && blocks.size() < maxBlocks) {
            truncated = true;
        }
        return new SchematicData(
                width,
                height,
                length,
                blocks,
                readBlockEntities(root, blocksRoot, false),
                readEntities(root),
                truncated
        );
    }

    private static SchematicData readLegacy(CompoundTag root, int maxBlocks, boolean includeAir) throws IOException {
        int width = positive(intOrZero(root, "Width"));
        int height = positive(intOrZero(root, "Height"));
        int length = positive(intOrZero(root, "Length"));
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException("Invalid schematic size");
        }

        byte[] rawBlocks = byteArrayOrEmpty(root, "Blocks");
        byte[] data = byteArrayOrEmpty(root, "Data");
        byte[] addBlocks = root.contains("AddBlocks") ? byteArrayOrEmpty(root, "AddBlocks") : new byte[0];
        if (rawBlocks.length == 0) {
            throw new IOException("Empty schematic blocks");
        }

        int expectedVolume = safeVolume(width, height, length);
        int volume = Math.min(expectedVolume, Math.min(rawBlocks.length, MAX_DECODED_BLOCKS));
        boolean truncated = expectedVolume > volume;
        ArrayList<SchematicData.Block> blocks = new ArrayList<>(Math.min(1024, maxBlocks));
        int layer = width * length;
        for (int index = 0; index < volume; index++) {
            int id = rawBlocks[index] & 0xFF;
            if (addBlocks.length > (index >> 1)) {
                int packed = addBlocks[index >> 1] & 0xFF;
                int high = (index & 1) == 0 ? (packed & 0x0F) : ((packed >> 4) & 0x0F);
                id |= high << 8;
            }
            int meta = data.length > index ? data[index] & 0x0F : 0;
            int color = SchematicBlockColor.forLegacyBlock(id, meta);
            if (!includeAir && color == 0) continue;
            int y = index / layer;
            int rem = index - y * layer;
            int z = rem / width;
            int x = rem - z * width;
            blocks.add(new SchematicData.Block(x, y, z, "legacy:" + id + ":" + meta, color));
            if (blocks.size() >= maxBlocks) {
                truncated = index + 1 < expectedVolume;
                break;
            }
        }
        return new SchematicData(
                width,
                height,
                length,
                blocks,
                readBlockEntities(root, root, true),
                readEntities(root),
                truncated
        );
    }

    private static java.util.List<SchematicData.BlockEntity> readBlockEntities(CompoundTag root, CompoundTag blocksRoot, boolean legacy) {
        ListTag source = firstList(root, blocksRoot, legacy ? "TileEntities" : "BlockEntities");
        if (source.isEmpty() && !legacy) {
            source = firstList(root, blocksRoot, "TileEntities");
        }

        ArrayList<SchematicData.BlockEntity> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            CompoundTag tag = source.getCompoundOrEmpty(i).copy();
            if (tag.isEmpty()) continue;

            int[] pos = blockEntityPos(tag);
            if (pos == null) continue;
            normalizeId(tag);
            result.add(new SchematicData.BlockEntity(pos[0], pos[1], pos[2], tag));
        }
        return result;
    }

    private static java.util.List<SchematicData.Entity> readEntities(CompoundTag root) {
        ListTag source = root.getListOrEmpty("Entities");
        ArrayList<SchematicData.Entity> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            CompoundTag tag = source.getCompoundOrEmpty(i).copy();
            if (tag.isEmpty()) continue;

            double[] pos = entityPos(tag);
            if (pos == null) continue;
            normalizeId(tag);
            result.add(new SchematicData.Entity(pos[0], pos[1], pos[2], tag));
        }
        return result;
    }

    private static ListTag firstList(CompoundTag primary, CompoundTag fallback, String key) {
        ListTag list = primary.getListOrEmpty(key);
        if (!list.isEmpty()) return list;
        return fallback.getListOrEmpty(key);
    }

    private static int[] blockEntityPos(CompoundTag tag) {
        int[] pos = tag.getIntArray("Pos").orElse(null);
        if (pos != null && pos.length >= 3) {
            return new int[]{pos[0], pos[1], pos[2]};
        }
        if (!tag.contains("x") || !tag.contains("y") || !tag.contains("z")) {
            return null;
        }
        return new int[]{
                tag.getIntOr("x", 0),
                tag.getIntOr("y", 0),
                tag.getIntOr("z", 0)
        };
    }

    private static double[] entityPos(CompoundTag tag) {
        ListTag pos = tag.getListOrEmpty("Pos");
        if (pos.size() >= 3) {
            return new double[]{
                    pos.getDoubleOr(0, 0.0),
                    pos.getDoubleOr(1, 0.0),
                    pos.getDoubleOr(2, 0.0)
            };
        }
        if (!tag.contains("x") || !tag.contains("y") || !tag.contains("z")) {
            return null;
        }
        return new double[]{
                tag.getDoubleOr("x", 0.0),
                tag.getDoubleOr("y", 0.0),
                tag.getDoubleOr("z", 0.0)
        };
    }

    private static void normalizeId(CompoundTag tag) {
        if (!tag.contains("id") && tag.contains("Id")) {
            tag.putString("id", tag.getStringOr("Id", ""));
        }
    }

    private static VarIntResult readVarInt(byte[] data, int offset) {
        int value = 0;
        int shift = 0;
        int cursor = offset;
        while (cursor < data.length && shift < 35) {
            int b = data[cursor++] & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return new VarIntResult(value, cursor, true);
            }
            shift += 7;
        }
        return new VarIntResult(0, cursor, false);
    }

    private static int positive(int value) {
        return Math.max(0, value);
    }

    private static int safeVolume(int width, int height, int length) {
        long volume = (long) width * height * length;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, volume));
    }

    private static int firstInt(CompoundTag primary, CompoundTag fallback, String key) {
        int value = intOrZero(primary, key);
        return value != 0 ? value : intOrZero(fallback, key);
    }

    private static int intOrZero(CompoundTag tag, String key) {
        Tag value = tag.get(key);
        return value == null ? 0 : value.asInt().orElse(0);
    }

    private static byte[] byteArrayOrEmpty(CompoundTag tag, String key) {
        return tag.getByteArray(key).orElse(new byte[0]);
    }

    private record VarIntResult(int value, int nextOffset, boolean valid) {
    }
}
