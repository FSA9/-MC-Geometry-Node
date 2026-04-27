package com.mine.geometry_node.core.node.nodes.actions.world;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SpawnParticle extends BaseNode {

    public static final String TYPE_ID = "spawn_particle";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.spawn_particle"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 粒子类型 (下拉框)
                .addRow(new PortRow(StandardPorts.PARTICLE.toInput("minecraft:flame"), null, UIHint.SELECT, null, null))
                // 坐标输入 (允许单点或连入 List)
                .addRow(new PortRow(StandardPorts.XYZ.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                // 数量
                .addRow(new PortRow(StandardPorts.COUNT.toInput(10), null, UIHint.INPUT, null, null))
                // 扩散范围 (dx, dy, dz)
                .addRow(new PortRow(StandardPorts.SPREAD.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                // 运动速度 (扩散动量倍率)
                .addRow(new PortRow(StandardPorts.SPEED.toInput(0.1f), null, UIHint.SLIDER, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ServerLevel level = context.getLevel();
        if (level == null) return next(StandardPorts.FLOW_OUT.getId());

        String particleId = getInput(context, StandardPorts.PARTICLE.getId(), String.class);
        // 核心：直接使用 getInputList，无论上游连的是单个 Vec3 还是 List<Vec3>，统统转为 List
        List<Vec3> positions = getInputList(context, StandardPorts.XYZ.getId(), Vec3.class);

        Integer count = getInput(context, StandardPorts.COUNT.getId(), Integer.class);
        Vec3 spread = getInput(context, StandardPorts.SPREAD.getId(), Vec3.class);
        Float speed = getInput(context, StandardPorts.SPEED.getId(), Float.class);

        if (particleId == null || positions.isEmpty() || count == null || spread == null || speed == null) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse(particleId));
        if (!(type instanceof ParticleOptions particleOptions)) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        // 统一遍历分发
        for (Vec3 pos : positions) {
            level.sendParticles(
                    particleOptions,
                    pos.x, pos.y, pos.z,
                    count,
                    spread.x, spread.y, spread.z,
                    speed
            );
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}