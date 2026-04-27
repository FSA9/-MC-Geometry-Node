package com.mine.geometry_node.core.utils;

import java.util.Map;

/**
 * 抽象语法树基础节点
 */
public interface ASTNode {
    // 渲染时每帧调用，传入当前的动态变量表 (如 T, X, Y, Z, V)
    double evaluate(Map<String, Double> vars);
}