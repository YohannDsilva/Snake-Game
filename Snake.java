import java.util.ArrayDeque;
import java.util.Deque;

// ─────────────────────────────────────────────
//  Snake
//  Manages the snake's body, direction, and movement logic.
//
//  The body is stored as a Deque<Cell> (double-ended queue):
//    - Front (index 0) = head
//    - Back (last)     = tail
//  Each move adds a new head to the front and removes the tail from the back.
//  When the snake eats food, the tail removal is skipped for one tick — making it grow.
// ─────────────────────────────────────────────
class Snake {

    private final Deque<Cell> body = new ArrayDeque<>();

    private Direction direction        = Direction.RIGHT; // current movement direction
    private Direction pendingDirection = Direction.RIGHT; // direction queued for next tick

    // When true, the tail is kept on the next move so the snake grows by one cell.
    private boolean grew = false;

    // Builds the snake at the given position, length 3, facing right.
    // addLast() is used so the Deque order matches visual order: head first, tail last.
    Snake(int startX, int startY) {
        body.addLast(new Cell(startX,     startY)); // head
        body.addLast(new Cell(startX - 1, startY)); // middle
        body.addLast(new Cell(startX - 2, startY)); // tail
    }

    // Queues a direction change for the next tick.
    // Ignores the request if it would reverse the snake into itself (e.g. RIGHT → LEFT).
    void setDirection(Direction d) {
        if (!d.isOpposite(direction)) {
            pendingDirection = d;
        }
    }

    // Advances the snake by one cell in the current direction.
    // Returns false (game over) if the move hits a wall or the snake's own body.
    boolean move(int cols, int rows) {
        direction = pendingDirection; // apply the queued direction change

        // Calculate where the new head will land
        Cell head = body.peekFirst();
        int nx = head.x + direction.dx;
        int ny = head.y + direction.dy;

        // Wall collision — new head is outside the grid bounds
        if (nx < 0 || nx >= cols || ny < 0 || ny >= rows) return false;

        Cell newHead = new Cell(nx, ny);

        // Self-collision check.
        // The tail tip is excluded when the snake is NOT growing, because that cell
        // moves away on this same tick — including it would cause false deaths on tight turns.
        Cell tail = grew ? null : body.peekLast();
        for (Cell c : body) {
            if (c == tail) continue; // this cell is about to vacate — skip it
            if (c.equals(newHead)) return false; // head overlaps body — game over
        }

        body.addFirst(newHead); // place new head at the front

        if (grew) {
            grew = false;       // growth consumed — tail stays, snake is one cell longer
        } else {
            body.removeLast();  // normal move — remove tail to keep the same length
        }
        return true;
    }

    // Sets the grow flag. On the next move() call, the tail won't be removed.
    void grow() {
        grew = true;
    }

    Cell getHead()         { return body.peekFirst(); }
    Deque<Cell> getBody()  { return body; }
    int getLength()        { return body.size(); }
}
