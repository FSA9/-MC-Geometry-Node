package com.mine.geometry_node.client.render.math;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

public class ClientPropertyFetcher {

    // 1. 新增：预编译协议载体
    public record ParsedBinding(int entityId, String property, double fallback) {}

    // 2. 新增：只在初始化时调用【一次】的解析器
    public static ParsedBinding parseProtocol(String bindingProtocol) {
        if (bindingProtocol == null || bindingProtocol.isEmpty()) return null;

        String[] parts = bindingProtocol.split(":");
        if (parts.length < 3 || !"entity".equals(parts[0])) return null;

        try {
            int entityId = Integer.parseInt(parts[1]);
            String property = parts[2];
            double fallback = parts.length >= 4 ? Double.parseDouble(parts[3]) : 0.0;
            return new ParsedBinding(entityId, property, fallback);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 3. 核心改进：高频渲染帧专用的极速抓取器（彻底消灭 String.split）
    public static double fetchFast(ParsedBinding binding, float partialTick) {
        if (binding == null) return 0.0;

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return binding.fallback;

        Entity entity = level.getEntity(binding.entityId);
        if (entity == null) return binding.fallback;

        return switch (binding.property) {
            case "velocity" -> entity.getDeltaMovement().length();
            case "velocity_x" -> entity.getDeltaMovement().x;
            case "velocity_y" -> entity.getDeltaMovement().y;
            case "velocity_z" -> entity.getDeltaMovement().z;
            case "pos_x" -> entity.getPosition(partialTick).x;
            case "pos_y" -> entity.getPosition(partialTick).y;
            case "pos_z" -> entity.getPosition(partialTick).z;
            case "pitch" -> entity.getXRot();
            case "yaw" -> entity.getYRot();
            case "yaw_head" -> entity.getYHeadRot();
            default -> binding.fallback;
        };
    }
}