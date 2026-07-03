import java.util.*;

public class Interpreter {
    private static class RuntimeScope {
        Map<String, Object> values = new LinkedHashMap<>();
    }

    private static class Position {
        int row;
        int col;
        Position(int row, int col) {
            this.row = row;
            this.col = col;
        }
    }

    private static class FunctionValue {
        AstNode declaration;
        FunctionValue(AstNode declaration) {
            this.declaration = declaration;
        }
    }

    private static class ReturnSignal extends RuntimeException {
        Object value;
        ReturnSignal(Object value) {
            this.value = value;
        }
    }

    private final Deque<RuntimeScope> scopes = new ArrayDeque<>();
    private final Map<String, FunctionValue> functions = new LinkedHashMap<>();
    private final Map<String, Position> entityPositions = new LinkedHashMap<>();

    public Interpreter() {
        enterScope();
    }

    public void interpret(AstNode program) {
        collectFunctions(program);

        for (AstNode decl : program.getChildren()) {
            switch (decl.getName()) {
                case "FuncDecl", "RecordDecl" -> {
                    // Declarations are already collected or ignored at runtime.
                }
                case "VarDecl" -> executeVarDecl(decl);
                case "LevelDef" -> executeLevel(decl);
                default -> throw runtime("Unknown top-level declaration: " + decl.getName());
            }
        }
    }

    private void collectFunctions(AstNode program) {
        for (AstNode child : program.getChildren()) {
            if (child.getName().equals("FuncDecl")) {
                functions.put(childValue(child, "Name"), new FunctionValue(child));
            }
        }
    }

    private void executeLevel(AstNode level) {
        String name = stripQuotes(childValue(level, "Name"));
        System.out.println("== Running level: " + name + " ==");
        executeBlock(child(level, "Block"), true);
    }

    private void executeBlock(AstNode block, boolean createScope) {
        if (createScope) enterScope();
        for (AstNode stmt : block.getChildren()) {
            executeStatement(stmt);
        }
        if (createScope) exitScope();
    }

    private void executeStatement(AstNode node) {
        switch (node.getName()) {
            case "VarDecl" -> executeVarDecl(node);
            case "Block" -> executeBlock(node, true);
            case "IfStmt" -> executeIfStmt(node);
            case "ForStmt" -> executeForStmt(node);
            case "ReturnStmt" -> executeReturnStmt(node);
            case "PrintStmt" -> System.out.println(stringify(evaluate(first(node))));
            case "PlaceStmt" -> executePlaceStmt(node);
            case "ValidateStmt" -> executeValidateStmt(node);
            case "IdentifierStmt" -> executeIdentifierStmt(node);
            default -> throw runtime("Unknown statement node: " + node.getName());
        }
    }

    private void executeVarDecl(AstNode node) {
        String name = childValue(node, "Name");
        AstNode init = child(node, "Initializer");
        Object value = init == null ? defaultValue(SymbolTable.Type.fromAst(child(node, "Type"))) : evaluate(first(init));
        define(name, value);
    }

    private void executeIfStmt(AstNode node) {
        boolean cond = asBool(evaluate(first(child(node, "Condition"))), "if condition");
        if (cond) {
            executeBlock(first(child(node, "Then")), true);
        } else {
            AstNode elseNode = child(node, "Else");
            if (elseNode != null) executeBlock(first(elseNode), true);
        }
    }

    private void executeForStmt(AstNode node) {
        int start = asInt(evaluate(first(child(node, "Start"))), "for start");
        int end = asInt(evaluate(first(child(node, "End"))), "for end");
        String loopVar = childValue(node, "LoopVariable");

        enterScope();
        define(loopVar, start);
        for (int i = start; i <= end; i++) {
            assign(loopVar, i);
            executeBlock(child(node, "Block"), true);
        }
        exitScope();
    }

    private void executeReturnStmt(AstNode node) {
        Object value = node.getChildren().isEmpty() ? null : evaluate(first(node));
        throw new ReturnSignal(value);
    }

    private void executePlaceStmt(AstNode node) {
        String entity = asEntity(evaluate(first(child(node, "Entity"))), "place entity");
        int row = asInt(evaluate(first(child(node, "Row"))), "place row");
        int col = asInt(evaluate(first(child(node, "Column"))), "place column");

        if (row < 0 || col < 0) {
            throw runtime("Domain error: entity cannot be placed at negative coordinates (" + row + ", " + col + ")");
        }

        if (entity.equals("WALL")) {
            entityPositions.put("WALL@" + row + "," + col, new Position(row, col));
        } else {
            entityPositions.put(entity, new Position(row, col));
        }

        System.out.println("Placed " + entity + " at (" + row + ", " + col + ")");
    }

    private void executeValidateStmt(AstNode node) {
        String message = stripQuotes(childValue(node, "Message"));
        boolean result = asBool(evaluate(first(child(node, "Expression"))), "validate expression");
        System.out.println("[VALIDATE] " + message + ": " + (result ? "PASS" : "FAIL"));
    }

    private void executeIdentifierStmt(AstNode node) {
        AstNode usage = child(node, "IdentifierUsage");
        AstNode assignment = child(node, "Assignment");

        if (assignment == null) {
            evaluate(usage); // function call statement
            return;
        }

        Object right = evaluate(first(assignment));
        assignUsage(usage, right);
    }

    private Object evaluate(AstNode node) {
        return switch (node.getName()) {
            case "Literal" -> literalValue(node.getValue());
            case "ArrayLiteral" -> evalArrayLiteral(node);
            case "GroupedExpr" -> evaluate(first(node));
            case "UnaryExpr" -> evalUnary(node);
            case "BinaryExpr" -> evalBinary(node);
            case "IdentifierUsage" -> evalIdentifierUsage(node);
            default -> throw runtime("Unknown expression node: " + node.getName());
        };
    }

    private Object literalValue(String value) {
        if (value.matches("\\d+")) return Integer.parseInt(value);
        if (value.equals("true")) return true;
        if (value.equals("false")) return false;
        if (value.startsWith("\"") && value.endsWith("\"")) return stripQuotes(value);
        if (Set.of("WALL", "PLAYER", "BOX", "TARGET", "FLOOR").contains(value)) return value;
        throw runtime("Unknown literal: " + value);
    }

    private Object evalArrayLiteral(AstNode node) {
        List<Object> values = new ArrayList<>();
        for (AstNode child : node.getChildren()) {
            values.add(evaluate(child));
        }
        return values;
    }

    private Object evalUnary(AstNode node) {
        String op = node.getValue();
        Object value = evaluate(first(node));
        return switch (op) {
            case "-" -> -asInt(value, "unary '-'");
            case "!" -> !asBool(value, "unary '!'");
            default -> throw runtime("Unknown unary operator: " + op);
        };
    }

    private Object evalBinary(AstNode node) {
        String op = node.getValue();

        if (op.equals("&&")) {
            boolean left = asBool(evaluate(node.getChildren().get(0)), "left operand of &&");
            return left && asBool(evaluate(node.getChildren().get(1)), "right operand of &&");
        }
        if (op.equals("||")) {
            boolean left = asBool(evaluate(node.getChildren().get(0)), "left operand of ||");
            return left || asBool(evaluate(node.getChildren().get(1)), "right operand of ||");
        }

        Object leftObj = evaluate(node.getChildren().get(0));
        Object rightObj = evaluate(node.getChildren().get(1));

        return switch (op) {
            case "+" -> asInt(leftObj, "left operand of +") + asInt(rightObj, "right operand of +");
            case "-" -> asInt(leftObj, "left operand of -") - asInt(rightObj, "right operand of -");
            case "*" -> asInt(leftObj, "left operand of *") * asInt(rightObj, "right operand of *");
            case "/" -> {
                int right = asInt(rightObj, "right operand of /");
                if (right == 0) throw runtime("Runtime error: division by zero");
                yield asInt(leftObj, "left operand of /") / right;
            }
            case "%" -> {
                int right = asInt(rightObj, "right operand of %");
                if (right == 0) throw runtime("Runtime error: modulo by zero");
                yield asInt(leftObj, "left operand of %") % right;
            }
            case "<" -> asInt(leftObj, "left operand of <") < asInt(rightObj, "right operand of <");
            case ">" -> asInt(leftObj, "left operand of >") > asInt(rightObj, "right operand of >");
            case "<=" -> asInt(leftObj, "left operand of <=") <= asInt(rightObj, "right operand of <=");
            case ">=" -> asInt(leftObj, "left operand of >=") >= asInt(rightObj, "right operand of >=");
            case "==" -> Objects.equals(leftObj, rightObj);
            case "!=" -> !Objects.equals(leftObj, rightObj);
            default -> throw runtime("Unknown binary operator: " + op);
        };
    }

    private Object evalIdentifierUsage(AstNode usage) {
        String name = usage.getValue();
        Object current;
        int index = 0;

        if (!usage.getChildren().isEmpty() && usage.getChildren().get(0).getName().equals("Call")) {
            current = callFunction(name, usage.getChildren().get(0));
            index = 1;
        } else {
            current = lookup(name);
        }

        for (int i = index; i < usage.getChildren().size(); i++) {
            AstNode postfix = usage.getChildren().get(i);
            if (postfix.getName().equals("IndexAccess")) {
                int idx = asInt(evaluate(first(postfix)), "array index");
                if (!(current instanceof List<?> list)) {
                    throw runtime("Runtime error: cannot index a non-array value");
                }
                if (idx < 0 || idx >= list.size()) {
                    throw runtime("Runtime error: array index out of range: " + idx);
                }
                current = list.get(idx);
            } else if (postfix.getName().equals("FieldAccess")) {
                if (!(current instanceof Map<?, ?> map)) {
                    throw runtime("Runtime error: cannot access field of non-record value");
                }
                current = map.get(postfix.getValue());
            } else if (postfix.getName().equals("Call")) {
                throw runtime("Runtime error: method calls are not supported");
            }
        }
        return current;
    }

    private Object callFunction(String name, AstNode call) {
        FunctionValue f = functions.get(name);
        if (f == null) throw runtime("Runtime error: undefined function '" + name + "'");

        AstNode params = child(f.declaration, "Params");
        if (params.getChildren().size() != call.getChildren().size()) {
            throw runtime("Runtime error: function '" + name + "' argument count mismatch");
        }

        List<Object> args = new ArrayList<>();
        for (AstNode arg : call.getChildren()) {
            args.add(evaluate(arg));
        }

        enterScope();
        for (int i = 0; i < params.getChildren().size(); i++) {
            String paramName = childValue(params.getChildren().get(i), "Name");
            define(paramName, args.get(i));
        }

        try {
            executeBlock(child(f.declaration, "Block"), false);
        } catch (ReturnSignal r) {
            exitScope();
            return r.value;
        }

        exitScope();
        return null;
    }

    private void assignUsage(AstNode usage, Object value) {
        if (!usage.getChildren().isEmpty()) {
            // Simple support for array[index] = value.
            if (usage.getChildren().size() == 1 && usage.getChildren().get(0).getName().equals("IndexAccess")) {
                Object arr = lookup(usage.getValue());
                if (!(arr instanceof List<?> rawList)) throw runtime("Runtime error: cannot index a non-array value");
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) rawList;
                int idx = asInt(evaluate(first(usage.getChildren().get(0))), "array index");
                if (idx < 0 || idx >= list.size()) throw runtime("Runtime error: array index out of range: " + idx);
                list.set(idx, value);
                return;
            }
            throw runtime("Runtime error: only simple variables and one-level array indexing are assignable");
        }
        assign(usage.getValue(), value);
    }

    private Object defaultValue(SymbolTable.Type type) {
        if (type.equals(SymbolTable.Type.INT)) return 0;
        if (type.equals(SymbolTable.Type.BOOL)) return false;
        if (type.equals(SymbolTable.Type.STRING)) return "";
        if (type.equals(SymbolTable.Type.ENTITY)) return "FLOOR";
        if (type.isArray()) return new ArrayList<>();
        return null;
    }

    private void enterScope() {
        scopes.push(new RuntimeScope());
    }

    private void exitScope() {
        if (scopes.size() <= 1) throw runtime("Internal error: cannot exit global scope");
        scopes.pop();
    }

    private void define(String name, Object value) {
        RuntimeScope current = scopes.peek();
        if (current.values.containsKey(name)) throw runtime("Runtime error: variable already defined: " + name);
        current.values.put(name, value);
    }

    private Object lookup(String name) {
        for (RuntimeScope scope : scopes) {
            if (scope.values.containsKey(name)) return scope.values.get(name);
        }
        throw runtime("Runtime error: undefined variable '" + name + "'");
    }

    private void assign(String name, Object value) {
        for (RuntimeScope scope : scopes) {
            if (scope.values.containsKey(name)) {
                scope.values.put(name, value);
                return;
            }
        }
        throw runtime("Runtime error: undefined variable '" + name + "'");
    }

    private int asInt(Object value, String context) {
        if (value instanceof Integer i) return i;
        throw runtime("Runtime error: " + context + " must be int but got " + typeName(value));
    }

    private boolean asBool(Object value, String context) {
        if (value instanceof Boolean b) return b;
        throw runtime("Runtime error: " + context + " must be bool but got " + typeName(value));
    }

    private String asEntity(Object value, String context) {
        if (value instanceof String s && Set.of("WALL", "PLAYER", "BOX", "TARGET", "FLOOR").contains(s)) return s;
        throw runtime("Runtime error: " + context + " must be entity but got " + typeName(value));
    }

    private String stringify(Object value) {
        if (value == null) return "void";
        return String.valueOf(value);
    }

    private String typeName(Object value) {
        if (value == null) return "void";
        if (value instanceof Integer) return "int";
        if (value instanceof Boolean) return "bool";
        if (value instanceof String s && Set.of("WALL", "PLAYER", "BOX", "TARGET", "FLOOR").contains(s)) return "entity";
        if (value instanceof String) return "string";
        if (value instanceof List<?>) return "array";
        return value.getClass().getSimpleName();
    }

    private RuntimeException runtime(String message) {
        return new RuntimeException(message);
    }

    private String stripQuotes(String s) {
        if (s != null && s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private AstNode child(AstNode node, String name) {
        if (node == null) return null;
        for (AstNode c : node.getChildren()) {
            if (c.getName().equals(name)) return c;
        }
        return null;
    }

    private String childValue(AstNode node, String name) {
        AstNode c = child(node, name);
        if (c == null) throw runtime("Internal error: missing child " + name + " in " + node.getName());
        return c.getValue();
    }

    private AstNode first(AstNode node) {
        if (node == null || node.getChildren().isEmpty()) throw runtime("Internal error: missing child expression");
        return node.getChildren().get(0);
    }
}
