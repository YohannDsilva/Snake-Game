import java.util.Random;

// ─────────────────────────────────────────────
//  Food
//  Represents one food item on the board.
//  Knows how to place itself in a free grid cell, avoiding
//  the snake's body and any other food already on screen.
// ─────────────────────────────────────────────
class Food {

    Cell position; // current grid location of this food item (null = not on board)

    private static final Random rng = new Random(); // shared across all Food instances

    // Finds a random free cell and places the food there.
    // Returns false if the board is completely full (no valid cell exists).
    //
    // 'others' is the full list of Food objects currently active — passed in so
    // this apple doesn't land on top of another one already on the board.
    boolean spawn(int cols, int rows, Snake snake, java.util.List<Food> others) {

        // Build an explicit list of every unoccupied cell on the grid.
        // This avoids the old random-retry loop that could hang on a full board.
        java.util.List<Cell> free = new java.util.ArrayList<>(cols * rows);

        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                Cell c = new Cell(x, y);

                // Skip if the snake is sitting on this cell
                if (snake.getBody().contains(c)) continue;

                // Skip if another food item is already on this cell
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

        if (free.isEmpty()) return false; // board is full — can't place food

        // Pick a random cell from the free list and place the food there
        position = free.get(rng.nextInt(free.size()));
        return true;
    }
}
