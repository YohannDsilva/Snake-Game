import java.util.Random;

// ─────────────────────────────────────────────
//  GoldenApple
//  A special food item that appears occasionally and is worth more points.
//  Extends Food to reuse the safe free-cell spawn logic.
//
//  Lifecycle:
//    Inactive → activate() → ticking down → deactivate() (eaten or expired)
//    After deactivation a countdown in GamePanel waits before the next activate().
// ─────────────────────────────────────────────
class GoldenApple extends Food {

    static final int POINTS = 40; // points awarded when the snake eats this

    boolean active = false;     // true while the apple is visible on the board
    float despawnProgress = 0f; // goes 0 → 1 over the apple's lifespan
                                // used by the renderer to trigger the blink effect
                                // when it gets close to expiring

    // Called every game tick while the apple is active.
    // 'fraction' = how much of the total lifespan one tick represents.
    // e.g. with GOLDEN_DESPAWN_TICKS = 60, fraction = 1/60 per tick.
    void tick(float fraction) {
        if (active) despawnProgress = Math.min(1f, despawnProgress + fraction);
    }

    // Places the apple on the board and marks it as active.
    // Calls the inherited Food.spawn() to safely find a free cell.
    // Sets active = false if the board is full and no cell could be found.
    void activate(int cols, int rows, Snake snake, java.util.List<Food> others) {
        active = spawn(cols, rows, snake, others);
        despawnProgress = 0f; // reset age so the blink timer starts fresh
    }

    // Removes the apple from the board — called when eaten OR when time runs out.
    void deactivate() {
        active = false;
        position = null; // clear position so it's not drawn or collision-checked
    }
}
