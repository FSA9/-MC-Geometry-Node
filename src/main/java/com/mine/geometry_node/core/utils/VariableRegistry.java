package com.mine.geometry_node.core.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * 变量注册表：在 AST 编译期，为所有变量名分配递增的 int 索引。
 */
public class VariableRegistry {
    private final Map<String, Integer> nameToIndex = new HashMap<>();
    private int nextIndex = 0;

    /**
     * 注册或获取变量的索引
     */
    public int registerOrGet(String name) {
        // 如果名字已经存在，返回旧索引；如果是新变量，分配新索引并自增
        return nameToIndex.computeIfAbsent(name, k -> nextIndex++);
    }

    /**
     * 获取当前公式一共使用了多少个独特的变量（决定了后面 double[] 的长度）
     */
    public int getVarCount() {
        return nextIndex;
    }

    public Map<String, Integer> getMapping() {
        return nameToIndex;
    }
}