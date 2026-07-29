package com.mine.geometry_node.client.ui.editor.asset.schematic;

import com.mine.geometry_node.core.schematic.SchematicBlockColor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SchematicThumbnailReader {
    private static final long MAX_NBT_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_GRID_COLUMNS = 32;
    private static final int MAX_DECODED_BLOCKS = 8_000_000;

    private SchematicThumbnailReader() {
    }

    static SchematicThumbnail read(File file) throws IOException, InterruptedException {
        checkInterrupted();
        CompoundTag root = readRoot(file.toPath());
        checkInterrupted();
        if (looksLikeSponge(root)) {
            return readSponge(root);
        }
        if (looksLikeLegacy(root)) {
            return readLegacy(root);
        }
        return SchematicThumbnail.error("unsupported schematic");
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

    private static SchematicThumbnail readSponge(CompoundTag root) throws InterruptedException {
        int width = positive(intOrZero(root, "Width"));
        int height = positive(intOrZero(root, "Height"));
        int length = positive(intOrZero(root, "Length"));
        if (width <= 0 || height <= 0 || length <= 0) {
            return SchematicThumbnail.error("invalid size");
        }

        CompoundTag nestedBlocks = root.getCompoundOrEmpty("Blocks");
        CompoundTag blocksRoot = nestedBlocks.contains("Palette") || nestedBlocks.contains("BlockData") || nestedBlocks.contains("Data")
                ? nestedBlocks
                : root;
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
            return SchematicThumbnail.error("empty palette");
        }

        Map<Integer, String> paletteById = new HashMap<>();
        for (String key : palette.keySet()) {
            paletteById.put(intOrZero(palette, key), key);
        }

        ColumnBuffer columns = new ColumnBuffer(width, height, length);
        int volume = safeVolume(width, height, length);
        int maxBlocks = Math.min(volume, MAX_DECODED_BLOCKS);
        int index = 0;
        int offset = 0;
        int layer = width * length;
        while (offset < blockData.length && index < maxBlocks) {
            if ((index & 4095) == 0) {
                checkInterrupted();
            }
            VarIntResult result = readVarInt(blockData, offset);
            if (!result.valid()) {
                break;
            }
            offset = result.nextOffset();
            String state = paletteById.get(result.value());
            int color = SchematicBlockColor.forBlockState(state);
            if (color != 0) {
                int y = index / layer;
                int rem = index - y * layer;
                int z = rem / width;
                int x = rem - z * width;
                columns.accept(x, y, z, color, state);
            }
            index++;
        }
        return columns.toThumbnail(index < volume);
    }

    private static SchematicThumbnail readLegacy(CompoundTag root) throws InterruptedException {
        int width = positive(intOrZero(root, "Width"));
        int height = positive(intOrZero(root, "Height"));
        int length = positive(intOrZero(root, "Length"));
        if (width <= 0 || height <= 0 || length <= 0) {
            return SchematicThumbnail.error("invalid size");
        }

        byte[] blocks = byteArrayOrEmpty(root, "Blocks");
        byte[] data = byteArrayOrEmpty(root, "Data");
        byte[] addBlocks = root.contains("AddBlocks") ? byteArrayOrEmpty(root, "AddBlocks") : new byte[0];
        if (blocks.length == 0) {
            return SchematicThumbnail.error("empty blocks");
        }

        ColumnBuffer columns = new ColumnBuffer(width, height, length);
        int expectedVolume = safeVolume(width, height, length);
        int volume = Math.min(expectedVolume, Math.min(blocks.length, MAX_DECODED_BLOCKS));
        int layer = width * length;
        int filledColumns = 0;
        for (int index = volume - 1; index >= 0; index--) {
            if (((volume - index) & 4095) == 0) {
                checkInterrupted();
            }
            int y = index / layer;
            int rem = index - y * layer;
            int z = rem / width;
            int x = rem - z * width;
            if (columns.hasColumn(x, z)) {
                continue;
            }

            int id = blocks[index] & 0xFF;
            if (addBlocks.length > (index >> 1)) {
                int packed = addBlocks[index >> 1] & 0xFF;
                int high = (index & 1) == 0 ? (packed & 0x0F) : ((packed >> 4) & 0x0F);
                id |= high << 8;
            }
            if (id == 0) continue;

            int meta = data.length > index ? data[index] & 0x0F : 0;
            int color = SchematicBlockColor.forLegacyBlock(id, meta);
            if (columns.accept(x, y, z, color, "legacy:" + id + ":" + meta)) {
                filledColumns++;
                if (filledColumns >= columns.capacity()) {
                    break;
                }
            }
        }
        return columns.toThumbnail(volume < expectedVolume);
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

    private static void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("schematic thumbnail load cancelled");
        }
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

    private static final class ColumnBuffer {
        private final int width;
        private final int height;
        private final int length;
        private final int stepX;
        private final int stepZ;
        private final int gridWidth;
        private final int gridLength;
        private final int[] topY;
        private final int[] colors;
        private final String[] states;

        private ColumnBuffer(int width, int height, int length) {
            this.width = width;
            this.height = height;
            this.length = length;
            this.gridWidth = Math.max(1, Math.min(MAX_GRID_COLUMNS, width));
            this.gridLength = Math.max(1, Math.min(MAX_GRID_COLUMNS, length));
            this.stepX = Math.max(1, (int) Math.ceil(width / (double) gridWidth));
            this.stepZ = Math.max(1, (int) Math.ceil(length / (double) gridLength));
            this.topY = new int[gridWidth * gridLength];
            this.colors = new int[gridWidth * gridLength];
            this.states = new String[gridWidth * gridLength];
            Arrays.fill(topY, -1);
        }

        private boolean accept(int x, int y, int z, int color, String state) {
            int sx = Math.min(gridWidth - 1, Math.max(0, x / stepX));
            int sz = Math.min(gridLength - 1, Math.max(0, z / stepZ));
            int index = sz * gridWidth + sx;
            if (y >= topY[index]) {
                boolean firstFill = topY[index] < 0;
                topY[index] = y;
                colors[index] = color;
                states[index] = state == null ? "" : state;
                return firstFill;
            }
            return false;
        }

        private boolean hasColumn(int x, int z) {
            int sx = Math.min(gridWidth - 1, Math.max(0, x / stepX));
            int sz = Math.min(gridLength - 1, Math.max(0, z / stepZ));
            return topY[sz * gridWidth + sx] >= 0;
        }

        private int capacity() {
            return topY.length;
        }

        private SchematicThumbnail toThumbnail(boolean incomplete) {
            List<SchematicThumbnail.Column> columns = new ArrayList<>();
            for (int z = 0; z < gridLength; z++) {
                for (int x = 0; x < gridWidth; x++) {
                    int index = z * gridWidth + x;
                    if (topY[index] >= 0) {
                        columns.add(new SchematicThumbnail.Column(x, z, topY[index], colors[index], states[index]));
                    }
                }
            }
            columns.sort(Comparator
                    .comparingInt((SchematicThumbnail.Column column) -> column.x() + column.z())
                    .thenComparingInt(SchematicThumbnail.Column::z));
            return new SchematicThumbnail(width, height, length, gridWidth, gridLength, columns, incomplete, "");
        }
    }
}
