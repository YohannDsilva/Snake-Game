// ─────────────────────────────────────────────
//  Cell
//  A single grid coordinate — just an x (column) and y (row).
//  Used throughout the program to represent positions:
//  snake body segments, food locations, new head positions, etc.
// ─────────────────────────────────────────────
class Cell {
    int x, y;

    Cell(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // Returns a new Cell with the same coordinates.
    // Useful when you need an independent copy rather than a shared reference.
    Cell copy() {
        return new Cell(x, y);
    }

    // Two Cells are considered equal if they share the same x and y.
    // Java requires this override so that .equals() and .contains() work correctly
    // when checking whether the snake occupies a given grid position.
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Cell)) return false;
        Cell c = (Cell) o;
        return x == c.x && y == c.y;
    }

    // hashCode must be consistent with equals.
    // Required so Cells behave correctly inside collections (Deque, ArrayList, etc.)
    // when using methods like .contains().
    @Override
    public int hashCode() {
        return 31 * x + y;
    }
}
