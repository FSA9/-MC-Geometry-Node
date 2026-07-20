package com.mine.geometry_node.core.schematic;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public final class LegacySchematicBlockStateMapper {
    private static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };
    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle"};
    private static final String[] WOODS_6 = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};

    private LegacySchematicBlockStateMapper() {
    }

    public static BlockState fromRaw(String raw) {
        if (raw == null || !raw.startsWith("legacy:")) {
            return null;
        }

        String[] parts = raw.split(":", 3);
        if (parts.length != 3) {
            return null;
        }

        int id;
        int meta;
        try {
            id = Integer.parseInt(parts[1]);
            meta = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ignored) {
            return null;
        }

        return blockState(blockPath(id, meta));
    }

    private static BlockState blockState(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Identifier id = Identifier.tryParse(path.indexOf(':') >= 0 ? path : "minecraft:" + path);
        if (id == null) {
            return null;
        }
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
        return block.map(Block::defaultBlockState).orElse(null);
    }

    private static String blockPath(int id, int meta) {
        return switch (id) {
            case 0 -> "air";
            case 1 -> stoneVariant(meta);
            case 2 -> "grass_block";
            case 3 -> switch (meta & 3) {
                case 1 -> "coarse_dirt";
                case 2 -> "podzol";
                default -> "dirt";
            };
            case 4 -> "cobblestone";
            case 5 -> wood6(meta, "planks");
            case 6 -> wood6(meta, "sapling");
            case 7 -> "bedrock";
            case 8, 9 -> "water";
            case 10, 11 -> "lava";
            case 12 -> (meta & 1) == 1 ? "red_sand" : "sand";
            case 13 -> "gravel";
            case 14 -> "gold_ore";
            case 15 -> "iron_ore";
            case 16 -> "coal_ore";
            case 17 -> wood(meta, "log");
            case 18 -> wood(meta, "leaves");
            case 19 -> (meta & 1) == 1 ? "wet_sponge" : "sponge";
            case 20 -> "glass";
            case 21 -> "lapis_ore";
            case 22 -> "lapis_block";
            case 23 -> "dispenser";
            case 24 -> switch (meta & 3) {
                case 1 -> "chiseled_sandstone";
                case 2 -> "cut_sandstone";
                default -> "sandstone";
            };
            case 25 -> "note_block";
            case 26 -> "red_bed";
            case 27 -> "powered_rail";
            case 28 -> "detector_rail";
            case 29 -> "sticky_piston";
            case 30 -> "cobweb";
            case 31 -> switch (meta & 3) {
                case 1 -> "short_grass";
                case 2 -> "fern";
                default -> "dead_bush";
            };
            case 32 -> "dead_bush";
            case 33 -> "piston";
            case 34 -> "piston_head";
            case 35 -> color(meta, "wool");
            case 37 -> "dandelion";
            case 38 -> flower(meta);
            case 39 -> "brown_mushroom";
            case 40 -> "red_mushroom";
            case 41 -> "gold_block";
            case 42 -> "iron_block";
            case 43 -> doubleStoneSlab(meta);
            case 44 -> stoneSlab(meta);
            case 45 -> "bricks";
            case 46 -> "tnt";
            case 47 -> "bookshelf";
            case 48 -> "mossy_cobblestone";
            case 49 -> "obsidian";
            case 50 -> "torch";
            case 51 -> "fire";
            case 52 -> "spawner";
            case 53 -> "oak_stairs";
            case 54 -> "chest";
            case 55 -> "redstone_wire";
            case 56 -> "diamond_ore";
            case 57 -> "diamond_block";
            case 58 -> "crafting_table";
            case 59 -> "wheat";
            case 60 -> "farmland";
            case 61, 62 -> "furnace";
            case 63 -> "oak_sign";
            case 64 -> "oak_door";
            case 65 -> "ladder";
            case 66 -> "rail";
            case 67 -> "cobblestone_stairs";
            case 68 -> "oak_wall_sign";
            case 69 -> "lever";
            case 70 -> "stone_pressure_plate";
            case 71 -> "iron_door";
            case 72 -> "oak_pressure_plate";
            case 73, 74 -> "redstone_ore";
            case 75, 76 -> "redstone_torch";
            case 77 -> "stone_button";
            case 78 -> "snow";
            case 79 -> "ice";
            case 80 -> "snow_block";
            case 81 -> "cactus";
            case 82 -> "clay";
            case 83 -> "sugar_cane";
            case 84 -> "jukebox";
            case 85 -> "oak_fence";
            case 86 -> "pumpkin";
            case 87 -> "netherrack";
            case 88 -> "soul_sand";
            case 89 -> "glowstone";
            case 90 -> "nether_portal";
            case 91 -> "jack_o_lantern";
            case 92 -> "cake";
            case 93, 94 -> "repeater";
            case 95 -> color(meta, "stained_glass");
            case 96 -> "oak_trapdoor";
            case 97 -> infested(meta);
            case 98 -> stoneBrick(meta);
            case 99 -> "brown_mushroom_block";
            case 100 -> "red_mushroom_block";
            case 101 -> "iron_bars";
            case 102 -> "glass_pane";
            case 103 -> "melon";
            case 104 -> "pumpkin_stem";
            case 105 -> "melon_stem";
            case 106 -> "vine";
            case 107 -> "oak_fence_gate";
            case 108 -> "brick_stairs";
            case 109 -> "stone_brick_stairs";
            case 110 -> "mycelium";
            case 111 -> "lily_pad";
            case 112 -> "nether_bricks";
            case 113 -> "nether_brick_fence";
            case 114 -> "nether_brick_stairs";
            case 115 -> "nether_wart";
            case 116 -> "enchanting_table";
            case 117 -> "brewing_stand";
            case 118 -> "cauldron";
            case 119 -> "end_portal";
            case 120 -> "end_portal_frame";
            case 121 -> "end_stone";
            case 122 -> "dragon_egg";
            case 123, 124 -> "redstone_lamp";
            case 125 -> wood6(meta, "planks");
            case 126 -> wood6(meta, "slab");
            case 127 -> "cocoa";
            case 128 -> "sandstone_stairs";
            case 129 -> "emerald_ore";
            case 130 -> "ender_chest";
            case 131 -> "tripwire_hook";
            case 132 -> "tripwire";
            case 133 -> "emerald_block";
            case 134 -> "spruce_stairs";
            case 135 -> "birch_stairs";
            case 136 -> "jungle_stairs";
            case 137 -> "command_block";
            case 138 -> "beacon";
            case 139 -> (meta & 1) == 1 ? "mossy_cobblestone_wall" : "cobblestone_wall";
            case 140 -> "flower_pot";
            case 141 -> "carrots";
            case 142 -> "potatoes";
            case 143 -> "oak_button";
            case 144 -> "skeleton_skull";
            case 145 -> "anvil";
            case 146 -> "trapped_chest";
            case 147 -> "light_weighted_pressure_plate";
            case 148 -> "heavy_weighted_pressure_plate";
            case 149, 150 -> "comparator";
            case 151, 178 -> "daylight_detector";
            case 152 -> "redstone_block";
            case 153 -> "nether_quartz_ore";
            case 154 -> "hopper";
            case 155 -> quartz(meta);
            case 156 -> "quartz_stairs";
            case 157 -> "activator_rail";
            case 158 -> "dropper";
            case 159 -> color(meta, "terracotta");
            case 160 -> color(meta, "stained_glass_pane");
            case 161 -> (meta & 1) == 1 ? "dark_oak_leaves" : "acacia_leaves";
            case 162 -> (meta & 1) == 1 ? "dark_oak_log" : "acacia_log";
            case 163 -> "acacia_stairs";
            case 164 -> "dark_oak_stairs";
            case 165 -> "slime_block";
            case 166 -> "barrier";
            case 167 -> "iron_trapdoor";
            case 168 -> prismarine(meta);
            case 169 -> "sea_lantern";
            case 170 -> "hay_block";
            case 171 -> color(meta, "carpet");
            case 172 -> "terracotta";
            case 173 -> "coal_block";
            case 174 -> "packed_ice";
            case 175 -> doublePlant(meta);
            case 176 -> "white_banner";
            case 177 -> "white_wall_banner";
            case 179 -> redSandstone(meta);
            case 180 -> "red_sandstone_stairs";
            case 181 -> redSandstone(meta);
            case 182 -> "red_sandstone_slab";
            case 183 -> "spruce_fence_gate";
            case 184 -> "birch_fence_gate";
            case 185 -> "jungle_fence_gate";
            case 186 -> "dark_oak_fence_gate";
            case 187 -> "acacia_fence_gate";
            case 188 -> "spruce_fence";
            case 189 -> "birch_fence";
            case 190 -> "jungle_fence";
            case 191 -> "dark_oak_fence";
            case 192 -> "acacia_fence";
            case 193 -> "spruce_door";
            case 194 -> "birch_door";
            case 195 -> "jungle_door";
            case 196 -> "acacia_door";
            case 197 -> "dark_oak_door";
            case 198 -> "end_rod";
            case 199 -> "chorus_plant";
            case 200 -> "chorus_flower";
            case 201 -> "purpur_block";
            case 202 -> "purpur_pillar";
            case 203 -> "purpur_stairs";
            case 204, 205 -> "purpur_slab";
            case 206 -> "end_stone_bricks";
            case 207 -> "beetroots";
            case 208 -> "dirt_path";
            case 209 -> "end_gateway";
            case 210 -> "repeating_command_block";
            case 211 -> "chain_command_block";
            case 212 -> "frosted_ice";
            case 213 -> "magma_block";
            case 214 -> "nether_wart_block";
            case 215 -> "red_nether_bricks";
            case 216 -> "bone_block";
            case 217 -> "structure_void";
            case 218 -> "observer";
            case 219, 220, 221, 222, 223, 224, 225, 226,
                 227, 228, 229, 230, 231, 232, 233, 234 -> color(id - 219, "shulker_box");
            case 235, 236, 237, 238, 239, 240, 241, 242,
                 243, 244, 245, 246, 247, 248, 249, 250 -> color(id - 235, "glazed_terracotta");
            case 251 -> color(meta, "concrete");
            case 252 -> color(meta, "concrete_powder");
            case 255 -> "structure_block";
            default -> null;
        };
    }

    private static String color(int meta, String suffix) {
        return COLORS[meta & 15] + "_" + suffix;
    }

    private static String wood(int meta, String suffix) {
        return WOODS[meta & 3] + "_" + suffix;
    }

    private static String wood6(int meta, String suffix) {
        return WOODS_6[(meta & 7) % WOODS_6.length] + "_" + suffix;
    }

    private static String stoneVariant(int meta) {
        return switch (meta & 7) {
            case 1 -> "granite";
            case 2 -> "polished_granite";
            case 3 -> "diorite";
            case 4 -> "polished_diorite";
            case 5 -> "andesite";
            case 6 -> "polished_andesite";
            default -> "stone";
        };
    }

    private static String flower(int meta) {
        return switch (meta & 15) {
            case 1 -> "blue_orchid";
            case 2 -> "allium";
            case 3 -> "azure_bluet";
            case 4 -> "red_tulip";
            case 5 -> "orange_tulip";
            case 6 -> "white_tulip";
            case 7 -> "pink_tulip";
            case 8 -> "oxeye_daisy";
            default -> "poppy";
        };
    }

    private static String doubleStoneSlab(int meta) {
        return switch (meta & 7) {
            case 1 -> "sandstone";
            case 2 -> "oak_planks";
            case 3 -> "cobblestone";
            case 4 -> "bricks";
            case 5 -> "stone_bricks";
            case 6 -> "nether_bricks";
            case 7 -> "quartz_block";
            default -> "smooth_stone";
        };
    }

    private static String stoneSlab(int meta) {
        return switch (meta & 7) {
            case 1 -> "sandstone_slab";
            case 2 -> "petrified_oak_slab";
            case 3 -> "cobblestone_slab";
            case 4 -> "brick_slab";
            case 5 -> "stone_brick_slab";
            case 6 -> "nether_brick_slab";
            case 7 -> "quartz_slab";
            default -> "smooth_stone_slab";
        };
    }

    private static String infested(int meta) {
        return switch (meta & 7) {
            case 1 -> "infested_cobblestone";
            case 2 -> "infested_stone_bricks";
            case 3 -> "infested_mossy_stone_bricks";
            case 4 -> "infested_cracked_stone_bricks";
            case 5 -> "infested_chiseled_stone_bricks";
            default -> "infested_stone";
        };
    }

    private static String stoneBrick(int meta) {
        return switch (meta & 3) {
            case 1 -> "mossy_stone_bricks";
            case 2 -> "cracked_stone_bricks";
            case 3 -> "chiseled_stone_bricks";
            default -> "stone_bricks";
        };
    }

    private static String quartz(int meta) {
        return switch (meta & 3) {
            case 1 -> "chiseled_quartz_block";
            case 2 -> "quartz_pillar";
            default -> "quartz_block";
        };
    }

    private static String prismarine(int meta) {
        return switch (meta & 3) {
            case 1 -> "prismarine_bricks";
            case 2 -> "dark_prismarine";
            default -> "prismarine";
        };
    }

    private static String doublePlant(int meta) {
        return switch (meta & 7) {
            case 1 -> "lilac";
            case 2 -> "tall_grass";
            case 3 -> "large_fern";
            case 4 -> "rose_bush";
            case 5 -> "peony";
            default -> "sunflower";
        };
    }

    private static String redSandstone(int meta) {
        return switch (meta & 3) {
            case 1 -> "chiseled_red_sandstone";
            case 2 -> "cut_red_sandstone";
            default -> "red_sandstone";
        };
    }
}
