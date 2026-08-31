package com.mine.geometry_node.core.utils.expression;

public class ExpressionCompiler {

    private ExpressionCompiler() {
    }

    /**
     * 传入变量注册表进行编译，自动为变量分配数组索引
     */
    public static ASTNode compile(final String str, VariableRegistry registry) {
        if (str == null || str.trim().isEmpty()) {
            return new ASTNodes.ConstantNode(0);
        }
        try {
            return compileStrict(str, registry);
        } catch (ExpressionSyntaxException exception) {
            System.err.println("[ExpressionCompiler] " + exception.getMessage());
            return new ASTNodes.ConstantNode(0);
        }
    }

    /**
     * Compiles a formula and reports malformed input to callers that need a safe fallback.
     */
    public static ASTNode compileStrict(final String str, VariableRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("Variable registry cannot be null");
        }
        if (str == null || str.trim().isEmpty()) {
            throw new ExpressionSyntaxException("Expression formula is empty");
        }

        return new Parser(str, registry).parse();
    }

    public static final class ExpressionSyntaxException extends IllegalArgumentException {
        public ExpressionSyntaxException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String source;
        private final VariableRegistry registry;
        int pos = -1, ch;

        private Parser(String source, VariableRegistry registry) {
            this.source = source;
            this.registry = registry;
        }

        void nextChar() {
            ch = (++pos < source.length()) ? source.charAt(pos) : -1;
        }

        boolean eat(int charToEat) {
            while (Character.isWhitespace(ch)) nextChar();
            if (ch == charToEat) {
                nextChar();
                return true;
            }
            return false;
        }

        ASTNode parse() {
            nextChar();
            ASTNode node = parseExpression();
            while (Character.isWhitespace(ch)) nextChar();
            if (ch != -1) {
                throw syntax("Unexpected character '" + (char) ch + "'");
            }
            return node;
        }

        ASTNode parseExpression() {
            ASTNode node = parseTerm();
            for (;;) {
                if      (eat('+')) node = new ASTNodes.BinaryNode('+', node, parseTerm());
                else if (eat('-')) node = new ASTNodes.BinaryNode('-', node, parseTerm());
                else return node;
            }
        }

        ASTNode parseTerm() {
            ASTNode node = parseFactor();
            for (;;) {
                if      (eat('*')) node = new ASTNodes.BinaryNode('*', node, parseFactor());
                else if (eat('/')) node = new ASTNodes.BinaryNode('/', node, parseFactor());
                else return node;
            }
        }

        ASTNode parseFactor() {
            if (eat('+')) return new ASTNodes.UnaryNode('+', parseFactor());
            if (eat('-')) return new ASTNodes.UnaryNode('-', parseFactor());

            ASTNode node = null;
            int startPos = this.pos;
            if (eat('(')) {
                node = parseExpression();
                if (!eat(')')) {
                    throw syntax("Missing closing parenthesis");
                }
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                try {
                    double value = Double.parseDouble(source.substring(startPos, this.pos));
                    node = new ASTNodes.ConstantNode(value);
                } catch (NumberFormatException exception) {
                    throw syntax("Malformed number");
                }
            } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                while ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_') {
                    nextChar();
                }
                String name = source.substring(startPos, this.pos);

                // 数学常数直接折叠为常量节点。
                if (name.equalsIgnoreCase("pi")) {
                    node = new ASTNodes.ConstantNode(Math.PI);
                } else if (name.equalsIgnoreCase("e")) {
                    node = new ASTNodes.ConstantNode(Math.E);
                }
                // 内置函数。
                else if (name.equals("sin") || name.equals("cos") || name.equals("tan") ||
                        name.equals("sqrt") || name.equals("abs")) {
                    if (ch == -1) {
                        throw syntax("Function '" + name + "' is missing an argument");
                    }
                    node = new ASTNodes.FunctionNode(name, parseFactor());
                } else {
                    String varName = name.equalsIgnoreCase("tick") ? "tick" : name;
                    int index = registry.registerOrGet(varName);
                    node = new ASTNodes.VariableNode(index, varName);
                }
            } else {
                if (ch == -1) {
                    throw syntax("Unexpected end of formula");
                }
                throw syntax("Illegal character '" + (char) ch + "'");
            }

            if (eat('^')) node = new ASTNodes.BinaryNode('^', node, parseFactor());
            return node;
        }

        private ExpressionSyntaxException syntax(String detail) {
            return new ExpressionSyntaxException(detail + " at position " + Math.max(pos, 0)
                    + " in '" + source + "'");
        }
    }
}
