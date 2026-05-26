// ─────────────────────────────────────────────
//  Direction enum
// ─────────────────────────────────────────────
enum Direction {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

    final int dx, dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    boolean isOpposite(Direction other) {
        return this.dx == -other.dx && this.dy == -other.dy;
    }
}
