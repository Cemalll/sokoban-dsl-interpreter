public class RuntimeState {

    private final int width;
    private final int height;
    private final String[][] grid;

    public RuntimeState(int width, int height) {
        this.width = width;
        this.height = height;
        this.grid = new String[height][width];
        
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                grid[i][j] = "FLOOR";
            }
        }
    }

    public void placeEntity(Token token, String entity, int row, int col) {
        if (row < 0 || row >= height || col < 0 || col >= width) {
            throw new Errors.GridOutOfBoundsException(token, row, col);
        }

        if (!grid[row][col].equals("FLOOR") && !grid[row][col].equals("TARGET")) {
            throw new Errors.InvalidPlacementException(token, entity, row, col);
        }

        grid[row][col] = entity;
    }

    public String getEntityAt(Token token, int row, int col) {
        if (row < 0 || row >= height || col < 0 || col >= width) {
            throw new Errors.GridOutOfBoundsException(token, row, col);
        }
        return grid[row][col];
    }

    public void printMap() {
        System.out.println("\n=== SOKOBAN LEVEL STATE ===");
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                String e = grid[i][j];
                
                char symbol = switch (e) {
                    case "WALL"   -> '█';
                    case "PLAYER" -> 'P';
                    case "BOX"    -> 'B';
                    case "TARGET" -> 'X';
                    default       -> '.';
                };
                System.out.print(symbol + " ");
            }
            System.out.println();
        }
        System.out.println("===========================\n");
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
}