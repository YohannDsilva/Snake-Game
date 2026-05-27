// ─────────────────────────────────────────────
//  Direction enum
//  Stores the 4 directions the snake can travel.
//  Each direction holds how much x and y change per step (dx, dy).
// ─────────────────────────────────────────────
enum Direction {

    // Each constant gets a dx and dy value.
    // Note: UP is (0, -1) because y=0 is the TOP of the screen in Java2D —
    // so moving "up" means decreasing y.
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

    final int dx, dy; // movement delta per tick in grid units

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    // Returns true if the given direction would be a 180° reversal.
    // e.g. moving RIGHT, isOpposite(LEFT) = true → that input gets blocked.
    // This prevents the snake from instantly colliding with its own neck.
    boolean isOpposite(Direction other) {
        return this.dx == -other.dx && this.dy == -other.dy;
    }
}
