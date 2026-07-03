public class Errors {

    // ==========================================
    // 1. TEMEL HATA SINIFI
    // ==========================================
    public static class SokobanRuntimeException extends RuntimeException {
        private final Token token;

        public SokobanRuntimeException(Token token, String message) {
            super(message);
            this.token = token;
        }

        public Token getToken() {
            return token;
        }

        @Override
        public String getMessage() {
            if (token != null) {
                return String.format("Runtime Error at line %d, column %d: %s", 
                    token.line(), token.column(), super.getMessage());
            }
            return "Runtime Error: " + super.getMessage();
        }
    }

    // ==========================================
    // 2. STANDART ÇALIŞMA ZAMANI HATALARI
    // ==========================================
    public static class DivisionByZeroException extends SokobanRuntimeException {
        public DivisionByZeroException(Token token) {
            super(token, "Division or modulo by zero is not allowed.");
        }
    }

    public static class IndexOutOfBoundsException extends SokobanRuntimeException {
        public IndexOutOfBoundsException(Token token, int index, int size) {
            super(token, String.format("Index %d is out of bounds for array of size %d.", index, size));
        }
    }

    public static class TypeMismatchException extends SokobanRuntimeException {
        public TypeMismatchException(Token token, String expectedType, String actualType) {
            super(token, String.format("Type mismatch: expected %s, but got %s.", expectedType, actualType));
        }
    }

    public static class UndefinedVariableException extends SokobanRuntimeException {
        public UndefinedVariableException(Token token, String name) {
            super(token, String.format("Undefined variable '%s'.", name));
        }
    }

    // ==========================================
    // 3. ALANA ÖZEL (DOMAIN-SPECIFIC) HATALAR
    // ==========================================
    public static class GridOutOfBoundsException extends SokobanRuntimeException {
        public GridOutOfBoundsException(Token token, int row, int col) {
            super(token, String.format("Cannot place entity at (%d, %d): Coordinates are outside the level grid.", row, col));
        }
    }

    public static class InvalidPlacementException extends SokobanRuntimeException {
        public InvalidPlacementException(Token token, String entityName, int row, int col) {
            super(token, String.format("Cannot place '%s' at (%d, %d): Position is already occupied or invalid.", entityName, row, col));
        }
    }

    public static class ValidationFailedException extends SokobanRuntimeException {
        public ValidationFailedException(Token token, String validationMessage) {
            super(token, String.format("Validation failed: %s", validationMessage));
        }
    }
}