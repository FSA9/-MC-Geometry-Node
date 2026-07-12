package com.mine.geometry_node.core.engine.blueprint.attachment;

import com.mine.geometry_node.GeometryNode;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * [实体附件] 伤害免疫数据载体
 */
public class EntityImmunityAttachment {

    private final Set<String> immunities = new HashSet<>();

    // ==========================================
    // 实例方法 (数据操作)
    // ==========================================

    public void grant(String damageTypeId) {
        String key = normalizeDamageTypeId(damageTypeId);
        if (!key.isEmpty()) {
            immunities.add(key);
        }
    }

    public void revoke(String damageTypeId) {
        String key = normalizeDamageTypeId(damageTypeId);
        if (!key.isEmpty()) {
            immunities.remove(key);
        }
    }

    public boolean has(String damageTypeId) {
        String key = normalizeDamageTypeId(damageTypeId);
        return !key.isEmpty() && immunities.contains(key);
    }

    // ==========================================
    // 序列化与反序列化 (存档支持)
    // ==========================================

    public ListTag save(HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (String type : immunities) {
            list.add(StringTag.valueOf(type));
        }
        return list;
    }

    public void load(ListTag tag, HolderLookup.Provider provider) {
        immunities.clear();
        for (int i = 0; i < tag.size(); i++) {
            String key = normalizeDamageTypeId(tag.getStringOr(i, ""));
            if (!key.isEmpty()) {
                immunities.add(key);
            }
        }
    }

    // ==========================================
    // 静态便捷接口 (供节点和事件调用)
    // ==========================================

    public static void grantImmunity(Entity entity, String damageTypeId) {
        if (entity != null) {
            entity.getData(GeometryNode.IMMUNITY_ATTACHMENT).grant(damageTypeId);
        }
    }

    public static void revokeImmunity(Entity entity, String damageTypeId) {
        if (entity != null && entity.hasData(GeometryNode.IMMUNITY_ATTACHMENT)) {
            entity.getData(GeometryNode.IMMUNITY_ATTACHMENT).revoke(damageTypeId);
        }
    }

    public static boolean hasImmunity(Entity entity, String damageTypeId) {
        if (entity != null && entity.hasData(GeometryNode.IMMUNITY_ATTACHMENT)) {
            return entity.getData(GeometryNode.IMMUNITY_ATTACHMENT).has(damageTypeId);
        }
        return false;
    }

    public static String damageTypeId(DamageSource source) {
        if (source == null) {
            return "";
        }
        return source.typeHolder()
                .unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("");
    }

    private static String normalizeDamageTypeId(String damageTypeId) {
        if (damageTypeId == null) {
            return "";
        }

        String key = damageTypeId.trim().toLowerCase(Locale.ROOT);
        return key.contains(":") ? key : "";
    }
}
