package com.mine.geometry_node.core.schematic;

import java.util.Locale;

public final class SchematicBlockColor {
    private SchematicBlockColor() {
    }

    public static int forBlockState(String state) {
        if (state == null || state.isBlank()) return 0xFF9AA7B8;
        String id = state.toLowerCase(Locale.ROOT);
        int bracket = id.indexOf('[');
        if (bracket >= 0) id = id.substring(0, bracket);
        if (isAir(id)) return 0;

        if (id.contains("water")) return 0xFF3D78D8;
        if (id.contains("lava")) return 0xFFFF6A22;
        if (id.contains("grass") || id.contains("moss") || id.contains("azalea")) return 0xFF5FA64D;
        if (id.contains("leaves") || id.contains("vine")) return 0xFF3F8F43;
        if (id.contains("dirt") || id.contains("mud") || id.contains("farmland")) return 0xFF76543A;
        if (id.contains("sand")) return 0xFFD8C27A;
        if (id.contains("gravel")) return 0xFF8B8B86;
        if (id.contains("snow") || id.contains("quartz")) return 0xFFE7EEF1;
        if (id.contains("ice")) return 0xFF95C8E8;
        if (id.contains("glass")) return 0xFF8FC8D8;
        if (id.contains("deepslate") || id.contains("blackstone")) return 0xFF3B3F48;
        if (id.contains("stone") || id.contains("cobble") || id.contains("andesite")) return 0xFF777B7D;
        if (id.contains("granite")) return 0xFF9A6B5D;
        if (id.contains("diorite")) return 0xFFB9B9B1;
        if (id.contains("brick") || id.contains("terracotta")) return 0xFF9B5545;
        if (id.contains("planks") || id.contains("log") || id.contains("wood") || id.contains("stem")) return 0xFF9B6B3C;
        if (id.contains("copper")) return 0xFFC06F4B;
        if (id.contains("gold")) return 0xFFE4B33F;
        if (id.contains("iron")) return 0xFFC8C8C0;
        if (id.contains("diamond") || id.contains("prismarine")) return 0xFF43BFC4;
        if (id.contains("emerald")) return 0xFF3CB866;
        if (id.contains("redstone") || id.contains("netherrack")) return 0xFF8C2D2D;
        if (id.contains("obsidian") || id.contains("purple") || id.contains("amethyst")) return 0xFF4B356D;
        return stableColor(id);
    }

    public static int forLegacyBlock(int id, int meta) {
        return switch (id) {
            case 0 -> 0;
            case 1, 4, 43, 44, 48, 67, 70, 77, 97, 98, 109, 139 -> 0xFF777B7D;
            case 2 -> 0xFF5FA64D;
            case 3, 60 -> 0xFF76543A;
            case 5, 17, 53, 54, 58, 63, 64, 85, 86, 91, 96, 107, 125, 126, 134, 135, 136, 162, 163, 164 -> 0xFF9B6B3C;
            case 8, 9 -> 0xFF3D78D8;
            case 10, 11 -> 0xFFFF6A22;
            case 12, 24, 128 -> 0xFFD8C27A;
            case 13 -> 0xFF8B8B86;
            case 18, 106, 111, 161 -> 0xFF3F8F43;
            case 20, 95, 102, 160 -> 0xFF8FC8D8;
            case 22 -> 0xFF315AA6;
            case 35, 159, 171 -> legacyDyeColor(meta);
            case 41 -> 0xFFE4B33F;
            case 42, 101, 148, 167 -> 0xFFC8C8C0;
            case 45, 108 -> 0xFF9B5545;
            case 49 -> 0xFF34284B;
            case 56, 57 -> 0xFF43BFC4;
            case 73, 74, 152 -> 0xFF8C2D2D;
            case 78, 79, 80 -> 0xFFE7EEF1;
            case 82 -> 0xFF9AA8B8;
            case 87, 88, 112, 113, 114 -> 0xFF7B2E2E;
            case 89, 169 -> 0xFFE6D37C;
            case 121, 201, 202 -> 0xFFE5E1A7;
            case 129, 133 -> 0xFF3CB866;
            case 155, 156 -> 0xFFE7EEF1;
            case 168 -> 0xFF43BFC4;
            case 172 -> 0xFF9B5545;
            case 173 -> 0xFF30343A;
            default -> stableColor("legacy:" + id + ":" + meta);
        };
    }

    private static boolean isAir(String id) {
        return id.endsWith(":air") || id.endsWith(":cave_air") || id.endsWith(":void_air") || id.equals("air");
    }

    private static int legacyDyeColor(int meta) {
        return switch (meta & 15) {
            case 0 -> 0xFFE9ECEC;
            case 1 -> 0xFFC36A2D;
            case 2 -> 0xFF8F4BB3;
            case 3 -> 0xFF4A90C8;
            case 4 -> 0xFFE0B83C;
            case 5 -> 0xFF5FA64D;
            case 6 -> 0xFFD8889B;
            case 7 -> 0xFF4D4D4D;
            case 8 -> 0xFF999999;
            case 9 -> 0xFF2E8B8B;
            case 10 -> 0xFF6B3FA0;
            case 11 -> 0xFF2D3F93;
            case 12 -> 0xFF5C3B22;
            case 13 -> 0xFF3F7D2D;
            case 14 -> 0xFF9B2E2E;
            default -> 0xFF202020;
        };
    }

    private static int stableColor(String id) {
        int hash = id.hashCode();
        int r = 90 + Math.floorMod(hash, 120);
        int g = 90 + Math.floorMod(hash >> 8, 120);
        int b = 90 + Math.floorMod(hash >> 16, 120);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
