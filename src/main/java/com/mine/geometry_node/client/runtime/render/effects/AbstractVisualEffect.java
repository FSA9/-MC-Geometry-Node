package com.mine.geometry_node.client.runtime.render.effects;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.runtime.render.math.ClientPropertyFetcher;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionEvaluationContext;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionSpec;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.graph.expression.LiveValues;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public abstract class AbstractVisualEffect {
    protected final String effectType;
    protected final int color;
    protected int remainingTicks;
    protected int ageTicks;

    private final Map<String, ExpressionData> expressions;
    private final ClientPropertyFetcher.Resolver bindingResolver = new ClientPropertyFetcher.Resolver();

    protected AbstractVisualEffect(PacketSpawnDynamicVisual packet) {
        this.effectType = packet.effectType();
        this.color = packet.color();
        this.remainingTicks = packet.durationTicks();
        this.expressions = packet.expressions();
    }

    public boolean tick() {
        ageTicks++;
        remainingTicks--;
        return remainingTicks <= 0;
    }

    protected final LiveValue.State<Float> captureFloat(PortDef port, String key, float snapshot) {
        ExpressionData expression = expressions.get(key);
        ExpressionSpec spec = ExpressionSpec.fromScalar(expression);
        LiveValue<Float> value = LiveValues.captureFloat(port, snapshot, spec);
        reportDiagnostics(key, value);
        return value.newState();
    }

    protected final LiveValue.State<Vec3> captureXyz(PortDef port, String key, Vec3 snapshot) {
        ExpressionData expression = expressions.get(key);
        ExpressionSpec x = componentSpec(expression, 0);
        ExpressionSpec y = componentSpec(expression, 1);
        ExpressionSpec z = componentSpec(expression, 2);
        LiveValue<Vec3> value = LiveValues.captureXyz(port, snapshot, x, y, z);
        reportDiagnostics(key, value);
        return value.newState();
    }

    protected final ExpressionEvaluationContext expressionContext(float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        bindingResolver.begin(level, partialTick);
        double worldGameTime = level != null ? level.getGameTime() + partialTick : partialTick;
        return new ExpressionEvaluationContext(worldGameTime, ageTicks + partialTick, bindingResolver);
    }

    private static ExpressionSpec componentSpec(ExpressionData expression, int component) {
        return ExpressionSpec.fromComponent(expression, component);
    }

    private void reportDiagnostics(String key, LiveValue<?> value) {
        for (String diagnostic : value.diagnostics()) {
            GeometryNode.LOGGER.warn("Invalid live expression for visual '{}' property '{}': {}",
                    effectType, key, diagnostic);
        }
    }

    public abstract void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                SubmitNodeCollector submitNodeCollector, Vec3 camPos, float partialTick);
}
