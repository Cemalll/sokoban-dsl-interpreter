import java.util.List;

public class Parser {

    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // =====================================================
    // BASIC TOKEN HELPERS
    // =====================================================

    private boolean isAtEnd() {
        return current >= tokens.size();
    }

    private Token peek() {
        if (isAtEnd()) {
            Token last = tokens.get(tokens.size() - 1);
            return new Token(last.type(), "<EOF>", last.line(), last.column());
        }
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type() == type;
    }

    private boolean check(TokenType type, String value) {
        if (isAtEnd()) return false;
        return peek().type() == type && peek().value().equals(value);
    }

    // Lexer bazı keywordleri IDENTIFIER verdiği için ikisini de kabul ediyoruz.
    private boolean checkKeyword(String keyword) {
        if (isAtEnd()) return false;

        return (peek().type() == TokenType.KEYWORD || peek().type() == TokenType.IDENTIFIER)
            && peek().value().equalsIgnoreCase(keyword);
    }

    private boolean match(TokenType type, String value) {
        if (check(type, value)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean matchKeyword(String keyword) {
        if (checkKeyword(keyword)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean matchAnyOperator(String... ops) {
        if (isAtEnd()) return false;

        if (peek().type() != TokenType.OP_SINGLE && peek().type() != TokenType.OP_MULTI) {
            return false;
        }

        for (String op : ops) {
            if (peek().value().equals(op)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        error(message);
        return null;
    }

    private Token consume(TokenType type, String value, String message) {
        if (check(type, value)) return advance();
        error(message);
        return null;
    }

    private Token consumeKeyword(String keyword, String message) {
        if (checkKeyword(keyword)) return advance();
        error(message);
        return null;
    }

    private void error(String message) {
        Token t = peek();
        throw new RuntimeException(
            "Parser error at line " + t.line() +
            ", column " + t.column() +
            ": " + message +
            " | Found: " + t.value()
        );
    }

    // =====================================================
    // PROGRAM
    // =====================================================

    public AstNode parseProgram() {
        AstNode program = new AstNode("Program");

        while (!isAtEnd()) {
            program.addChild(parseDeclaration());
        }

        return program;
    }

    private AstNode parseDeclaration() {

        if (matchKeyword("record")) {
            return parseRecordDecl();
        }

        if (matchKeyword("func")) {
            return parseFuncDecl();
        }

        if (matchKeyword("var")) {
            return parseVarDeclAfterVar();
        }

        if (matchKeyword("level")) {
            return parseLevelDef();
        }

        error("Declaration expected");
        return null;
    }

    // =====================================================
    // TYPE
    // =====================================================

    private AstNode parseType() {
        AstNode typeNode = new AstNode("Type");

        if (matchKeyword("int") ||
            matchKeyword("string") ||
            matchKeyword("bool") ||
            matchKeyword("entity")) {

            typeNode.addChild(new AstNode("BaseType", previous().value()));
            parseArraySuffixes(typeNode);
            return typeNode;
        }

        if (check(TokenType.IDENTIFIER)) {
            Token name = advance();
            typeNode.addChild(new AstNode("CustomType", name.value()));
            parseArraySuffixes(typeNode);
            return typeNode;
        }

        error("Type expected");
        return null;
    }

    private void parseArraySuffixes(AstNode typeNode) {
        while (match(TokenType.PUNCTUATION, "[")) {
            consume(TokenType.PUNCTUATION, "]", "']' expected after '[' in array type");
            typeNode.addChild(new AstNode("ArraySuffix", "[]"));
        }
    }

    // =====================================================
    // RECORD DECLARATION
    // =====================================================

    private AstNode parseRecordDecl() {
        AstNode node = new AstNode("RecordDecl");

        Token name = consume(TokenType.IDENTIFIER, "Record name expected");
        node.addChild(new AstNode("Name", name.value()));

        consume(TokenType.PUNCTUATION, "{", "'{' expected after record name");

        AstNode fields = new AstNode("Fields");

        while (!check(TokenType.PUNCTUATION, "}") && !isAtEnd()) {
            consumeKeyword("var", "Only variable declarations are allowed inside record");
            fields.addChild(parseVarDeclAfterVar());
        }

        node.addChild(fields);

        consume(TokenType.PUNCTUATION, "}", "'}' expected after record body");
        return node;
    }

    // =====================================================
    // VARIABLE DECLARATION
    // =====================================================

    private AstNode parseVarDeclAfterVar() {
        AstNode node = new AstNode("VarDecl");

        node.addChild(parseType());

        Token name = consume(TokenType.IDENTIFIER, "Variable name expected");
        node.addChild(new AstNode("Name", name.value()));

        if (match(TokenType.OP_SINGLE, "=")) {
            AstNode init = new AstNode("Initializer");
            init.addChild(parseExpression());
            node.addChild(init);
        }

        consume(TokenType.PUNCTUATION, ";", "';' expected after variable declaration");
        return node;
    }

    // =====================================================
    // FUNCTION DECLARATION
    // =====================================================

    private AstNode parseFuncDecl() {
        AstNode node = new AstNode("FuncDecl");

        if (matchKeyword("void")) {
            node.addChild(new AstNode("ReturnType", "void"));
        } else {
            AstNode returnType = new AstNode("ReturnType");
            returnType.addChild(parseType());
            node.addChild(returnType);
        }

        Token name = consume(TokenType.IDENTIFIER, "Function name expected");
        node.addChild(new AstNode("Name", name.value()));

        consume(TokenType.PUNCTUATION, "(", "'(' expected after function name");

        AstNode params = new AstNode("Params");
        if (!check(TokenType.PUNCTUATION, ")")) {
            parseParamList(params);
        }
        node.addChild(params);

        consume(TokenType.PUNCTUATION, ")", "')' expected after parameters");

        node.addChild(parseBlock());
        return node;
    }

    private void parseParamList(AstNode params) {
        params.addChild(parseParam());

        while (match(TokenType.PUNCTUATION, ",")) {
            params.addChild(parseParam());
        }
    }

    private AstNode parseParam() {
        AstNode param = new AstNode("Param");
        param.addChild(parseType());
        Token name = consume(TokenType.IDENTIFIER, "Parameter name expected");
        param.addChild(new AstNode("Name", name.value()));
        return param;
    }

    // =====================================================
    // LEVEL
    // =====================================================

    private AstNode parseLevelDef() {
        AstNode node = new AstNode("LevelDef");

        Token name = consume(TokenType.STRING_LITERAL, "Level name string expected");
        node.addChild(new AstNode("Name", name.value()));

        node.addChild(parseBlock());
        return node;
    }

    // =====================================================
    // BLOCK / STATEMENTS
    // =====================================================

    private AstNode parseBlock() {
        AstNode block = new AstNode("Block");

        consume(TokenType.PUNCTUATION, "{", "'{' expected");

        while (!check(TokenType.PUNCTUATION, "}") && !isAtEnd()) {
            block.addChild(parseStatement());
        }

        consume(TokenType.PUNCTUATION, "}", "'}' expected");
        return block;
    }

    private AstNode parseStatement() {

        if (matchKeyword("var")) {
            return parseVarDeclAfterVar();
        }

        if (matchKeyword("if")) {
            return parseIfStmt();
        }

        if (matchKeyword("for")) {
            return parseForStmt();
        }

        if (matchKeyword("return")) {
            return parseReturnStmt();
        }

        if (matchKeyword("place")) {
            return parsePlaceStmt();
        }

        if (matchKeyword("validate")) {
            return parseValidateStmt();
        }

        if (matchKeyword("print")) {
            return parsePrintStmt();
        }

        if (check(TokenType.PUNCTUATION, "{")) {
            return parseBlock();
        }

        return parseIdentifierStmt();
    }

    private AstNode parseIdentifierStmt() {
        AstNode node = new AstNode("IdentifierStmt");

        Token name = consume(TokenType.IDENTIFIER, "Identifier expected");
        AstNode usage = new AstNode("IdentifierUsage", name.value());
        parsePostfixOps(usage);
        node.addChild(usage);

        if (match(TokenType.OP_SINGLE, "=")) {
            AstNode assign = new AstNode("Assignment");
            assign.addChild(parseExpression());
            node.addChild(assign);
        }

        consume(TokenType.PUNCTUATION, ";", "';' expected after identifier statement");
        return node;
    }

    // =====================================================
    // IF / FOR / RETURN / PRINT
    // =====================================================

    private AstNode parseIfStmt() {
        AstNode node = new AstNode("IfStmt");

        consume(TokenType.PUNCTUATION, "(", "'(' expected after if");
        AstNode condition = new AstNode("Condition");
        condition.addChild(parseExpression());
        node.addChild(condition);
        consume(TokenType.PUNCTUATION, ")", "')' expected after if condition");

        AstNode thenBlock = new AstNode("Then");
        thenBlock.addChild(parseBlock());
        node.addChild(thenBlock);

        if (matchKeyword("else")) {
            AstNode elseBlock = new AstNode("Else");
            elseBlock.addChild(parseBlock());
            node.addChild(elseBlock);
        }

        return node;
    }

    private AstNode parseForStmt() {
        AstNode node = new AstNode("ForStmt");

        consume(TokenType.PUNCTUATION, "(", "'(' expected after for");

        Token loopVar = consume(TokenType.IDENTIFIER, "Loop variable expected");
        node.addChild(new AstNode("LoopVariable", loopVar.value()));

        consumeKeyword("in", "'in' expected in for statement");

        AstNode start = new AstNode("Start");
        start.addChild(parseExpression());
        node.addChild(start);

        consumeKeyword("to", "'to' expected in for statement");

        AstNode end = new AstNode("End");
        end.addChild(parseExpression());
        node.addChild(end);

        consume(TokenType.PUNCTUATION, ")", "')' expected after for statement");

        node.addChild(parseBlock());
        return node;
    }

    private AstNode parseReturnStmt() {
        AstNode node = new AstNode("ReturnStmt");

        if (!check(TokenType.PUNCTUATION, ";")) {
            node.addChild(parseExpression());
        }

        consume(TokenType.PUNCTUATION, ";", "';' expected after return statement");
        return node;
    }

    private AstNode parsePrintStmt() {
        AstNode node = new AstNode("PrintStmt");
        node.addChild(parseExpression());
        consume(TokenType.PUNCTUATION, ";", "';' expected after print statement");
        return node;
    }

    // =====================================================
    // DOMAIN STATEMENTS
    // =====================================================

    private AstNode parsePlaceStmt() {
        AstNode node = new AstNode("PlaceStmt");

        AstNode entity = new AstNode("Entity");
        entity.addChild(parseExpression());
        node.addChild(entity);

        consumeKeyword("at", "'at' expected in place statement");

        consume(TokenType.PUNCTUATION, "(", "'(' expected after at");

        AstNode row = new AstNode("Row");
        row.addChild(parseExpression());
        node.addChild(row);

        consume(TokenType.PUNCTUATION, ",", "',' expected between row and column");

        AstNode col = new AstNode("Column");
        col.addChild(parseExpression());
        node.addChild(col);

        consume(TokenType.PUNCTUATION, ")", "')' expected after coordinates");
        consume(TokenType.PUNCTUATION, ";", "';' expected after place statement");

        return node;
    }

    private AstNode parseValidateStmt() {
        AstNode node = new AstNode("ValidateStmt");

        Token msg = consume(TokenType.STRING_LITERAL, "Validation message expected");
        node.addChild(new AstNode("Message", msg.value()));

        consume(TokenType.PUNCTUATION, ":", "':' expected after validation message");

        AstNode expr = new AstNode("Expression");
        expr.addChild(parseExpression());
        node.addChild(expr);

        consume(TokenType.PUNCTUATION, ";", "';' expected after validate statement");
        return node;
    }

    // =====================================================
    // EXPRESSIONS
    // =====================================================

    private AstNode parseExpression() {
        return parseLogicalOr();
    }

    private AstNode parseLogicalOr() {
        AstNode left = parseLogicalAnd();

        while (matchAnyOperator("||")) {
            Token op = previous();
            AstNode node = new AstNode("BinaryExpr", op.value());
            node.addChild(left);
            node.addChild(parseLogicalAnd());
            left = node;
        }

        return left;
    }

    private AstNode parseLogicalAnd() {
        AstNode left = parseEquality();

        while (matchAnyOperator("&&")) {
            Token op = previous();
            AstNode node = new AstNode("BinaryExpr", op.value());
            node.addChild(left);
            node.addChild(parseEquality());
            left = node;
        }

        return left;
    }

    private AstNode parseEquality() {
        AstNode left = parseRelational();

        while (matchAnyOperator("==", "!=")) {
            Token op = previous();
            AstNode node = new AstNode("BinaryExpr", op.value());
            node.addChild(left);
            node.addChild(parseRelational());
            left = node;
        }

        return left;
    }

    private AstNode parseRelational() {
        AstNode left = parseAddition();

        while (matchAnyOperator("<", ">", "<=", ">=")) {
            Token op = previous();
            AstNode node = new AstNode("BinaryExpr", op.value());
            node.addChild(left);
            node.addChild(parseAddition());
            left = node;
        }

        return left;
    }

    private AstNode parseAddition() {
        AstNode left = parseMultiplication();

        while (matchAnyOperator("+", "-")) {
            Token op = previous();
            AstNode node = new AstNode("BinaryExpr", op.value());
            node.addChild(left);
            node.addChild(parseMultiplication());
            left = node;
        }

        return left;
    }

    private AstNode parseMultiplication() {
        AstNode left = parseUnary();

        while (matchAnyOperator("*", "/", "%")) {
            Token op = previous();
            AstNode node = new AstNode("BinaryExpr", op.value());
            node.addChild(left);
            node.addChild(parseUnary());
            left = node;
        }

        return left;
    }

    private AstNode parseUnary() {
        if (matchAnyOperator("!", "-")) {
            Token op = previous();
            AstNode node = new AstNode("UnaryExpr", op.value());
            node.addChild(parseUnary());
            return node;
        }

        return parsePrimary();
    }

    private AstNode parsePrimary() {

        if (matchLiteral()) {
            Token lit = previous();
            return new AstNode("Literal", lit.value());
        }

        if (match(TokenType.PUNCTUATION, "[")) {
            AstNode array = new AstNode("ArrayLiteral");

            if (!check(TokenType.PUNCTUATION, "]")) {
                parseArgList(array);
            }

            consume(TokenType.PUNCTUATION, "]", "']' expected after array literal");
            return array;
        }

        if (match(TokenType.PUNCTUATION, "(")) {
            AstNode group = new AstNode("GroupedExpr");
            group.addChild(parseExpression());
            consume(TokenType.PUNCTUATION, ")", "')' expected after expression");
            return group;
        }

        if (check(TokenType.IDENTIFIER)) {
            Token name = advance();
            AstNode usage = new AstNode("IdentifierUsage", name.value());
            parsePostfixOps(usage);
            return usage;
        }

        error("Expression expected");
        return null;
    }

    private boolean matchLiteral() {
        if (check(TokenType.INT_LITERAL) ||
            check(TokenType.STRING_LITERAL) ||
            check(TokenType.BOOL_LITERAL) ||
            check(TokenType.ENTITY_LITERAL)) {
            advance();
            return true;
        }

        return false;
    }

    // =====================================================
    // POSTFIX + ARGUMENTS
    // =====================================================

    private void parsePostfixOps(AstNode usage) {
        while (true) {
            if (match(TokenType.PUNCTUATION, "[")) {
                AstNode index = new AstNode("IndexAccess");
                index.addChild(parseExpression());
                consume(TokenType.PUNCTUATION, "]", "']' expected after index expression");
                usage.addChild(index);
            }
            else if (match(TokenType.PUNCTUATION, ".")) {
                Token field = consume(TokenType.IDENTIFIER, "Identifier expected after '.'");
                usage.addChild(new AstNode("FieldAccess", field.value()));
            }
            else if (match(TokenType.PUNCTUATION, "(")) {
                AstNode call = new AstNode("Call");

                if (!check(TokenType.PUNCTUATION, ")")) {
                    parseArgList(call);
                }

                consume(TokenType.PUNCTUATION, ")", "')' expected after arguments");
                usage.addChild(call);
            }
            else {
                break;
            }
        }
    }

    private void parseArgList(AstNode parent) {
        parent.addChild(parseExpression());

        while (match(TokenType.PUNCTUATION, ",")) {
            parent.addChild(parseExpression());
        }
    }
}