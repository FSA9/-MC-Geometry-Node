package com.mine.geometry_node.core.execution;

import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [执行结果协议] 定义节点执行后的控制流向。
 */
public sealed interface ExecutionResult {

    record Next(String outputPortName) implements ExecutionResult {}
    record Wait(long ticks, String nextPortName) implements ExecutionResult {}
    record Finish() implements ExecutionResult {}
    record Error(String errorMessage) implements ExecutionResult {}
    record Call(List<String> outputPorts) implements ExecutionResult {}

    // ==========================================
    // 全局缓存池 (Zero-Allocation)
    // ==========================================

    Finish FINISH_INSTANCE = new Finish();

    Map<String, Next> CACHED_NEXTS = buildCache();

    private static Map<String, Next> buildCache() {
        Map<String, Next> map = new HashMap<>();
        for (StandardPorts port : StandardPorts.values()) {
            if (port.getType() == PortType.EXECUTION) {
                map.put(port.getId(), new Next(port.getId()));
            }
        }
        return Map.copyOf(map);
    }

    // --- 静态工厂方法 (Syntactic Sugar) ---

    static ExecutionResult next(String port) {
        Next cached = CACHED_NEXTS.get(port);
        if (cached != null) {
            return cached;
        }
        return new Next(port);
    }

    static ExecutionResult finish() {
        return FINISH_INSTANCE;
    }

    static ExecutionResult delay(long ticks, String nextPort) { return new Wait(ticks, nextPort); }
    static ExecutionResult error(String msg) { return new Error(msg); }
    static ExecutionResult call(List<String> ports) { return new Call(ports); }
    static ExecutionResult call(String... ports) { return new Call(List.of(ports)); }
}