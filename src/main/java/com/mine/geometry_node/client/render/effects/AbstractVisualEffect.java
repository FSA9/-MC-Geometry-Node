package com.mine.geometry_node.client.render.effects;

import com.mine.geometry_node.core.utils.ASTNode;
import com.mine.geometry_node.core.utils.ExpressionCompiler;
import com.mine.geometry_node.client.render.math.ClientPropertyFetcher;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractVisualEffect {
    // 基础属性
    protected final String effectType;
    protected final int color;
    protected int remainingTicks;
    protected int ageTicks = 0;

    // 编译后的 AST 树与绑定协议
    protected final Map<String, ASTNode> compiledExpressions = new HashMap<>();
    protected final Map<String, ClientPropertyFetcher.ParsedBinding> parsedBindings = new HashMap<>();

    public AbstractVisualEffect(PacketSpawnDynamicVisual packet) {
        this.effectType = packet.effectType();
        this.color = packet.color();
        this.remainingTicks = packet.durationTicks();

        if (packet.bindings() != null) {
            packet.bindings().forEach((key, protocol) -> {
                ClientPropertyFetcher.ParsedBinding parsed = ClientPropertyFetcher.parseProtocol(protocol);
                if (parsed != null) {
                    parsedBindings.put(key, parsed);
                }
            });
        }

        if (packet.expressions() != null) {
            packet.expressions().forEach((key, expr) -> {
                compiledExpressions.put(key, ExpressionCompiler.compile(expr));
            });
        }
    }

    public boolean tick() {
        ageTicks++;
        remainingTicks--;
        return remainingTicks <= 0;
    }

    protected Map<String, Double> buildVariableTable(float partialTick) {
        Map<String, Double> vars = new HashMap<>();
        double currentTick = ageTicks + partialTick;
        vars.put("tick", currentTick);
        vars.put("TICK", currentTick);

        parsedBindings.forEach((varKey, binding) -> {
            double realTimeValue = ClientPropertyFetcher.fetchFast(binding, partialTick);
            vars.put(varKey, realTimeValue);
        });

        return vars;
    }

    protected double eval(String key, Map<String, Double> vars, double defaultValue) {
        ASTNode node = compiledExpressions.get(key);
        return node != null ? node.evaluate(vars) : defaultValue;
    }

    public abstract void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 camPos, float partialTick);
}