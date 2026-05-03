package com.mine.geometry_node.core.utils;

import java.util.Map;

public class ASTNodes {

    public record ConstantNode(double value) implements ASTNode {
        @Override
        public double evaluate(double[] vars) { return value; } // 修改参数

        @Override
        public ASTNode substitute(Map<String, ASTNode> substitutions) { return this; }

        @Override
        public String toFormulaString() {
            if (value == (long) value) return String.valueOf((long) value);
            return String.valueOf(value);
        }
    }

    // 【核心修改】：VariableNode 增加 index 字段，并根据 index 极速读取
    public record VariableNode(int index, String originalName) implements ASTNode {
        @Override
        public double evaluate(double[] vars) {
            // 数组越界保护，安全极速读取
            return (index >= 0 && index < vars.length) ? vars[index] : 0.0;
        }

        @Override
        public ASTNode substitute(Map<String, ASTNode> substitutions) {
            // 替换逻辑依然需要用到原始名称，所以保留了 originalName 字段
            return substitutions.getOrDefault(originalName, this);
        }

        @Override
        public String toFormulaString() { return originalName; }
    }

    public record BinaryNode(char operator, ASTNode left, ASTNode right) implements ASTNode {
        @Override
        public double evaluate(double[] vars) { // 修改参数
            double l = left.evaluate(vars);
            double r = right.evaluate(vars);
            return switch (operator) {
                case '+' -> l + r;
                case '-' -> l - r;
                case '*' -> l * r;
                case '/' -> r == 0 ? 0 : l / r;
                case '^' -> Math.pow(l, r);
                default -> 0;
            };
        }

        @Override
        public ASTNode substitute(Map<String, ASTNode> substitutions) {
            return new BinaryNode(operator, left.substitute(substitutions), right.substitute(substitutions));
        }

        @Override
        public String toFormulaString() {
            return "(" + left.toFormulaString() + operator + right.toFormulaString() + ")";
        }
    }

    public record UnaryNode(char operator, ASTNode child) implements ASTNode {
        @Override
        public double evaluate(double[] vars) { // 修改参数
            double v = child.evaluate(vars);
            return operator == '-' ? -v : v;
        }

        @Override
        public ASTNode substitute(Map<String, ASTNode> substitutions) {
            return new UnaryNode(operator, child.substitute(substitutions));
        }

        @Override
        public String toFormulaString() {
            return operator + "(" + child.toFormulaString() + ")";
        }
    }

    public record FunctionNode(String funcName, ASTNode child) implements ASTNode {
        @Override
        public double evaluate(double[] vars) { // 修改参数
            double v = child.evaluate(vars);
            return switch (funcName) {
                case "sin" -> Math.sin(v);
                case "cos" -> Math.cos(v);
                case "tan" -> Math.tan(v);
                case "sqrt" -> Math.sqrt(v);
                case "abs" -> Math.abs(v);
                default -> 0;
            };
        }

        @Override
        public ASTNode substitute(Map<String, ASTNode> substitutions) {
            return new FunctionNode(funcName, child.substitute(substitutions));
        }

        @Override
        public String toFormulaString() {
            return funcName + "(" + child.toFormulaString() + ")";
        }
    }
}