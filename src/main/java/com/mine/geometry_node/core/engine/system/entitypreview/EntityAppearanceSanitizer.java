package com.mine.geometry_node.core.engine.system.entitypreview;

import net.minecraft.nbt.*;

import java.util.Set;

/** Positive schemas, including nested items. No arbitrary entity/component NBT is retained. */
public final class EntityAppearanceSanitizer {
    private static final Set<String> SCALARS = Set.of(
            "CustomNameVisible", "Invisible", "Glowing", "HasVisualFire", "TicksFrozen", "LeftHanded",
            "Age", "IsBaby", "Size", "size", "Small", "ShowArms", "NoBasePlate", "Marker",
            "variant", "Variant", "Type", "type", "Color", "CollarColor", "Sheared", "sheared",
            "RabbitType", "PuffState", "HasLeftHorn", "HasRightHorn", "IsScreamingGoat", "Pumpkin",
            "ChestedHorse", "Strength", "Tame", "Sitting", "Sleeping", "Crouching", "EatingHaystack",
            "MainGene", "HiddenGene", "HasNectar", "HasStung", "BatFlags", "powered", "Invul",
            "AttachFace", "Peek", "weather_state", "state", "ShowBottom", "facing", "Facing",
            "ItemRotation", "DisplayOffset", "billboard", "view_range", "shadow_radius", "shadow_strength",
            "width", "height", "glow_color_override", "item_display", "line_width", "background",
            "text_opacity", "shadow", "see_through", "default_background", "alignment");
    private static final Set<String> COMPLEX = Set.of("CustomName", "text", "equipment", "Item", "item",
            "FireworksItem", "Pose", "VillagerData", "BlockState", "block_state", "carriedBlockState",
            "DisplayState", "transformation", "brightness");
    public static final Set<String> ITEM_COMPONENTS = Set.of(
            "minecraft:item_model", "minecraft:custom_model_data", "minecraft:dyed_color",
            "minecraft:enchantment_glint_override", "minecraft:damage", "minecraft:max_damage",
            "minecraft:trim", "minecraft:equippable", "minecraft:potion_contents",
            "minecraft:banner_patterns", "minecraft:base_color", "minecraft:block_state");
    private static final Set<String> SLOTS = Set.of("mainhand", "offhand", "head", "chest", "legs", "feet", "body", "saddle");
    private EntityAppearanceSanitizer() {}

    public static boolean allows(String field) { return SCALARS.contains(field) || COMPLEX.contains(field); }

    public static CompoundTag sanitize(CompoundTag source) {
        EntityPreviewNbt.validate(source);
        CompoundTag result = new CompoundTag();
        for (var entry : source.entrySet()) {
            Tag value = field(entry.getKey(), entry.getValue());
            if (value != null) result.put(entry.getKey(), value);
        }
        EntityPreviewNbt.validate(result);
        return result;
    }

    private static Tag field(String name, Tag value) {
        if (SCALARS.contains(name)) return scalar(value);
        return switch (name) {
            case "CustomName", "text" -> text(value);
            case "Item", "item", "FireworksItem" -> item(value);
            case "equipment" -> {
                CompoundTag out = new CompoundTag();
                if (value instanceof CompoundTag equipment) for (String slot : SLOTS) {
                    Tag stack = item(equipment.get(slot));
                    if (stack != null) out.put(slot, stack);
                }
                yield out;
            }
            case "Pose" -> select(value, Set.of("Head", "Body", "LeftArm", "RightArm", "LeftLeg", "RightLeg"), true);
            case "VillagerData" -> select(value, Set.of("type", "profession", "level"), false);
            case "BlockState", "block_state", "carriedBlockState", "DisplayState" -> block(value);
            case "transformation" -> value instanceof ListTag ? numericTree(value)
                    : select(value, Set.of("translation", "scale", "left_rotation", "right_rotation"), true);
            case "brightness" -> select(value, Set.of("block", "sky"), false);
            default -> null;
        };
    }

    private static Tag scalar(Tag value) {
        return value instanceof NumericTag || value instanceof StringTag ? value.copy() : null;
    }
    private static Tag numericTree(Tag value) {
        if (value instanceof NumericTag) return value.copy();
        if (value instanceof ListTag list) {
            ListTag result = new ListTag();
            for (Tag child : list) { Tag safe = numericTree(child); if (safe != null) result.add(safe); }
            return result;
        }
        return null;
    }
    private static CompoundTag select(Tag value, Set<String> keys, boolean numbers) {
        CompoundTag result = new CompoundTag();
        if (value instanceof CompoundTag compound) for (String key : keys) {
            Tag child = compound.get(key);
            Tag safe = numbers ? numericTree(child) : scalar(child);
            if (safe != null) result.put(key, safe);
        }
        return result;
    }
    private static Tag block(Tag value) {
        CompoundTag result = select(value, Set.of("Name"), false);
        if (value instanceof CompoundTag compound && compound.get("Properties") instanceof CompoundTag props) {
            CompoundTag safe = new CompoundTag();
            for (var e : props.entrySet()) if (e.getValue() instanceof StringTag) safe.put(e.getKey(), e.getValue().copy());
            result.put("Properties", safe);
        }
        return result;
    }

    /** Called only after a bounded read/encode. Events, selectors and embedded NBT are excluded. */
    private static Tag text(Tag value) {
        if (value instanceof StringTag) return value.copy();
        if (value instanceof ListTag list) {
            ListTag result = new ListTag();
            for (Tag child : list) { Tag safe = text(child); if (safe != null) result.add(safe); }
            return result;
        }
        if (!(value instanceof CompoundTag compound)) return null;
        // Only static literal/translation contents can be rendered without consulting live world data.
        if (!compound.contains("text") && !compound.contains("translate")) return null;
        CompoundTag result = select(compound, Set.of("text", "translate", "fallback", "color", "font",
                "bold", "italic", "underlined", "strikethrough", "obfuscated"), false);
        Tag extra = text(compound.get("extra"));
        if (extra != null) result.put("extra", extra);
        if (compound.get("with") instanceof ListTag arguments) {
            ListTag safe = new ListTag();
            for (Tag argument : arguments) {
                Tag sanitized = argument instanceof NumericTag ? argument.copy() : text(argument);
                if (sanitized == null) throw new IllegalArgumentException("Unsupported preview translation argument");
                safe.add(sanitized);
            }
            result.put("with", safe);
        }
        return result;
    }

    private static Tag item(Tag value) {
        if (!(value instanceof CompoundTag compound)) return null;
        CompoundTag result = select(compound, Set.of("id", "count"), false);
        if (compound.get("components") instanceof CompoundTag components) {
            CompoundTag safe = new CompoundTag();
            for (String key : ITEM_COMPONENTS) {
                Tag component = components.get(key);
                if (component == null) continue;
                Tag sanitized = switch (key) {
                    case "minecraft:custom_model_data" -> select(component, Set.of("floats", "flags", "strings", "colors"), false);
                    case "minecraft:trim" -> select(component, Set.of("material", "pattern"), false);
                    case "minecraft:equippable" -> select(component, Set.of("slot", "asset_id", "camera_overlay"), false);
                    case "minecraft:potion_contents" -> select(component, Set.of("potion", "custom_color"), false);
                    case "minecraft:block_state" -> select(component, component instanceof CompoundTag c ? c.keySet() : Set.of(), false);
                    case "minecraft:banner_patterns" -> bannerPatterns(component);
                    default -> scalar(component);
                };
                if (key.equals("minecraft:custom_model_data") && component instanceof CompoundTag model) {
                    CompoundTag data = new CompoundTag();
                    for (String field : Set.of("floats", "flags", "strings", "colors")) {
                        if (model.get(field) instanceof ListTag list) {
                            ListTag values = new ListTag();
                            for (Tag child : list) { Tag s = scalar(child); if (s != null) values.add(s); }
                            data.put(field, values);
                        }
                    }
                    sanitized = data;
                }
                if (sanitized != null) safe.put(key, sanitized);
            }
            result.put("components", safe);
        }
        return result;
    }
    private static Tag bannerPatterns(Tag value) {
        ListTag result = new ListTag();
        if (value instanceof ListTag list) for (Tag child : list)
            result.add(select(child, Set.of("pattern", "color"), false));
        return result;
    }
}
