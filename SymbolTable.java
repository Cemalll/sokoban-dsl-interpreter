import java.util.*;

public class SymbolTable {

    public static class Type {
        public static final Type INT = new Type("int", 0);
        public static final Type STRING = new Type("string", 0);
        public static final Type BOOL = new Type("bool", 0);
        public static final Type ENTITY = new Type("entity", 0);
        public static final Type VOID = new Type("void", 0);
        public static final Type UNKNOWN = new Type("unknown", 0);

        private final String name;
        private final int arrayDepth;

        public Type(String name, int arrayDepth) {
            this.name = name;
            this.arrayDepth = arrayDepth;
        }

        public String name() {
            return name;
        }

        public int arrayDepth() {
            return arrayDepth;
        }

        public boolean isArray() {
            return arrayDepth > 0;
        }

        public Type elementType() {
            if (arrayDepth <= 0) {
                throw new RuntimeException("Type error: type '" + this + "' is not an array");
            }
            return new Type(name, arrayDepth - 1);
        }

        public boolean isNumeric() {
            return arrayDepth == 0 && name.equals("int");
        }

        public boolean isBool() {
            return arrayDepth == 0 && name.equals("bool");
        }

        public boolean isString() {
            return arrayDepth == 0 && name.equals("string");
        }

        public boolean isEntity() {
            return arrayDepth == 0 && name.equals("entity");
        }

        public boolean isVoid() {
            return arrayDepth == 0 && name.equals("void");
        }

        public static Type fromAst(AstNode typeNode) {
            if (typeNode == null) return UNKNOWN;

            if (typeNode.getName().equals("ReturnType") && "void".equals(typeNode.getValue())) {
                return VOID;
            }

            AstNode realType = typeNode;
            if (typeNode.getName().equals("ReturnType") && !typeNode.getChildren().isEmpty()) {
                realType = typeNode.getChildren().get(0);
            }

            String base = null;
            int depth = 0;

            for (AstNode child : realType.getChildren()) {
                if (child.getName().equals("BaseType") || child.getName().equals("CustomType")) {
                    base = child.getValue();
                } else if (child.getName().equals("ArraySuffix")) {
                    depth++;
                }
            }

            if (base == null) return UNKNOWN;
            return new Type(base, depth);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Type t)) return false;
            return name.equals(t.name) && arrayDepth == t.arrayDepth;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, arrayDepth);
        }

        @Override
        public String toString() {
            return name + "[]".repeat(arrayDepth);
        }
    }

    public static class VariableSymbol {
        public final String name;
        public final Type type;

        public VariableSymbol(String name, Type type) {
            this.name = name;
            this.type = type;
        }
    }

    public static class FunctionSymbol {
        public final String name;
        public final Type returnType;
        public final List<VariableSymbol> params;
        public final AstNode declarationNode;

        public FunctionSymbol(String name, Type returnType, List<VariableSymbol> params, AstNode declarationNode) {
            this.name = name;
            this.returnType = returnType;
            this.params = params;
            this.declarationNode = declarationNode;
        }
    }

    public static class RecordSymbol {
        public final String name;
        public final Map<String, Type> fields = new LinkedHashMap<>();

        public RecordSymbol(String name) {
            this.name = name;
        }
    }

    private final Deque<Map<String, VariableSymbol>> scopes = new ArrayDeque<>();
    private final Map<String, FunctionSymbol> functions = new LinkedHashMap<>();
    private final Map<String, RecordSymbol> records = new LinkedHashMap<>();

    public SymbolTable() {
        enterScope();
    }

    public void enterScope() {
        scopes.push(new LinkedHashMap<>());
    }

    public void exitScope() {
        if (scopes.size() <= 1) {
            throw new RuntimeException("Internal error: cannot exit global scope");
        }
        scopes.pop();
    }

    public void defineVariable(String name, Type type) {
        Map<String, VariableSymbol> current = scopes.peek();
        if (current.containsKey(name)) {
            throw new RuntimeException("Type error: variable '" + name + "' is already declared in this scope");
        }
        current.put(name, new VariableSymbol(name, type));
    }

    public VariableSymbol lookupVariable(String name) {
        for (Map<String, VariableSymbol> scope : scopes) {
            if (scope.containsKey(name)) return scope.get(name);
        }
        throw new RuntimeException("Type error: variable '" + name + "' is not declared");
    }

    public boolean hasVariable(String name) {
        for (Map<String, VariableSymbol> scope : scopes) {
            if (scope.containsKey(name)) return true;
        }
        return false;
    }

    public void defineFunction(FunctionSymbol function) {
        if (functions.containsKey(function.name)) {
            throw new RuntimeException("Type error: function '" + function.name + "' is already declared");
        }
        functions.put(function.name, function);
    }

    public FunctionSymbol lookupFunction(String name) {
        FunctionSymbol f = functions.get(name);
        if (f == null) {
            throw new RuntimeException("Type error: function '" + name + "' is not declared");
        }
        return f;
    }

    public boolean hasFunction(String name) {
        return functions.containsKey(name);
    }

    public void defineRecord(RecordSymbol record) {
        if (records.containsKey(record.name)) {
            throw new RuntimeException("Type error: record '" + record.name + "' is already declared");
        }
        records.put(record.name, record);
    }

    public RecordSymbol lookupRecord(String name) {
        RecordSymbol r = records.get(name);
        if (r == null) {
            throw new RuntimeException("Type error: record type '" + name + "' is not declared");
        }
        return r;
    }

    public boolean isKnownType(Type type) {
        if (type.isVoid()) return true;
        if (type.name().equals("int") || type.name().equals("string") ||
            type.name().equals("bool") || type.name().equals("entity")) {
            return true;
        }
        return records.containsKey(type.name());
    }
}
