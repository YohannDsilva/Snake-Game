// ─────────────────────────────────────────────
//  ScoreBoard
//  Tracks the current round score and the all-time high score.
//  The high score persists across restarts for the whole session —
//  it only resets when the program is closed.
// ─────────────────────────────────────────────
class ScoreBoard {

    private int score     = 0; // points earned this round
    private int highScore = 0; // best score ever seen this session

    private static final int POINTS_PER_FOOD = 10; // regular apple value

    // Called when the snake eats a regular (red) apple
    void addPoint() {
        score += POINTS_PER_FOOD;
        if (score > highScore) highScore = score; // update high score if beaten
    }

    // Called when the snake eats the golden apple — worth more points
    void addGolden() {
        score += GoldenApple.POINTS; // 40 points
        if (score > highScore) highScore = score;
    }

    // Called at the start of each new game — resets round score only, keeps high score
    void reset() {
        score = 0;
    }

    int getScore()     { return score; }
    int getHighScore() { return highScore; }
}
