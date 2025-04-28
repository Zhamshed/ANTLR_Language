import java.util.HashMap;
import java.util.Map;
import org.antlr.v4.runtime.Token;

public class EvalVisitor extends ExprBaseVisitor<Object> {
    private final Map<String, Object> memory = new HashMap<>();
    private final SymbolTable symbolTable = new SymbolTable();


    @Override
    public Object visitVarDeclStatement(ExprParser.VarDeclStatementContext ctx) {
        String typeStr = ctx.TYPE().getText();
        for (var var : ctx.VAR()) {
            String name = var.getText();
            if (!symbolTable.isDeclared(name)) {
                Object defaultValue = switch (typeStr) {
                    case "int" -> 0;
                    case "float" -> 0.0;
                    case "string" -> "";
                    case "bool" -> false;
                    default -> null;
                };
                memory.put(name, defaultValue);
                Type type = switch (typeStr) {
                    case "int" -> Type.INT;
                    case "float" -> Type.FLOAT;
                    case "string" -> Type.STRING;
                    case "bool" -> Type.BOOL;
                    default -> Type.INVALID;
                };
                symbolTable.declare(name, type);
            }
        }
        return null;
    }

    @Override
    public Object visitAssignExpression(ExprParser.AssignExpressionContext ctx) {
        String name = ctx.VAR().getText();
        if (!symbolTable.isDeclared(name)) {
            return null;
        }
        Object value;
        if (ctx.expression() instanceof ExprParser.AssignExpressionContext nestedAssign) {
            value = visit(nestedAssign);
        } else {
            value = visit(ctx.expression());
        }
        memory.put(name, value);
        return value;
    }

    @Override
    public Object visitVarExpression(ExprParser.VarExpressionContext ctx) {
        String name = ctx.VAR().getText();
        if (!symbolTable.isDeclared(name)) {return null;}
        return memory.get(name);
    }

    @Override
    public Object visitLiteralExpression(ExprParser.LiteralExpressionContext ctx) {
        return visit(ctx.literal());
    }

    @Override
    public Object visitLiteral(ExprParser.LiteralContext ctx) {
        if (ctx.INT() != null) {
            return Integer.parseInt(ctx.INT().getText());
        } else if (ctx.FLOAT() != null) {
            return Double.parseDouble(ctx.FLOAT().getText());
        } else if (ctx.STRING() != null) {
            return ctx.STRING().getText().replaceAll("\"", "");
        } else if (ctx.BOOL() != null) {
            return Boolean.parseBoolean(ctx.BOOL().getText());
        } else {
            throw new RuntimeException("Unknown literal type: " + ctx.getText());
        }
    }

    @Override
    public Object visitAritmExpression(ExprParser.AritmExpressionContext ctx) {
        Object left = visit(ctx.expression(0));
        Object right = visit(ctx.expression(1));
        String operator = ctx.op.getText();

        return switch (operator) {
            case "+" -> add(left, right);
            case "-" -> subtract(left, right);
            case "*" -> multiply(left, right);
            case "/" -> divide(left, right);
            default -> throw new RuntimeException("Unknown arithmetic operator: " + operator);
        };
    }


    @Override
    public Object visitModuloExpression(ExprParser.ModuloExpressionContext ctx) {
        Object left = visit(ctx.expression(0));
        Object right = visit(ctx.expression(1));
        return modulo(left, right);
    }

    @Override
    public Object visitConcatExpression(ExprParser.ConcatExpressionContext ctx) {
        Object left = visit(ctx.expression(0));
        Object right = visit(ctx.expression(1));
        return String.valueOf(left) + right;
    }

    @Override
    public Object visitRelationExpression(ExprParser.RelationExpressionContext ctx) {
        double a = toDouble(visit(ctx.expression(0)));
        double b = toDouble(visit(ctx.expression(1)));
        return switch (ctx.op.getText()) {
            case "<" -> a < b;
            case ">" -> a > b;
            default -> throw new RuntimeException("Unknown relational operator: " + ctx.op.getText());
        };
    }

    @Override
    public Object visitComparisonExpression(ExprParser.ComparisonExpressionContext ctx) {
        Object left = visit(ctx.expression(0));
        Object right = visit(ctx.expression(1));
        return switch (ctx.op.getText()) {
            case "==" -> left.equals(right);
            case "!=" -> !left.equals(right);
            default -> throw new RuntimeException("Unknown comparison operator: " + ctx.op.getText());
        };
    }

    @Override
    public Object visitLogicExpression(ExprParser.LogicExpressionContext ctx) {
        Object left = visit(ctx.expression(0));
        Object right = visit(ctx.expression(1));
        if (!(left instanceof Boolean) || !(right instanceof Boolean)) {
            throw new RuntimeException("Logical operators require boolean operands.");
        }
        return switch (ctx.op.getText()) {
            case "&&" -> (Boolean) left && (Boolean) right;
            case "||" -> (Boolean) left || (Boolean) right;
            default -> throw new RuntimeException("Unknown logical operator: " + ctx.op.getText());
        };
    }

    @Override
    public Object visitNotExpression(ExprParser.NotExpressionContext ctx) {
        Object value = visit(ctx.expression());
        if (!(value instanceof Boolean)) {
            throw new RuntimeException("Expected boolean inside '!', but got: " + value.getClass());
        }
        return !(Boolean) value;
    }

    @Override
    public Object visitNegExpression(ExprParser.NegExpressionContext ctx) {
        Object value = visit(ctx.expression());
        if (value instanceof Integer) {
            return -(Integer) value;
        } else if (value instanceof Double) {
            return -(Double) value;
        } else {
            throw new RuntimeException("Unary minus requires numeric operand.");
        }
    }

    @Override
    public Object visitParenExpression(ExprParser.ParenExpressionContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public Object visitWriteStatement(ExprParser.WriteStatementContext ctx) {
        for (var expr : ctx.expression()) {
            Object value = visit(expr);
            System.out.print(value + " ");
        }
        System.out.println();
        return null;
    }

    // Helpers
    private Object add(Object a, Object b) {
        if (a instanceof String || b instanceof String) return String.valueOf(a) + b;
        if (a instanceof Double || b instanceof Double) return toDouble(a) + toDouble(b);
        return (Integer) a + (Integer) b;
    }

    private Object subtract(Object a, Object b) {
        if (a instanceof Double || b instanceof Double) return toDouble(a) - toDouble(b);
        return (Integer) a - (Integer) b;
    }

    private Object multiply(Object a, Object b) {
        if (a instanceof Double || b instanceof Double) return toDouble(a) * toDouble(b);
        return (Integer) a * (Integer) b;
    }

    private Object divide(Object a, Object b) {
        if (a instanceof Double || b instanceof Double) return toDouble(a) / toDouble(b);
        return (Integer) a / (Integer) b;
    }

    private Object modulo(Object a, Object b) {
        if (!(a instanceof Integer) || !(b instanceof Integer)) {
            throw new RuntimeException("Modulo can only be used with integers.");
        }
        return (Integer) a % (Integer) b;
    }

    private double toDouble(Object obj) {
        return obj instanceof Integer ? (Integer) obj : (Double) obj;
    }
}
