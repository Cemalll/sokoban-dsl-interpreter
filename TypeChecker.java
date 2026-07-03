import java.util.*;

public class TypeChecker {
    private final SymbolTable symbols = new SymbolTable();
    private SymbolTable.Type currentReturnType = SymbolTable.Type.VOID;
    private boolean insideFunction = false;

    private static final Set<String> ENTITY_LITERALS = Set.of("WALL", "PLAYER", "BOX", "TARGET", "FLOOR");

    public void check(AstNode program) {
        if (!program.getName().equals("Program")) {
            throw new RuntimeException("Type error: root node must be Program");
        }

        collectRecords(program);
        collectFunctionSignatures(program);

        for (AstNode child : program.getChildren()) {
            checkDeclaration(child);
        }
    }

    private void collectRecords(AstNode program) {
        for (AstNode decl : program.getChildren()) {
            if (!decl.getName().equals("RecordDecl")) continue;
            String name = childValue(decl, "Name");
            SymbolTable.RecordSymbol record = new SymbolTable.RecordSymbol(name);

            AstNode fields = child(decl, "Fields");
            if (fields != null) {
                for (AstNode fieldDecl : fields.getChildren()) {
                    String fieldName = childValue(fieldDecl, "Name");
                    SymbolTable.Type fieldType = SymbolTable.Type.fromAst(child(fieldDecl, "Type"));
                    record.fields.put(fieldName, fieldType);
                }
            }
            symbols.defineRecord(record);
        }
    }

    private void collectFunctionSignatures(AstNode program) {
        for (AstNode decl : program.getChildren()) {
            if (!decl.getName().equals("FuncDecl")) continue;

            SymbolTable.Type returnType = SymbolTable.Type.fromAst(child(decl, "ReturnType"));
            ensureKnownType(returnType);

            String name = childValue(decl, "Name");
            List<SymbolTable.VariableSymbol> params = new ArrayList<>();
            AstNode paramsNode = child(decl, "Params");

            if (paramsNode != null) {
                for (AstNode param : paramsNode.getChildren()) {
                    SymbolTable.Type paramType = SymbolTable.Type.fromAst(child(param, "Type"));
                    ensureKnownType(paramType);
                    params.add(new SymbolTable.VariableSymbol(childValue(param, "Name"), paramType));
                }
            }

            symbols.defineFunction(new SymbolTable.FunctionSymbol(name, returnType, params, decl));
        }
    }

    private void checkDeclaration(AstNode node) {
        switch (node.getName()) {
            case "RecordDecl" -> checkRecordDecl(node);
            case "FuncDecl" -> checkFuncDecl(node);
            case "VarDecl" -> checkVarDecl(node);
            case "LevelDef" -> checkLevelDef(node);
            default -> throw new RuntimeException("Type error: unknown declaration node " + node.getName());
        }
    }

    private void checkRecordDecl(AstNode node) {
        AstNode fields = child(node, "Fields");
        if (fields == null) return;
        for (AstNode field : fields.getChildren()) {
            SymbolTable.Type type = SymbolTable.Type.fromAst(child(field, "Type"));
            ensureKnownType(type);
            AstNode init = child(field, "Initializer");
            if (init != null) {
                SymbolTable.Type exprType = checkExpr(first(init));
                requireAssignable(type, exprType, "record field initializer");
            }
        }
    }

    private void checkFuncDecl(AstNode node) {
        SymbolTable.FunctionSymbol f = symbols.lookupFunction(childValue(node, "Name"));

        SymbolTable.Type oldReturn = currentReturnType;
        boolean oldInside = insideFunction;
        currentReturnType = f.returnType;
        insideFunction = true;

        symbols.enterScope();
        for (SymbolTable.VariableSymbol p : f.params) {
            symbols.defineVariable(p.name, p.type);
        }
        checkBlock(child(node, "Block"), false);
        symbols.exitScope();

        currentReturnType = oldReturn;
        insideFunction = oldInside;
    }

    private void checkLevelDef(AstNode node) {
        checkBlock(child(node, "Block"), true);
    }

    private void checkBlock(AstNode block, boolean createScope) {
        if (createScope) symbols.enterScope();
        for (AstNode stmt : block.getChildren()) {
            checkStatement(stmt);
        }
        if (createScope) symbols.exitScope();
    }

    private void checkStatement(AstNode node) {
        switch (node.getName()) {
            case "VarDecl" -> checkVarDecl(node);
            case "Block" -> checkBlock(node, true);
            case "IfStmt" -> checkIfStmt(node);
            case "ForStmt" -> checkForStmt(node);
            case "ReturnStmt" -> checkReturnStmt(node);
            case "PrintStmt" -> checkExpr(first(node));
            case "PlaceStmt" -> checkPlaceStmt(node);
            case "ValidateStmt" -> checkValidateStmt(node);
            case "IdentifierStmt" -> checkIdentifierStmt(node);
            default -> throw new RuntimeException("Type error: unknown statement node " + node.getName());
        }
    }

    private void checkVarDecl(AstNode node) {
        SymbolTable.Type declared = SymbolTable.Type.fromAst(child(node, "Type"));
        ensureKnownType(declared);
        String name = childValue(node, "Name");

        AstNode init = child(node, "Initializer");
        if (init != null) {
            SymbolTable.Type actual = checkExpr(first(init));
            requireAssignable(declared, actual, "initializer of variable '" + name + "'");
        }

        symbols.defineVariable(name, declared);
    }

    private void checkIfStmt(AstNode node) {
        SymbolTable.Type cond = checkExpr(first(child(node, "Condition")));
        requireType(SymbolTable.Type.BOOL, cond, "if condition must be bool");
        checkBlock(first(child(node, "Then")), true);
        AstNode elseNode = child(node, "Else");
        if (elseNode != null) checkBlock(first(elseNode), true);
    }

    private void checkForStmt(AstNode node) {
        SymbolTable.Type start = checkExpr(first(child(node, "Start")));
        SymbolTable.Type end = checkExpr(first(child(node, "End")));
        requireType(SymbolTable.Type.INT, start, "for start expression must be int");
        requireType(SymbolTable.Type.INT, end, "for end expression must be int");

        symbols.enterScope();
        symbols.defineVariable(childValue(node, "LoopVariable"), SymbolTable.Type.INT);
        checkBlock(child(node, "Block"), false);
        symbols.exitScope();
    }

    private void checkReturnStmt(AstNode node) {
        if (!insideFunction) {
            throw new RuntimeException("Type error: return statement can only be used inside a function");
        }

        if (node.getChildren().isEmpty()) {
            requireType(currentReturnType, SymbolTable.Type.VOID, "return statement");
            return;
        }

        SymbolTable.Type actual = checkExpr(first(node));
        requireAssignable(currentReturnType, actual, "return statement");
    }

    private void checkPlaceStmt(AstNode node) {
        requireType(SymbolTable.Type.ENTITY, checkExpr(first(child(node, "Entity"))), "place entity expression must be entity");
        requireType(SymbolTable.Type.INT, checkExpr(first(child(node, "Row"))), "place row expression must be int");
        requireType(SymbolTable.Type.INT, checkExpr(first(child(node, "Column"))), "place column expression must be int");
    }

    private void checkValidateStmt(AstNode node) {
        SymbolTable.Type actual = checkExpr(first(child(node, "Expression")));
        requireType(SymbolTable.Type.BOOL, actual, "validate expression must be bool");
    }

    private void checkIdentifierStmt(AstNode node) {
        AstNode usage = child(node, "IdentifierUsage");
        AstNode assignment = child(node, "Assignment");

        if (assignment == null) {
            if (!isFunctionCallUsage(usage)) {
                throw new RuntimeException("Type error: standalone identifier statement must be a function call or assignment");
            }
            checkExpr(usage);
            return;
        }

        SymbolTable.Type left = checkAssignableUsage(usage);
        SymbolTable.Type right = checkExpr(first(assignment));
        requireAssignable(left, right, "assignment to '" + usage.getValue() + "'");
    }

    private SymbolTable.Type checkExpr(AstNode node) {
        return switch (node.getName()) {
            case "Literal" -> literalType(node.getValue());
            case "ArrayLiteral" -> checkArrayLiteral(node);
            case "GroupedExpr" -> checkExpr(first(node));
            case "UnaryExpr" -> checkUnaryExpr(node);
            case "BinaryExpr" -> checkBinaryExpr(node);
            case "IdentifierUsage" -> checkIdentifierUsage(node);
            default -> throw new RuntimeException("Type error: unknown expression node " + node.getName());
        };
    }

    private SymbolTable.Type literalType(String value) {
        if (value == null) return SymbolTable.Type.UNKNOWN;
        if (value.matches("\\d+")) return SymbolTable.Type.INT;
        if (value.equals("true") || value.equals("false")) return SymbolTable.Type.BOOL;
        if (value.startsWith("\"") && value.endsWith("\"")) return SymbolTable.Type.STRING;
        if (ENTITY_LITERALS.contains(value)) return SymbolTable.Type.ENTITY;
        return SymbolTable.Type.UNKNOWN;
    }

    private SymbolTable.Type checkArrayLiteral(AstNode node) {
        if (node.getChildren().isEmpty()) {
            return new SymbolTable.Type("unknown", 1);
        }
        SymbolTable.Type firstType = checkExpr(node.getChildren().get(0));
        for (int i = 1; i < node.getChildren().size(); i++) {
            requireType(firstType, checkExpr(node.getChildren().get(i)), "array literal elements must have same type");
        }
        return new SymbolTable.Type(firstType.name(), firstType.arrayDepth() + 1);
    }

    private SymbolTable.Type checkUnaryExpr(AstNode node) {
        String op = node.getValue();
        SymbolTable.Type operand = checkExpr(first(node));
        if (op.equals("-")) {
            requireType(SymbolTable.Type.INT, operand, "unary '-' requires int");
            return SymbolTable.Type.INT;
        }
        if (op.equals("!")) {
            requireType(SymbolTable.Type.BOOL, operand, "unary '!' requires bool");
            return SymbolTable.Type.BOOL;
        }
        throw new RuntimeException("Type error: unknown unary operator " + op);
    }

    private SymbolTable.Type checkBinaryExpr(AstNode node) {
        String op = node.getValue();
        SymbolTable.Type left = checkExpr(node.getChildren().get(0));
        SymbolTable.Type right = checkExpr(node.getChildren().get(1));

        switch (op) {
            case "+", "-", "*", "/", "%" -> {
                requireType(SymbolTable.Type.INT, left, "left operand of '" + op + "' must be int");
                requireType(SymbolTable.Type.INT, right, "right operand of '" + op + "' must be int");
                return SymbolTable.Type.INT;
            }
            case "<", ">", "<=", ">=" -> {
                requireType(SymbolTable.Type.INT, left, "left operand of '" + op + "' must be int");
                requireType(SymbolTable.Type.INT, right, "right operand of '" + op + "' must be int");
                return SymbolTable.Type.BOOL;
            }
            case "==", "!=" -> {
                requireType(left, right, "both operands of '" + op + "' must have same type");
                return SymbolTable.Type.BOOL;
            }
            case "&&", "||" -> {
                requireType(SymbolTable.Type.BOOL, left, "left operand of '" + op + "' must be bool");
                requireType(SymbolTable.Type.BOOL, right, "right operand of '" + op + "' must be bool");
                return SymbolTable.Type.BOOL;
            }
            default -> throw new RuntimeException("Type error: unknown binary operator " + op);
        }
    }

    private SymbolTable.Type checkIdentifierUsage(AstNode usage) {
        String name = usage.getValue();
        SymbolTable.Type current;
        int index = 0;

        if (!usage.getChildren().isEmpty() && usage.getChildren().get(0).getName().equals("Call")) {
            current = checkFunctionCall(name, usage.getChildren().get(0));
            index = 1;
        } else {
            current = symbols.lookupVariable(name).type;
        }

        for (int i = index; i < usage.getChildren().size(); i++) {
            AstNode postfix = usage.getChildren().get(i);
            if (postfix.getName().equals("IndexAccess")) {
                requireType(SymbolTable.Type.INT, checkExpr(first(postfix)), "array index must be int");
                current = current.elementType();
            } else if (postfix.getName().equals("FieldAccess")) {
                SymbolTable.RecordSymbol record = symbols.lookupRecord(current.name());
                SymbolTable.Type fieldType = record.fields.get(postfix.getValue());
                if (fieldType == null) {
                    throw new RuntimeException("Type error: record '" + record.name + "' has no field '" + postfix.getValue() + "'");
                }
                current = fieldType;
            } else if (postfix.getName().equals("Call")) {
                throw new RuntimeException("Type error: method calls are not supported; only normal function calls are supported");
            }
        }

        return current;
    }

    private SymbolTable.Type checkAssignableUsage(AstNode usage) {
        if (!usage.getChildren().isEmpty() && usage.getChildren().get(0).getName().equals("Call")) {
            throw new RuntimeException("Type error: cannot assign to a function call");
        }
        return checkIdentifierUsage(usage);
    }

    private SymbolTable.Type checkFunctionCall(String name, AstNode call) {
        SymbolTable.FunctionSymbol f = symbols.lookupFunction(name);
        if (call.getChildren().size() != f.params.size()) {
            throw new RuntimeException("Type error: function '" + name + "' expects " + f.params.size() +
                    " arguments but got " + call.getChildren().size());
        }
        for (int i = 0; i < f.params.size(); i++) {
            SymbolTable.Type actual = checkExpr(call.getChildren().get(i));
            requireAssignable(f.params.get(i).type, actual, "argument " + (i + 1) + " of function '" + name + "'");
        }
        return f.returnType;
    }

    private boolean isFunctionCallUsage(AstNode usage) {
        return !usage.getChildren().isEmpty() && usage.getChildren().get(0).getName().equals("Call");
    }

    private void ensureKnownType(SymbolTable.Type type) {
        if (!symbols.isKnownType(type)) {
            throw new RuntimeException("Type error: unknown type '" + type + "'");
        }
    }

    private void requireAssignable(SymbolTable.Type expected, SymbolTable.Type actual, String context) {
        requireType(expected, actual, context);
    }

    private void requireType(SymbolTable.Type expected, SymbolTable.Type actual, String context) {
        if (!expected.equals(actual)) {
            throw new RuntimeException("Type error: " + context + " expected " + expected + " but got " + actual);
        }
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
        if (c == null) throw new RuntimeException("Internal error: missing child " + name + " in " + node.getName());
        return c.getValue();
    }

    private AstNode first(AstNode node) {
        if (node == null || node.getChildren().isEmpty()) {
            throw new RuntimeException("Internal error: missing child expression");
        }
        return node.getChildren().get(0);
    }
}
