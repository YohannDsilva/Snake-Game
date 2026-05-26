import java.util.Random;

// ─────────────────────────────────────────────
//  Food model
// ─────────────────────────────────────────────
class Food {
    Cell position;
    private static final Random rng = new Random();

    /** Spawn at a random free cell. Returns false if the board is completely full. */
    boolean spawn(int cols, int rows, Snake snake, java.util.List<Food> others) {
        java.util.List<Cell> free = new java.util.ArrayList<>(cols * rows);
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                Cell c = new Cell(x, y);
                if (snake.getBody().contains(c)) continue;
                boolean occupied = false;
                for (Food f : others) {
                    if (f != this && f.position != null && f.position.equals(c)) {
                        occupied = true;
                        break;
                    }
                }
                if (!occupied) free.add(c);
            }
        }
        if (free.isEmpty()) return false;
        position = free.get(rng.nextInt(free.size()));
        return true;
    }
}
