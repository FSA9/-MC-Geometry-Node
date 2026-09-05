package com.mine.geometry_node.core.node.definition.port;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.value.GraphEntityReferenceResolver;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mine.geometry_node.core.node.value.SlotRef;
import com.mine.geometry_node.core.node.value.color.ColorValue;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import com.mine.geometry_node.core.node.value.dynamic.DynamicData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Canonical registry for implicit data-port conversions. Connection checks and
 * runtime conversion must both consult this registry.
 */
public final class PortConversionRegistry {
    private static final Map<PortType, Map<PortType, Converter>> RULES = createRules();
    private static final EnumSet<PortType> LIST_LIFT_TARGETS = EnumSet.of(PortType.ENTITY);

    private PortConversionRegistry() {
    }

    public static boolean isCompatible(@Nullable PortType source, @Nullable PortType target) {
        if (source == null || target == null) return false;
        if (source.isFlow() || target.isFlow()) return source == target;
        if (source == PortType.ANY || target == PortType.ANY || source == target) return true;
        if (isListLift(source, target)) return true;
        return RULES.getOrDefault(source, Map.of()).containsKey(target);
    }

    @Nullable
    public static Object convert(@Nullable Object value, PortType target,
                                 @Nullable GraphDataContext context) {
        if (value == null || target == null || target.isFlow()) return null;
        PortType source = PortType.getTypeOf(value);
        return convert(value, source, target, context);
    }

    /**
     * Converts across declared schema types. Runtime-class inference is used
     * only when an ANY source has no stronger type information.
     */
    @Nullable
    public static Object convert(@Nullable Object value, @Nullable PortType source,
                                 @Nullable PortType target,
                                 @Nullable GraphDataContext context) {
        if (value == null || target == null || target.isFlow()) return null;
        if (target == PortType.ANY) return value;

        DynamicData dynamic = value instanceof DynamicData wrapped ? wrapped : null;
        if (dynamic != null) {
            value = dynamic.value();
            if (value == null) return null;
        }
        if (source == null || source == PortType.ANY) source = PortType.getTypeOf(value);
        if (isLiftedListValue(value, source, target)) {
            return value;
        }
        if (source == target) {
            Object converted;
            if (target == PortType.ENTITY && value instanceof UUID entityId) {
                converted = resolveEntity(entityId, context);
            } else if (value instanceof Number number
                    && (target == PortType.INTEGER || target == PortType.LONG
                    || target == PortType.FLOAT)) {
                converted = switch (target) {
                    case INTEGER -> roundToInteger(number);
                    case LONG -> number.longValue();
                    case FLOAT -> number.floatValue();
                    default -> throw new IllegalStateException("Unexpected numeric port: " + target);
                };
            } else if (target == PortType.XYZ && value instanceof BlockPos pos) {
                converted = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            } else if (target == PortType.XYZ && value instanceof List<?>) {
                converted = listVector(value, context);
            } else {
                PortType actual = PortType.getTypeOf(value);
                Converter canonicalizer = RULES.getOrDefault(actual, Map.of()).get(target);
                converted = actual != source
                        ? (canonicalizer != null ? canonicalizer.convert(value, context) : null)
                        : value;
            }
            return dynamic != null && converted != null
                    ? new DynamicData(converted, dynamic.expression())
                    : converted;
        }

        Converter converter = RULES.getOrDefault(source, Map.of()).get(target);
        return converter != null ? converter.convert(value, context) : null;
    }

    /**
     * Collection lifting is a connection-shape rule, not a scalar conversion.
     * ENTITY inputs intentionally accept either one entity reference or a list;
     * the consuming node decides whether to read one indexed value or all values.
     */
    private static boolean isListLift(PortType source, PortType target) {
        return source == PortType.LIST && LIST_LIFT_TARGETS.contains(target);
    }

    private static boolean isLiftedListValue(Object value, PortType source, PortType target) {
        if (!(value instanceof List<?>) || !LIST_LIFT_TARGETS.contains(target)) return false;
        // After a passthrough port, the declared source is ENTITY while the
        // lifted runtime value intentionally retains its collection shape.
        return source == PortType.LIST || source == target;
    }

    @Nullable
    private static Entity resolveEntity(UUID entityId, @Nullable GraphDataContext context) {
        return GraphEntityReferenceResolver.resolve(entityId, context);
    }

    private static Map<PortType, Map<PortType, Converter>> createRules() {
        EnumMap<PortType, Map<PortType, Converter>> rules = new EnumMap<>(PortType.class);

        registerNumericRules(rules);
        register(rules, PortType.INTEGER, PortType.COLOR,
                (value, context) -> ColorValue.fromArgb(((Number) value).intValue()));
        register(rules, PortType.LONG, PortType.COLOR,
                (value, context) -> ColorValue.fromArgb(((Number) value).intValue()));
        register(rules, PortType.FLOAT, PortType.COLOR,
                (value, context) -> ColorValue.fromArgb(((Number) value).intValue()));
        register(rules, PortType.COLOR, PortType.INTEGER,
                (value, context) -> ((ColorValue) value).toArgb());

        register(rules, PortType.INTEGER, PortType.XYZ, PortConversionRegistry::scalarVector);
        register(rules, PortType.LONG, PortType.XYZ, PortConversionRegistry::scalarVector);
        register(rules, PortType.FLOAT, PortType.XYZ, PortConversionRegistry::scalarVector);
        register(rules, PortType.LIST, PortType.XYZ, PortConversionRegistry::listVector);

        register(rules, PortType.STRING, PortType.PATH, (value, context) -> value);
        register(rules, PortType.STRING, PortType.RICH_TEXT,
                (value, context) -> RichTextValue.plain((String) value));
        register(rules, PortType.DICT, PortType.RICH_TEXT,
                (value, context) -> RichTextValue.from(value));
        register(rules, PortType.DICT, PortType.COLOR,
                (value, context) -> ColorValue.from(value));
        register(rules, PortType.DICT, PortType.ENTITY_TEMPLATE,
                (value, context) -> nonEmptyTemplate(value));
        register(rules, PortType.DICT, PortType.SLOT,
                (value, context) -> SlotRef.from(value));

        register(rules, PortType.STRING, PortType.BLOCK_STATE, PortConversionRegistry::parseBlockState);
        register(rules, PortType.STRING, PortType.ITEM, PortConversionRegistry::parseItem);
        register(rules, PortType.STRING, PortType.SLOT,
                (value, context) -> SlotRef.parse((String) value));

        register(rules, PortType.DICT, PortType.SHOP, (value, context) -> value);
        register(rules, PortType.SHOP, PortType.DICT, (value, context) -> value);

        for (PortType source : PortType.values()) {
            if (!source.isFlow() && source != PortType.ANY && source != PortType.STRING) {
                register(rules, source, PortType.STRING, PortConversionRegistry::formatString);
            }
        }

        EnumMap<PortType, Map<PortType, Converter>> frozen = new EnumMap<>(PortType.class);
        rules.forEach((source, targets) -> frozen.put(source, Map.copyOf(targets)));
        return Map.copyOf(frozen);
    }

    private static void registerNumericRules(EnumMap<PortType, Map<PortType, Converter>> rules) {
        PortType[] numeric = {PortType.INTEGER, PortType.LONG, PortType.FLOAT, PortType.BOOLEAN};
        for (PortType source : numeric) {
            for (PortType target : numeric) {
                if (source != target) {
                    register(rules, source, target,
                            (value, context) -> convertNumeric(value, target));
                }
            }
        }
        register(rules, PortType.STRING, PortType.INTEGER,
                (value, context) -> parseNumber((String) value, PortType.INTEGER));
        register(rules, PortType.STRING, PortType.LONG,
                (value, context) -> parseNumber((String) value, PortType.LONG));
        register(rules, PortType.STRING, PortType.FLOAT,
                (value, context) -> parseNumber((String) value, PortType.FLOAT));
        register(rules, PortType.STRING, PortType.BOOLEAN,
                (value, context) -> parseBoolean((String) value));
    }

    private static void register(EnumMap<PortType, Map<PortType, Converter>> rules,
                                 PortType source, PortType target, Converter converter) {
        Map<PortType, Converter> targets = rules.computeIfAbsent(
                source, ignored -> new EnumMap<>(PortType.class));
        Converter previous = targets.putIfAbsent(target, converter);
        if (previous != null) {
            throw new IllegalStateException("Duplicate port conversion rule: " + source + " -> " + target);
        }
    }

    @Nullable
    private static Object convertNumeric(Object value, PortType target) {
        if (value instanceof Boolean bool) {
            return switch (target) {
                case INTEGER -> bool ? 1 : 0;
                case LONG -> bool ? 1L : 0L;
                case FLOAT -> bool ? 1.0f : 0.0f;
                case BOOLEAN -> bool;
                default -> null;
            };
        }
        if (!(value instanceof Number number)) return null;
        return switch (target) {
            case INTEGER -> roundToInteger(number);
            case LONG -> number.longValue();
            case FLOAT -> number.floatValue();
            case BOOLEAN -> number.doubleValue() != 0.0d;
            default -> null;
        };
    }

    @Nullable
    private static Object parseNumber(String value, PortType target) {
        try {
            double number = Double.parseDouble(value);
            return switch (target) {
                case INTEGER -> roundToInteger(number);
                case LONG -> (long) number;
                case FLOAT -> (float) number;
                default -> null;
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private static Boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return null;
    }

    private static int roundToInteger(Number value) {
        long rounded = Math.round(value.doubleValue());
        if (rounded < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (rounded > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) rounded;
    }

    private static Vec3 scalarVector(Object value, @Nullable GraphDataContext context) {
        double component = ((Number) value).doubleValue();
        return new Vec3(component, component, component);
    }

    @Nullable
    private static Vec3 listVector(Object value, @Nullable GraphDataContext context) {
        if (!(value instanceof List<?> list) || list.size() < 3
                || !(list.get(0) instanceof Number x)
                || !(list.get(1) instanceof Number y)
                || !(list.get(2) instanceof Number z)) {
            return null;
        }
        return new Vec3(x.doubleValue(), y.doubleValue(), z.doubleValue());
    }

    @Nullable
    private static EntityTemplateValue nonEmptyTemplate(Object value) {
        EntityTemplateValue template = EntityTemplateValue.from(value);
        return template.isEmpty() ? null : template;
    }

    @Nullable
    private static BlockState parseBlockState(Object value, @Nullable GraphDataContext context) {
        Identifier id = Identifier.tryParse((String) value);
        if (id == null) return null;
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        return block != null ? block.defaultBlockState() : null;
    }

    @Nullable
    private static Item parseItem(Object value, @Nullable GraphDataContext context) {
        Identifier id = Identifier.tryParse((String) value);
        return id != null ? BuiltInRegistries.ITEM.getValue(id) : null;
    }

    private static String formatString(Object value, @Nullable GraphDataContext context) {
        return switch (value) {
            case RichTextValue richText -> richText.plain();
            case ColorValue color -> String.format(Locale.US, "rgba(%.3f, %.3f, %.3f, %.3f)",
                    color.r(), color.g(), color.b(), color.a());
            case SlotRef slotRef -> slotRef.serialize();
            case Entity entity -> entity.getStringUUID();
            case Vec3 vector -> String.format(Locale.US, "[%.2f, %.2f, %.2f]",
                    vector.x, vector.y, vector.z);
            case BlockPos pos -> String.format(Locale.US, "[%d, %d, %d]",
                    pos.getX(), pos.getY(), pos.getZ());
            case BlockState state -> BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            case ItemStack stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            case Item item -> BuiltInRegistries.ITEM.getKey(item).toString();
            default -> String.valueOf(value);
        };
    }

    @FunctionalInterface
    private interface Converter {
        @Nullable Object convert(Object value, @Nullable GraphDataContext context);
    }
}
