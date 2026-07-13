package com.mine.geometry_node.core.node.value;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

public record SlotRef(String space, String key) {
    public static final String PLAYER_INVENTORY = "minecraft:player_inventory";
    public static final String EQUIPMENT = "minecraft:equipment";
    public static final String CONTAINER = "minecraft:container";
    public static final String ENTITY_ITEM_HANDLER = "minecraft:entity_item_handler";

    public static final SlotRef DEFAULT = new SlotRef(PLAYER_INVENTORY, "hotbar.0");

    public SlotRef {
        space = normalizeSpace(space);
        key = normalizeKey(key);
    }

    public String serialize() {
        return space + "|" + key;
    }

    public String displayName() {
        return switch (space) {
            case PLAYER_INVENTORY -> playerInventoryLabel(key);
            case EQUIPMENT -> equipmentLabel(key);
            case CONTAINER -> "Container " + key;
            case ENTITY_ITEM_HANDLER -> "Item Handler " + key;
            default -> space + " " + key;
        };
    }

    @Nullable
    public static SlotRef from(@Nullable Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof SlotRef slotRef) {
            return slotRef;
        }
        if (raw instanceof String string) {
            return parse(string);
        }
        if (raw instanceof Map<?, ?> map) {
            Object space = map.get("space");
            Object key = map.get("key");
            if (space != null && key != null) {
                return new SlotRef(String.valueOf(space), String.valueOf(key));
            }
        }
        return null;
    }

    @Nullable
    public static SlotRef parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }

        int separator = value.indexOf('|');
        if (separator >= 0) {
            String space = value.substring(0, separator);
            String key = value.substring(separator + 1);
            if (!space.isBlank() && !key.isBlank()) {
                return new SlotRef(space, key);
            }
            return null;
        }

        if (value.startsWith("hotbar.") || value.startsWith("main.") || value.startsWith("inventory.") || value.startsWith("raw.")) {
            return new SlotRef(PLAYER_INVENTORY, value);
        }
        if (isEquipmentKey(value)) {
            return new SlotRef(EQUIPMENT, value);
        }
        return new SlotRef(CONTAINER, value);
    }

    public static String compact(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    private static String normalizeSpace(String value) {
        if (value == null || value.isBlank()) {
            return PLAYER_INVENTORY;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            return "0";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isEquipmentKey(String value) {
        String key = compact(value);
        return key.equals("mainhand")
                || key.equals("offhand")
                || key.equals("head")
                || key.equals("chest")
                || key.equals("legs")
                || key.equals("feet");
    }

    private static String playerInventoryLabel(String key) {
        Integer hotbar = suffixInt(key, "hotbar.");
        if (hotbar != null) {
            return "Hotbar " + (hotbar + 1);
        }
        Integer main = suffixInt(key, "main.");
        if (main != null) {
            return "Inventory " + (main + 1);
        }
        Integer raw = suffixInt(key, "raw.");
        if (raw != null) {
            return "Inventory Raw " + raw;
        }
        Integer inventory = suffixInt(key, "inventory.");
        if (inventory != null) {
            return "Inventory Raw " + inventory;
        }
        return "Inventory " + key;
    }

    private static String equipmentLabel(String key) {
        return switch (compact(key)) {
            case "mainhand" -> "Main Hand";
            case "offhand" -> "Off Hand";
            case "head" -> "Head";
            case "chest" -> "Chest";
            case "legs" -> "Legs";
            case "feet" -> "Feet";
            default -> "Equipment " + key;
        };
    }

    @Nullable
    private static Integer suffixInt(String key, String prefix) {
        if (!key.startsWith(prefix)) {
            return null;
        }
        try {
            return Integer.parseInt(key.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public String toString() {
        return serialize();
    }
}
