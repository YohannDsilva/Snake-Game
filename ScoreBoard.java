// ─────────────────────────────────────────────
//  Score tracker
// ─────────────────────────────────────────────
class ScoreBoard {
    private int score = 0;
    private int highScore = 0;
    private static final int POINTS_PER_FOOD = 10;

    void addPoint() {
        score += POINTS_PER_FOOD;
        if (score > highScore) highScore = score;
    }

    void addGolden() {
        score += GoldenApple.POINTS;
        if (score > highScore) highScore = score;
    }

    void reset() {
        score = 0;
    }

    int getScore() { return score; }
    int getHighScore() { return highScore; }
}
