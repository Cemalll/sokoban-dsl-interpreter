import java.util.List;

public class Part2TestRunner {

    private static class TestCase {
        final String title;
        final String code;
        final boolean printLexer;
        final boolean printAst;
        final boolean runTypeChecker;
        final boolean runInterpreter;

        TestCase(String title, String code, boolean printLexer, boolean printAst,
                 boolean runTypeChecker, boolean runInterpreter) {
            this.title = title;
            this.code = code;
            this.printLexer = printLexer;
            this.printAst = printAst;
            this.runTypeChecker = runTypeChecker;
            this.runInterpreter = runInterpreter;
        }
    }

    public static void main(String[] args) {
        TestCase[] tests = new TestCase[] {
                // ==========================================
                // GEÇERLİ 3 ÖRNEK PROGRAM (PART 2 ÇIKTILARI İÇİN)
                // ==========================================
                new TestCase(
                        "VALID 1 - Border Generation (Loops, Place, Validate)",
                        """
                        level "Border Generation" {
                            var int width = 10;
                            var int height = 10;
                        
                            for (i in 0 to width) {
                                place WALL at (0, i);
                                place WALL at (height - 1, i);
                            }
                        
                            for (j in 1 to height - 1) {
                                place WALL at (j, 0);
                                place WALL at (j, width - 1);
                            }
                        
                            var entity p = PLAYER;
                            place p at (5, 5);
                        
                            validate "Player initialized": p == PLAYER;
                        }
                        """,
                        false, false, true, true
                ),
                new TestCase(
                        "VALID 2 - Box Array Placement (Functions, Arrays, Logic)",
                        """
                        func bool isSafe(int r, int c, int maxBound) {
                            if (r > 0 && r < maxBound && c > 0 && c < maxBound) {
                                return true;
                            } else {
                                return false;
                            }
                        }
                        
                        level "Box Array Placement" {
                            var int mapSize = 8;
                            var entity[] boxes = [BOX, BOX, BOX];
                        
                            for (i in 0 to 2) {
                                var int row = i + 2;
                                var int col = 4;
                        
                                if (isSafe(row, col, mapSize) == true) {
                                    place boxes[i] at (row, col);
                                }
                            }
                            print "Boxes are placed safely.";
                        }
                        """,
                        false, false, true, true
                ),
                new TestCase(
                        "VALID 3 - Manhattan Distance & Validation",
                        """
                        func int calcDistance(int x1, int y1, int x2, int y2) {
                            var int dx = x1 - x2;
                            var int dy = y1 - y2;
                            
                            if (dx < 0) { dx = 0 - dx; }
                            if (dy < 0) { dy = 0 - dy; }
                            
                            return dx + dy;
                        }
                        
                        level "Distance Check" {
                            var int pX = 5;
                            var int pY = 5;
                            var int tX = 8;
                            var int tY = 2;
                        
                            place PLAYER at (pX, pY);
                            place TARGET at (tX, tY);
                        
                            var int dist = calcDistance(pX, pY, tX, tY);
                            
                            if (dist > 0) {
                                print "Target is placed successfully. Distance:";
                                print dist;
                            }
                        
                            validate "Distance limit": dist < 10;
                        }
                        """,
                        false, false, true, true
                ),

                // ==========================================
                // TİP HATASI (TYPE CHECKER TESTİ)
                // ==========================================
                new TestCase(
                        "TYPE ERROR 1 - Assign string to int",
                        """
                        level "Bad Type 1" {
                            var int x = "Bu bir string";
                            print x;
                        }
                        """,
                        false, false, true, true
                ),

                // ==========================================
                // ÇALIŞMA ZAMANI HATALARI (RUNTIME ERRORS)
                // ==========================================
                new TestCase(
                        "RUNTIME ERROR 1 - Division by zero",
                        """
                        level "Runtime Bad 1" {
                            var int x = 10 / 0;
                            print x;
                        }
                        """,
                        false, false, true, true
                ),
                new TestCase(
                        "RUNTIME ERROR 2 - Index out of range",
                        """
                        level "Runtime Bad 2" {
                            var entity[] boxes = [BOX, TARGET];
                            place boxes[5] at (2, 2);
                        }
                        """,
                        false, false, true, true
                ),
                new TestCase(
                        "RUNTIME ERROR 3 - Domain-Specific (Grid Out Of Bounds)",
                        """
                        level "Runtime Bad 3" {
                            var entity p = PLAYER;
                            place p at (-1, 25);
                        }
                        """,
                        false, false, true, true
                )
        };

        for (TestCase test : tests) {
            runTest(test);
        }
    }

    private static void runTest(TestCase test) {
        System.out.println("\n============================================================");
        System.out.println(test.title);
        System.out.println("============================================================");
        System.out.println("\n--- SOURCE CODE ---");
        System.out.println(test.code);

        try {
            Lexer lexer = new Lexer(test.code);
            List<Token> tokens = lexer.tokenize();

            if (test.printLexer) {
                System.out.println("--- LEXER OUTPUT ---");
                for (Token token : tokens) {
                    System.out.println(token);
                }
            }

            Parser parser = new Parser(tokens);
            AstNode ast = parser.parseProgram();

            if (test.printAst) {
                System.out.println("--- AST OUTPUT ---");
                ast.print();
            }

            if (test.runTypeChecker) {
                System.out.println("--- TYPE CHECKER ---");
                TypeChecker checker = new TypeChecker();
                checker.check(ast);
                System.out.println("Type checking successful.");
            }

            if (test.runInterpreter) {
                System.out.println("--- INTERPRETER ---");
                Interpreter interpreter = new Interpreter();
                interpreter.interpret(ast);

                // NOT: Eğer Interpreter sınıfı oyun bittiğinde haritayı otomatik yazdırmıyorsa,
                // arkadaşınızın yazdığı kodda o metodu burada manuel çağırabilirsiniz.
                // Örn: interpreter.getRuntimeState().printMap();
            }
        } catch (Errors.SokobanRuntimeException e) {
            System.out.println("--- RUNTIME ERROR CAUGHT ---");
            System.out.println(e.getMessage());
        } catch (RuntimeException e) {
            System.out.println("--- ERROR CAUGHT ---");
            System.out.println(e.getMessage());
        }
    }
}