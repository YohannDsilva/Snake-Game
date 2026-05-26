import java.util.Random;

// ─────────────────────────────────────────────
//  Golden Apple — special food, timed spawn/despawn
// ─────────────────────────────────────────────
class GoldenApple extends Food {
    static final int POINTS = 40;
    boolean active = false;           // visible on board?
    float despawnProgress = 0f;       // 0→1 as it ages (used for flicker)

    // Called each game tick when active to age the apple
    void tick(float fraction) {
        if (active) despawnProgress = Math.min(1f, despawnProgress + fraction);
    }

    void activate(int cols, int rows, Snake snake, java.util.List<Food> others) {
        active = spawn(cols, rows, snake, others);
        despawnProgress = 0f;
    }

    void deactivate() {
        active = false;
        position = null;
    }
}
