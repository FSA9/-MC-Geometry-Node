package com.mine.geometry_node.client.render.effects;

import com.mine.geometry_node.core.utils.ASTNode;
import com.mine.geometry_node.core.utils.ExpressionCompiler;
import com.mine.geometry_node.client.render.math.ClientPropertyFetcher;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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

    // 锚点与基础数据
    protected final int sourceEntityId;
    protected final Vec3 baseStart;
    protected final int targetEntityId;
    protected final Vec3 baseEnd;
    protected final float baseSize;

    // 编译后的 AST 树与绑定协议
    protected final Map<String, ASTNode> compiledExpressions = new HashMap<>();
    protected final Map<String, String> bindings; // <--- 新增：保存服务端的绑定协议

    protected final Map<String, ClientPropertyFetcher.ParsedBinding> parsedBindings = new HashMap<>();

    public AbstractVisualEffect(PacketSpawnDynamicVisual packet) {
        this.effectType = packet.effectType();
        this.sourceEntityId = packet.sourceEntityId();
        this.baseStart = packet.baseStartPos();
        this.targetEntityId = packet.targetEntityId();
        this.baseEnd = packet.baseEndPos();
        this.color = packet.color();
        this.baseSize = packet.baseSize();
        this.remainingTicks = packet.durationTicks();
        this.bindings = packet.bindings(); // <--- 接收绑定协议字典

        // 【新增】：在初始化时，将所有文本协议预编译为操作对象
        if (this.bindings != null) {
            this.bindings.forEach((key, protocol) -> {
                ClientPropertyFetcher.ParsedBinding parsed = ClientPropertyFetcher.parseProtocol(protocol);
                if (parsed != null) {
                    parsedBindings.put(key, parsed);
                }
            });
        }

        // 仅在初始化时编译一次 AST 树
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

    /**
     * 【核心优化】每一帧渲染时组装变量上下文
     */
    protected Map<String, Double> buildVariableTable(float partialTick) {
        Map<String, Double> vars = new HashMap<>();

        // 1. 注入平滑时间
        double currentTick = ageTicks + partialTick;
        vars.put("tick", currentTick);
        vars.put("TICK", currentTick);

        // 2. 核心修改：现在每帧只遍历预编译好的对象，不再进行 String 切割！
        parsedBindings.forEach((varKey, binding) -> {
            double realTimeValue = ClientPropertyFetcher.fetchFast(binding, partialTick);
            vars.put(varKey, realTimeValue);
        });

        return vars;
    }

    /**
     * 计算某一个属性在当前帧的动态值
     */
    protected double eval(String key, Map<String, Double> vars, double defaultValue) {
        ASTNode node = compiledExpressions.get(key);
        return node != null ? node.evaluate(vars) : defaultValue;
    }

    /**
     * 获取当前帧平滑的起点和终点坐标 (配合部分刻进行插值)
     */
    protected DynamicAnchors getDynamicAnchors(float partialTick) {
        Map<String, Double> vars = buildVariableTable(partialTick);
        ClientLevel level = Minecraft.getInstance().level;

        // 1. 获取基础物理坐标
        Vec3 start = (level != null && sourceEntityId != -1) ?
                level.getEntity(sourceEntityId).getPosition(partialTick).add(baseStart) : baseStart;
        Vec3 end = (level != null && targetEntityId != -1) ?
                level.getEntity(targetEntityId).getPosition(partialTick).add(baseEnd) : baseEnd;

        // 2. 动态公式覆盖逻辑 (利用 NaN 判断是否有公式)
        // 如果没连线，eval 会返回 NaN，则使用基础坐标；如果连了线，直接覆盖原坐标。
        double dynStartX = eval("startX", vars, Double.NaN);
        double dynStartY = eval("startY", vars, Double.NaN);
        double dynStartZ = eval("startZ", vars, Double.NaN);
        start = new Vec3(
                Double.isNaN(dynStartX) ? start.x : dynStartX,
                Double.isNaN(dynStartY) ? start.y : dynStartY,
                Double.isNaN(dynStartZ) ? start.z : dynStartZ
        );

        double dynEndX = eval("endX", vars, Double.NaN);
        double dynEndY = eval("endY", vars, Double.NaN);
        double dynEndZ = eval("endZ", vars, Double.NaN);
        end = new Vec3(
                Double.isNaN(dynEndX) ? end.x : dynEndX,
                Double.isNaN(dynEndY) ? end.y : dynEndY,
                Double.isNaN(dynEndZ) ? end.z : dynEndZ
        );

        // 3. 计算动态尺寸
        float size = (float) eval("size", vars, baseSize);

        return new DynamicAnchors(start, end, Math.max(0.01f, size));
    }

    public abstract void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 camPos, float partialTick);

    protected record DynamicAnchors(Vec3 start, Vec3 end, float size) {}
}