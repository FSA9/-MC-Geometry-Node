package com.mine.geometry_node.core.node.port;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.value.color.ColorValue;
import com.mine.geometry_node.core.node.value.dynamic.DynamicData;
import com.mine.geometry_node.core.node.value.dynamic.ExpressionData;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mine.geometry_node.core.node.value.SlotRef;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
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

import java.util.List;
import java.util.UUID;

/**
 * [核心基建] 类型转换中心
 * 负责将任意来源的原始数据 (Object) 尽最大可能转换为节点期望的目标类型 (Class<T>)。
 */
public class TypeConverter {

    /**
     * 核心转换方法。
     * @param val  原始数据
     * @param type 期望的目标类型
     * @param ctx  执行上下文 (用于解析 UUID 到实体等需要 Level 的操作)
     * @return 转换后的对象，如果完全无法转换则返回 null
     */
    @Nullable
    public static <T> T convert(@Nullable Object val, Class<T> type, GraphDataContext ctx) {
        if (val == null) return null;

        // ==========================================
        // 双模数字与公式协议的智能拆解
        // ==========================================
        if (val instanceof DynamicData dyn) {
            // 目标索要公式，直接交出活公式
            if (type == ExpressionData.class) {
                return type.cast(dyn.expression());
            }
            // 目标索要普通数值，剥离包装，让里面的浮点数继续往下走常规转换
            val = dyn.value();
        } else if (type == ExpressionData.class && val instanceof Number num) {
            return type.cast(new ExpressionData(String.valueOf(num.floatValue()), java.util.Map.of()));
        }

        if (type == ColorValue.class) {
            ColorValue color = ColorValue.from(val);
            if (color != null) {
                return type.cast(color);
            }
        }

        if (type == EntityTemplateValue.class) {
            EntityTemplateValue template = EntityTemplateValue.from(val);
            return template.isEmpty() ? null : type.cast(template);
        }

        // 1. 完美匹配：本身就是目标类型或其子类
        if (type.isInstance(val)) {
            return type.cast(val);
        }

        if (type == Entity.class && val instanceof EntityTemplateValue template) {
            if (ctx == null || ctx.getLevel() == null || template.isEmpty()) return null;
            return type.cast(template.create(ctx.getLevel(), Vec3.ZERO));
        }

        if (type == Entity.class && val instanceof UUID uuid) {
            return type.cast(resolveEntity(uuid, ctx));
        }

        // 2. 数值体系的隐式互转
        if (val instanceof Number n) {
            if (type == Integer.class) return type.cast(n.intValue());
            if (type == Long.class) return type.cast(n.longValue());
            if (type == Float.class) return type.cast(n.floatValue());
            if (type == Double.class) return type.cast(n.doubleValue());
            if (type == Boolean.class) return type.cast(n.floatValue() > 0);
            if (type == Vec3.class) {
                double component = n.doubleValue();
                return type.cast(new Vec3(component, component, component));
            }
        }

        if (val instanceof ColorValue color) {
            if (type == Integer.class) return type.cast(color.toArgb());
        }

        // 3. 布尔转数值
        if (val instanceof Boolean b) {
            if (type == Integer.class) return type.cast(b ? 1 : 0);
            if (type == Long.class) return type.cast(b ? 1L : 0L);
            if (type == Float.class) return type.cast(b ? 1.0f : 0.0f);
            if (type == Double.class) return type.cast(b ? 1.0 : 0.0);
        }

        // 4. 富文本值模型
        if (type == RichTextValue.class) {
            return type.cast(RichTextValue.from(val));
        }

        // 5. 万物皆可转 String (序列化)
        if (type == String.class) {
            return switch (val) {
                case RichTextValue richText -> type.cast(richText.plain());
                case ColorValue color -> type.cast(String.format(java.util.Locale.US, "rgba(%.3f, %.3f, %.3f, %.3f)", color.r(), color.g(), color.b(), color.a()));
                case SlotRef slotRef -> type.cast(slotRef.serialize());
                case Entity e -> type.cast(e.getStringUUID());
                case GeometryValue geometry -> type.cast(geometry.toString());
                case Vec3 v -> type.cast(String.format(java.util.Locale.US, "[%.2f, %.2f, %.2f]", v.x, v.y, v.z));
                case BlockPos p ->
                        type.cast(String.format(java.util.Locale.US, "[%d, %d, %d]", p.getX(), p.getY(), p.getZ()));

                // 方块/物品转 Registry ID (例如 "minecraft:stone")
                case BlockState bs -> type.cast(BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString());
                case ItemStack is -> type.cast(BuiltInRegistries.ITEM.getKey(is.getItem()).toString());
                case Item item -> type.cast(BuiltInRegistries.ITEM.getKey(item).toString());
                default -> type.cast(String.valueOf(val));
            };

        }

        // 6. 特殊聚合转对象 (List -> Vec3)
        if (type == Vec3.class && val instanceof List<?> list) {
            if (list.size() >= 3 && list.get(0) instanceof Number n1 && list.get(1) instanceof Number n2 && list.get(2) instanceof Number n3) {
                return type.cast(new Vec3(n1.doubleValue(), n2.doubleValue(), n3.doubleValue()));
            }
        }

        if (type == Vec3.class && val instanceof BlockPos pos) {
            return type.cast(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        }

        // 7. 字符串反向解析 (反序列化)
        if (val instanceof String s) {

            // 解析数值 (Integer, Long, Float, Double)
            if (type == Integer.class || type == Long.class || type == Float.class || type == Double.class) {
                try {
                    // 先统一解析为 Double 以兼容 "1.5" 这种格式，然后再向下转型
                    double d = Double.parseDouble(s);
                    if (type == Integer.class) return type.cast((int) d);
                    if (type == Long.class) return type.cast((long) d);
                    if (type == Float.class) return type.cast((float) d);
                    return type.cast(d);
                } catch (NumberFormatException ignored) {}
            }

            // 解析 Entity (UUID)
            if (type == Entity.class) {
                try {
                    UUID uuid = UUID.fromString(s);
                    return type.cast(resolveEntity(uuid, ctx));
                } catch (Exception ignored) {}
            }

            // 解析 BlockState
            if (type == BlockState.class) {
                try {
                    Identifier resLoc = Identifier.tryParse(s);
                    if (resLoc != null) {
                        Block block = BuiltInRegistries.BLOCK.getValue(resLoc);
                        return type.cast(block.defaultBlockState());
                    }
                } catch (Exception ignored) {}
            }

            // 解析 SlotRef
            if (type == SlotRef.class) {
                return type.cast(SlotRef.parse(s));
            }

            // 解析 Item
            if (type == Item.class) {
                try {
                    Identifier resLoc = Identifier.tryParse(s);
                    if (resLoc != null) {
                        return type.cast(BuiltInRegistries.ITEM.getValue(resLoc));
                    }
                } catch (Exception ignored) {}
            }

            // 解析 Boolean
            if (type == Boolean.class) {
                if ("true".equalsIgnoreCase(s)) return type.cast(true);
                if ("false".equalsIgnoreCase(s)) return type.cast(false);
            }
        }

        if (type == SlotRef.class) {
            SlotRef slotRef = SlotRef.from(val);
            if (slotRef != null) {
                return type.cast(slotRef);
            }
        }

        if (type == ExpressionData.class && (val instanceof List || val instanceof Vec3)) {
            return null;
        }

        System.err.println("[TypeConverter] Failed to convert " + val.getClass().getSimpleName() + " to " + type.getSimpleName());
        return null;
    }

    @Nullable
    private static Entity resolveEntity(UUID uuid, @Nullable GraphDataContext ctx) {
        if (ctx == null || ctx.getLevel() == null) return null;
        Entity entity = ctx.getLevel().getEntity(uuid);
        if (entity != null) return entity;
        for (var level : ctx.getLevel().getServer().getAllLevels()) {
            if (level == ctx.getLevel()) continue;
            entity = level.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }
}
