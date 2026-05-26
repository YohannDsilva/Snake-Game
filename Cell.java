// ─────────────────────────────────────────────
//  Point cell (grid coordinate)
// ─────────────────────────────────────────────
class Cell {
    int x, y;

    Cell(int x, int y) {
        this.x = x;
        this.y = y;
    }

    Cell copy() {
        return new Cell(x, y);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Cell)) return false;
        Cell c = (Cell) o;
        return x == c.x && y == c.y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }
}
