package com.mine.geometry_node.client.runtime.render.math;

import net.minecraft.world.entity.Entity;

public class ClientPropertyFetcher {
    public record ParsedBinding(int index, int entityId, String property, double fallback) {}

    public static ParsedBinding parseProtocol(String bindingProtocol, int targetIndex) {
        if (bindingProtocol == null || bindingProtocol.isEmpty()) return null;

        String[] parts = bindingProtocol.split(":");
        if (parts.length < 3 || !"entity".equals(parts[0])) return null;

        try {
            int entityId = Integer.parseInt(parts[1]);
            String property = parts[2];
            double fallback = parts.length >= 4 ? Double.parseDouble(parts[3]) : 0.0;
            return new ParsedBinding(targetIndex, entityId, property, fallback);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 剥离实体查询逻辑，Entity 从外部传入
    public static double fetchFast(ParsedBinding binding, Entity entity, float partialTick) {
        if (entity == null) return binding.fallback;

        return switch (binding.property) {
            case "velocity" -> entity.getDeltaMovement().length();
            case "velocity_x" -> entity.getDeltaMovement().x;
            case "velocity_y" -> entity.getDeltaMovement().y;
            case "velocity_z" -> entity.getDeltaMovement().z;
            case "pos_x" -> entity.getPosition(partialTick).x;
            case "pos_y" -> entity.getPosition(partialTick).y;
            case "pos_z" -> entity.getPosition(partialTick).z;
            case "rotation_x" -> entity.getXRot();
            case "rotation_y" -> entity.getYRot();
            case "rotation_z" -> 0.0;
            case "pitch" -> entity.getXRot();
            case "yaw" -> entity.getYRot();
            case "yaw_head" -> entity.getYHeadRot();
            default -> binding.fallback;
        };
    }
}