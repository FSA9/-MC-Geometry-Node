package com.mine.geometry_node.core.utils;

import java.util.Map;

public class ASTNodes {

    public record ConstantNode(double value) implements ASTNode {
        @Override
        public double evaluate(Map<String, Double> vars) { return value; }

        @Override
        public ASTNode substitute(Map<String, ASTNode> substitutions) { return this; }

        @Override
        public String toFormulaString() {
            // 去掉多余的小数点（如 1.0 -> 1）
            if (value == (long) value) return String.valueOf((long) value);
            return String.valueOf(value);
        }
    }

    public record VariableNode(String name) implements ASTNode {
        @Override
        public double evaluate(Map<String, Double> vars) {
            return vars.getOrDefault(name, 0.0);
        }

        @Override
        public ASTNode substitute(Map<String, ASTNode> substitutions) {
            // 如果命中替换表，返回嫁接的子树；否则返回自己
            return substitutions.getOrDefault(name, this);
        }

        @Override
        public String toFormulaString() { return name; }
    }

    public record BinaryNode(char operator, ASTNode left, ASTNode right) implements ASTNode {
        @Override
        public double evaluate(Map<String, Double> vars) {
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
            // 递归构建字符串，强制加括号保证优先级绝对正确
            return "(" + left.toFormulaString() + operator + right.toFormulaString() + ")";
        }
    }

    public record UnaryNode(char operator, ASTNode child) implements ASTNode {
        @Override
        public double evaluate(Map<String, Double> vars) {
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
        public double evaluate(Map<String, Double> vars) {
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