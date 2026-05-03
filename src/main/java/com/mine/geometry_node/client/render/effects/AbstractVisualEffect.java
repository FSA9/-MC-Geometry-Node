package com.mine.geometry_node.client.render.effects;

import com.mine.geometry_node.core.utils.ASTNode;
import com.mine.geometry_node.core.utils.ExpressionCompiler;
import com.mine.geometry_node.core.utils.VariableRegistry;
import com.mine.geometry_node.client.render.math.ClientPropertyFetcher;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractVisualEffect {
    protected final String effectType;
    protected final int color;
    protected int remainingTicks;
    protected int ageTicks = 0;

    protected final Map<String, ASTNode> compiledExpressions = new HashMap<>();

    // 渲染期的内存池与指令集
    protected final List<ClientPropertyFetcher.ParsedBinding> activeBindings = new ArrayList<>();
    protected final double[] varArray;
    protected final int tickIndex;

    public AbstractVisualEffect(PacketSpawnDynamicVisual packet) {
        this.effectType = packet.effectType();
        this.color = packet.color();
        this.remainingTicks = packet.durationTicks();

        // 1. 创建该特效专属的注册表
        VariableRegistry registry = new VariableRegistry();

        // 2. 编译公式并分配索引
        if (packet.expressions() != null) {
            packet.expressions().forEach((key, expr) -> {
                compiledExpressions.put(key, ExpressionCompiler.compile(expr, registry));
            });
        }

        // 3. 解析协议，绑定目标数组索引
        if (packet.bindings() != null) {
            packet.bindings().forEach((varKey, protocol) -> {
                int index = registry.registerOrGet(varKey);
                ClientPropertyFetcher.ParsedBinding parsed = ClientPropertyFetcher.parseProtocol(protocol, index);
                if (parsed != null) {
                    activeBindings.add(parsed);
                }
            });
            // 按 EntityId 排序，保证同实体的属性连续抓取，命中缓存
            activeBindings.sort(Comparator.comparingInt(ClientPropertyFetcher.ParsedBinding::entityId));
        }

        // 4. 提取 tick 的固定索引，并初始化极速数组
        this.tickIndex = registry.registerOrGet("tick");
        this.varArray = new double[registry.getVarCount()];
    }

    public boolean tick() {
        ageTicks++;
        remainingTicks--;
        return remainingTicks <= 0;
    }

    // 彻底取代 buildVariableTable，直接覆写数组 (Zero GC)
    protected void updateVariables(float partialTick) {
        // 写入 Tick
        if (tickIndex >= 0 && tickIndex < varArray.length) {
            varArray[tickIndex] = ageTicks + partialTick;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        // 实体缓存：避免同一帧对同一个 entityId 执行多次 level.getEntity
        Entity cachedEntity = null;
        int cachedEntityId = -1;

        for (int i = 0; i < activeBindings.size(); i++) {
            ClientPropertyFetcher.ParsedBinding binding = activeBindings.get(i);

            if (binding.entityId() != cachedEntityId) {
                cachedEntity = level.getEntity(binding.entityId());
                cachedEntityId = binding.entityId();
            }

            varArray[binding.index()] = ClientPropertyFetcher.fetchFast(binding, cachedEntity, partialTick);
        }
    }

    // 估算：不再传入 Map，直接传数组
    protected double eval(String key, double defaultValue) {
        ASTNode node = compiledExpressions.get(key);
        return node != null ? node.evaluate(this.varArray) : defaultValue;
    }

    public abstract void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 camPos, float partialTick);
}