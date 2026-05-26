import java.util.ArrayDeque;
import java.util.Deque;

// ─────────────────────────────────────────────
//  Snake model
// ─────────────────────────────────────────────
class Snake {
    private final Deque<Cell> body = new ArrayDeque<>();
    private Direction direction = Direction.RIGHT;
    private Direction pendingDirection = Direction.RIGHT;
    private boolean grew = false;

    Snake(int startX, int startY) {
        // Head at startX, body trailing to the left
        body.addLast(new Cell(startX,     startY));
        body.addLast(new Cell(startX - 1, startY));
        body.addLast(new Cell(startX - 2, startY));
    }

    /** Queue a direction change (prevents 180° reversal) */
    void setDirection(Direction d) {
        if (!d.isOpposite(direction)) {
            pendingDirection = d;
        }
    }

    /** Advance the snake by one cell; returns false on self-collision */
    boolean move(int cols, int rows) {
        direction = pendingDirection;
        Cell head = body.peekFirst();
        int nx = head.x + direction.dx;
        int ny = head.y + direction.dy;

        // Wall collision
        if (nx < 0 || nx >= cols || ny < 0 || ny >= rows) return false;

        Cell newHead = new Cell(nx, ny);

        // Self-collision: exclude the tail tip when not growing,
        // because it will vacate this tick before the new head arrives.
        Cell tail = grew ? null : body.peekLast();
        for (Cell c : body) {
            if (c == tail) continue; // same object reference — this is the tip that moves away
            if (c.equals(newHead)) return false;
        }

        body.addFirst(newHead);

        if (grew) {
            grew = false; // keep tail
        } else {
            body.removeLast();
        }
        return true;
    }

    void grow() {
        grew = true;
    }

    Cell getHead() {
        return body.peekFirst();
    }

    Deque<Cell> getBody() {
        return body;
    }

    int getLength() {
        return body.size();
    }
}
