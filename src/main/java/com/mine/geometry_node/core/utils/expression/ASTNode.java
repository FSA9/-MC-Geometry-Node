package com.mine.geometry_node.core.utils.expression;

import java.util.Map;

public interface ASTNode {
    // 【核心修改】：传入单纯的 double 数组，彻底消灭 String 查找与拆箱
    double evaluate(double[] vars);

    ASTNode substitute(Map<String, ASTNode> substitutions);

    String toFormulaString();
}