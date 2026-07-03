import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// =====================================================
// TOKEN TYPES
// =====================================================
enum TokenType {
    KEYWORD,
    IDENTIFIER,
    INT_LITERAL,
    STRING_LITERAL,
    BOOL_LITERAL,
    ENTITY_LITERAL,
    OP_MULTI,
    OP_SINGLE,
    PUNCTUATION
}

// =====================================================
// KEYWORDS
// =====================================================
enum Keyword {
    VAR,
    FUNC,
    IF,
    ELSE,
    FOR,
    IN,
    TO,
    RETURN,
    PLACE,
    AT,
    VALIDATE,
    RECORD,
    LEVEL,
    PRINT,
    INT,
    STRING,
    BOOL,
    ENTITY,
    VOID;

    public static boolean contains(String value) {
        for (Keyword keyword : values()) {
            if (keyword.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}

// =====================================================
// BOOLEAN LITERALS
// =====================================================
enum BoolLiteral {
    TRUE,
    FALSE;

    public static boolean contains(String value) {
        for (BoolLiteral literal : values()) {
            if (literal.name().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}

// =====================================================
// ENTITY LITERALS
// =====================================================
enum EntityLiteral {
    WALL,
    PLAYER,
    BOX,
    TARGET,
    FLOOR;

    public static boolean contains(String value) {
        for (EntityLiteral literal : values()) {
            if (literal.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}

// =====================================================
// TOKEN
// =====================================================
record Token(TokenType type, String value, int line, int column) {

    @Override
    public String toString() {
        return String.format(
            "%-18s %-15s Line: %d, Col: %d",
            type,
            value,
            line,
            column
        );
    }
}

// =====================================================
// LEXER
// =====================================================
public class Lexer {

    // =================================================
    // REGEX
    // =================================================
    private static final String REGEX =
        "(?<COMMENT>\\(\\*([\\s\\S]*?)\\*\\))" +
        "|(?<STRING>\"[^\"]*\")" +
        "|(?<INT>\\d+)" +
        "|(?<OPMULTI>==|!=|<=|>=|\\|\\||&&)" +
        "|(?<PUNCT>[\\{\\}\\[\\]\\(\\)\\.,;:])" +
        "|(?<OPSINGLE>[=+\\-*/%<>!])" +
        "|(?<ID>[A-Za-z][A-Za-z0-9_]*)" +
        "|(?<WHITESPACE>[ \\t\\r\\n]+)" +
        "|(?<MISMATCH>.)";

    private final Pattern pattern;
    private final String code;

    public Lexer(String code) {
        this.code = code;
        this.pattern = Pattern.compile(REGEX);
    }

    // =================================================
    // TOKENIZE
    // =================================================
    public List<Token> tokenize() {

        List<Token> tokens = new ArrayList<>();

        Matcher matcher = pattern.matcher(code);

        int lineNum = 1;
        int lineStart = 0;

        while (matcher.find()) {

            String value = matcher.group();

            int start = matcher.start();

            int column = start - lineStart;

            String rawType = determineTokenType(matcher);

            // =========================================
            // WHITESPACE / COMMENT
            // =========================================
            if (rawType.equals("WHITESPACE") ||
                rawType.equals("COMMENT")) {

                if (value.contains("\n")) {

                    long newlines =
                        value.chars()
                             .filter(ch -> ch == '\n')
                             .count();

                    lineNum += newlines;

                    lineStart =
                        start + value.lastIndexOf('\n') + 1;
                }

                continue;
            }

            // =========================================
            // IDENTIFIERS / KEYWORDS
            // =========================================
            TokenType tokenType;

            switch (rawType) {

                case "STRING" -> tokenType =
                    TokenType.STRING_LITERAL;

                case "INT" -> tokenType =
                    TokenType.INT_LITERAL;

                case "OPMULTI" -> tokenType =
                    TokenType.OP_MULTI;

                case "OPSINGLE" -> tokenType =
                    TokenType.OP_SINGLE;

                case "PUNCT" -> tokenType =
                    TokenType.PUNCTUATION;

                case "ID" -> {
                    // Keywords için büyük harfe çevir
                    String upperValue = value.toUpperCase();

                    if (Keyword.contains(upperValue)) {
                        tokenType = TokenType.KEYWORD;
                    }
                    // Entity ve Bool için orijinal değeri kullan (case-sensitive)
                    else if (EntityLiteral.contains(value)) {
                        tokenType = TokenType.ENTITY_LITERAL;
                    }
                    else if (BoolLiteral.contains(value)) {
                        tokenType = TokenType.BOOL_LITERAL;
                    }
                    else {
                        tokenType = TokenType.IDENTIFIER;
                    }
                }

                case "MISMATCH" -> throw new RuntimeException(
                    String.format(
                        "Unexpected character '%s' at line %d column %d",
                        value,
                        lineNum,
                        column
                    )
                );

                default -> throw new RuntimeException(
                    "Unknown token type."
                );
            }

            tokens.add(
                new Token(
                    tokenType,
                    value,
                    lineNum,
                    column
                )
            );
        }

        return tokens;
    }

    // =================================================
    // DETERMINE TOKEN TYPE
    // =================================================
    private String determineTokenType(Matcher matcher) {

        if (matcher.group("COMMENT") != null)
            return "COMMENT";

        if (matcher.group("STRING") != null)
            return "STRING";

        if (matcher.group("INT") != null)
            return "INT";

        if (matcher.group("OPMULTI") != null)
            return "OPMULTI";

        if (matcher.group("PUNCT") != null)
            return "PUNCT";

        if (matcher.group("OPSINGLE") != null)
            return "OPSINGLE";

        if (matcher.group("ID") != null)
            return "ID";

        if (matcher.group("WHITESPACE") != null)
            return "WHITESPACE";

        return "MISMATCH";
    }
}