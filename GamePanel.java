import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

// ─────────────────────────────────────────────
//  Main game panel (rendering + logic + input)
// ─────────────────────────────────────────────
class GamePanel extends JPanel implements ActionListener, KeyListener {

    // ── Grid / sizing ──────────────────────────
    private static final int COLS = 28;
    private static final int ROWS = 22;
    private static final int CELL = 26;
    private static final int HUD  = 64;
    private static final int W    = COLS * CELL;
    private static final int H    = ROWS * CELL + HUD;

    // ── Palette ────────────────────────────────
    private static final Color BG          = new Color(10,  10,  18);
    private static final Color GRID        = new Color(22,  24,  38);
    private static final Color HUD_BG      = new Color(14,  14,  26);
    private static final Color SNAKE_HEAD  = new Color(80, 255, 160);
    private static final Color SNAKE_BODY  = new Color(40, 200, 110);
    private static final Color SNAKE_DARK  = new Color(20, 140,  70);
    private static final Color FOOD_COLOR  = new Color(255,  80,  80);
    private static final Color GOLD_COLOR   = new Color(255, 215,  0);
    private static final Color GOLD_GLOW    = new Color(255, 200,  0, 70);
    private static final Color FOOD_GLOW   = new Color(255,  80,  80, 60);
    private static final Color SCORE_COLOR = new Color(200, 255, 220);
    private static final Color HI_COLOR    = new Color(255, 215,  80);
    private static final Color OVERLAY_BG  = new Color(0,   0,   0, 170);
    private static final Color BTN_NORMAL  = new Color(80, 255, 160);
    private static final Color BTN_HOVER   = new Color(140, 255, 200);

    // ── Game state ─────────────────────────────
    enum State { READY, PLAYING, PAUSED, DYING, GAME_OVER }

    private static final int FOOD_COUNT = 5;

    // ── Difficulty ─────────────────────────────
    enum Difficulty {
        EASY(240), NORMAL(180), HARD(100);
        final int delay;
        Difficulty(int delay) { this.delay = delay; }
    }
    private Difficulty difficulty = Difficulty.NORMAL;

    private State state = State.READY;
    private Snake snake;
    private final java.util.List<Food> foods = new java.util.ArrayList<>();
    private final ScoreBoard score = new ScoreBoard();
    private final Timer timer;
    private int tickDelay = Difficulty.NORMAL.delay;

    // ── Button rects ───────────────────────────
    private final Rectangle btnStart    = new Rectangle();
    private final Rectangle btnRestart  = new Rectangle();
    private final Rectangle btnEasy     = new Rectangle();
    private final Rectangle btnNormal   = new Rectangle();
    private final Rectangle btnHard     = new Rectangle();
    private boolean hoverStart   = false;
    private boolean hoverRestart = false;
    private boolean hoverEasy    = false;
    private boolean hoverNormal  = false;
    private boolean hoverHard    = false;

    // ── Flash animation on food eat ────────────
    private int flashTick = 0;

    // ── Death animation ────────────────────────
    private int deathTick = 0;
    private static final int DEATH_FRAMES = 7; // frames before GAME_OVER
    private Timer deathTimer;

    // ── Golden apple ───────────────────────────
    private final GoldenApple goldenApple = new GoldenApple();
    private static final int  GOLDEN_SPAWN_TICKS   = 120; // game ticks between spawns
    private static final int  GOLDEN_DESPAWN_TICKS = 60;  // game ticks before it vanishes
    private int goldenSpawnCountdown  = GOLDEN_SPAWN_TICKS;
    private int goldenDespawnCountdown = 0;

    // ── Constructor ────────────────────────────
    GamePanel() {
        setPreferredSize(new Dimension(W, H));
        setBackground(BG);
        setFocusable(true);
        addKeyListener(this);

        timer = new Timer(Difficulty.NORMAL.delay, this);
        // Death animation timer fires every 80 ms for DEATH_FRAMES ticks
        deathTimer = new Timer(80, ev -> {
            deathTick++;
            if (deathTick >= DEATH_FRAMES) {
                deathTimer.stop();
                state = State.GAME_OVER;
            }
            repaint();
        });

        // Mouse for buttons
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (state == State.READY && btnStart.contains(e.getPoint()))       startGame();
                if (state == State.GAME_OVER && btnRestart.contains(e.getPoint())) startGame();
                if (state == State.READY || state == State.GAME_OVER) {
                    if (btnEasy.contains(e.getPoint()))   { difficulty = Difficulty.EASY;   repaint(); }
                    if (btnNormal.contains(e.getPoint())) { difficulty = Difficulty.NORMAL; repaint(); }
                    if (btnHard.contains(e.getPoint()))   { difficulty = Difficulty.HARD;   repaint(); }
                }
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                hoverStart   = btnStart.contains(e.getPoint());
                hoverRestart = btnRestart.contains(e.getPoint());
                hoverEasy    = btnEasy.contains(e.getPoint());
                hoverNormal  = btnNormal.contains(e.getPoint());
                hoverHard    = btnHard.contains(e.getPoint());
                repaint();
            }
        });
    }

    // ── Game lifecycle ─────────────────────────
    private void togglePause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
            timer.stop();
        } else if (state == State.PAUSED) {
            state = State.PLAYING;
            timer.start();
            requestFocusInWindow();
        }
        repaint();
    }

    private void startGame() {
        score.reset();
        tickDelay = difficulty.delay;
        timer.setDelay(tickDelay);
        snake = new Snake(COLS / 2, ROWS / 2);
        foods.clear();
        for (int i = 0; i < FOOD_COUNT; i++) {
            Food f = new Food();
            f.spawn(COLS, ROWS, snake, foods);
            foods.add(f);
        }
        flashTick = 0;
        goldenApple.deactivate();
        goldenSpawnCountdown  = GOLDEN_SPAWN_TICKS;
        goldenDespawnCountdown = 0;
        state = State.PLAYING;
        timer.start();
        requestFocusInWindow();
        repaint();
    }

    // ── Timer tick (game loop) ─────────────────
    @Override
    public void actionPerformed(ActionEvent e) {
        if (state != State.PLAYING) return;

        if (flashTick > 0) flashTick--;

        boolean alive = snake.move(COLS, ROWS);
        if (!alive) {
            timer.stop();
            state = State.DYING;
            deathTick = 0;
            deathTimer.start();
            repaint();
            return;
        }

        // Check food
        Cell head = snake.getHead();
        for (Food f : foods) {
            if (head.equals(f.position)) {
                snake.grow();
                score.addPoint();
                f.spawn(COLS, ROWS, snake, foods); // safe respawn
                flashTick = 4;

                // Speed up slightly every 50 points, down to a floor of 80ms
                if (score.getScore() % 50 == 0 && tickDelay > 80) {
                    tickDelay -= 6;
                    timer.setDelay(tickDelay);
                }
                break;
            }
        }

        // ── Golden apple logic ──────────────────
        if (goldenApple.active) {
            goldenDespawnCountdown--;
            // Tick despawn progress for flicker rendering (progress 0→1 over lifespan)
            goldenApple.tick(1f / GOLDEN_DESPAWN_TICKS);
            if (goldenDespawnCountdown <= 0) {
                goldenApple.deactivate();
                goldenSpawnCountdown = GOLDEN_SPAWN_TICKS;
            } else if (snake.getHead().equals(goldenApple.position)) {
                // Snake ate the golden apple
                snake.grow();
                score.addGolden();
                goldenApple.deactivate();
                goldenSpawnCountdown = GOLDEN_SPAWN_TICKS;
            }
        } else {
            goldenSpawnCountdown--;
            if (goldenSpawnCountdown <= 0) {
                java.util.List<Food> allFoods = new java.util.ArrayList<>(foods);
                allFoods.add(goldenApple);
                goldenApple.activate(COLS, ROWS, snake, allFoods);
                goldenDespawnCountdown = GOLDEN_DESPAWN_TICKS;
            }
        }

        repaint();
    }

    // ── Rendering ──────────────────────────────
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawHUD(g);
        drawGrid(g);
        if (state != State.READY) {
            drawFood(g);
            drawGoldenApple(g);
            drawSnake(g);
        }

        if (state == State.DYING) drawDyingOverlay(g);

        if (state == State.READY)      drawReadyOverlay(g);
        if (state == State.PAUSED)     drawPausedOverlay(g);
        if (state == State.GAME_OVER)  drawGameOverOverlay(g);
    }

    private void drawHUD(Graphics2D g) {
        g.setColor(HUD_BG);
        g.fillRect(0, 0, W, HUD);

        // Separator line
        g.setColor(SNAKE_DARK);
        g.fillRect(0, HUD - 2, W, 2);

        // SCORE label
        g.setFont(pixelFont(11f));
        g.setColor(new Color(100, 130, 110));
        g.drawString("SCORE", 28, 24);
        g.setFont(pixelFont(22f));
        g.setColor(SCORE_COLOR);
        g.drawString(String.valueOf(score.getScore()), 28, 50);

        // BEST label
        g.setFont(pixelFont(11f));
        g.setColor(new Color(130, 120, 60));
        String bestLabel = "BEST";
        int bx = W - 28 - g.getFontMetrics(pixelFont(22f)).stringWidth(String.valueOf(score.getHighScore()));
        g.drawString(bestLabel, bx, 24);
        g.setFont(pixelFont(22f));
        g.setColor(HI_COLOR);
        g.drawString(String.valueOf(score.getHighScore()), bx, 50);

        // SNAKE title center
        g.setFont(pixelFont(20f));
        g.setColor(SNAKE_HEAD);
        String title = "SNAKE";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (W - fm.stringWidth(title)) / 2, 42);
    }

    private void drawGrid(Graphics2D g) {
        int top = HUD;
        g.setColor(BG);
        g.fillRect(0, top, W, ROWS * CELL);

        g.setColor(GRID);
        for (int x = 0; x <= COLS; x++)
            g.drawLine(x * CELL, top, x * CELL, top + ROWS * CELL);
        for (int y = 0; y <= ROWS; y++)
            g.drawLine(0, top + y * CELL, W, top + y * CELL);
    }

    private void drawFood(Graphics2D g) {
        for (int i = 0; i < foods.size(); i++) {
            Food food = foods.get(i);
            if (food.position == null) continue;
            Cell f = food.position;
            int px = f.x * CELL;
            int py = HUD + f.y * CELL;
            int pad = 4;

            // Glow
            RadialGradientPaint glow = new RadialGradientPaint(
                px + CELL / 2f, py + CELL / 2f, CELL,
                new float[]{0f, 1f},
                new Color[]{FOOD_GLOW, new Color(0, 0, 0, 0)}
            );
            g.setPaint(glow);
            g.fillOval(px - CELL / 2, py - CELL / 2, CELL * 2, CELL * 2);

            // Apple body — flash only the most recently eaten slot (index 0 convention via flashTick)
            g.setColor(flashTick > 0 && i == 0 ? new Color(255, 140, 80) : FOOD_COLOR);
            g.fillOval(px + pad, py + pad, CELL - pad * 2, CELL - pad * 2);

            // Shine
            g.setColor(new Color(255, 255, 255, 90));
            g.fillOval(px + pad + 3, py + pad + 3, (CELL - pad * 2) / 3, (CELL - pad * 2) / 3);
        }
    }

    private void drawGoldenApple(Graphics2D g) {
        if (!goldenApple.active || goldenApple.position == null) return;

        // Flicker as it nears despawn — blink rapidly in last 25% of life
        float prog = goldenApple.despawnProgress;
        if (prog > 0.75f) {
            // Blink every ~3 frames
            long now = System.currentTimeMillis();
            if ((now / 120) % 2 == 0) return; // skip draw = invisible frame
        }

        Cell f = goldenApple.position;
        int px = f.x * CELL;
        int py = HUD + f.y * CELL;
        int pad = 3;

        // Golden glow (larger than normal apple)
        RadialGradientPaint glow = new RadialGradientPaint(
            px + CELL / 2f, py + CELL / 2f, CELL * 1.2f,
            new float[]{0f, 1f},
            new Color[]{GOLD_GLOW, new Color(0, 0, 0, 0)}
        );
        g.setPaint(glow);
        g.fillOval(px - CELL / 2, py - CELL / 2, CELL * 2, CELL * 2);

        // Apple body — gold with slight orange tint
        g.setColor(GOLD_COLOR);
        g.fillOval(px + pad, py + pad, CELL - pad * 2, CELL - pad * 2);

        // Darker gold outline for definition
        g.setColor(new Color(200, 150, 0));
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(px + pad, py + pad, CELL - pad * 2, CELL - pad * 2);
        g.setStroke(new BasicStroke(1f));

        // Bright shine spot
        g.setColor(new Color(255, 255, 180, 160));
        g.fillOval(px + pad + 3, py + pad + 3, (CELL - pad * 2) / 3, (CELL - pad * 2) / 3);

        // Small sparkle star at top-right
        int sx = px + CELL - 7, sy = py + 5;
        g.setColor(new Color(255, 255, 120, 200));
        g.fillOval(sx, sy, 4, 4);
        g.setColor(new Color(255, 255, 200, 150));
        g.drawLine(sx + 2, sy - 3, sx + 2, sy + 7); // vertical
        g.drawLine(sx - 3, sy + 2, sx + 7, sy + 2); // horizontal
    }

    private void drawSnake(Graphics2D g) {
        Deque<Cell> body = snake.getBody();
        Cell[] cells = body.toArray(new Cell[0]);

        boolean dying = (state == State.DYING);
        // Alternate between red-flash and dark on every other death frame
        boolean flashOn = dying && (deathTick % 2 == 0);

        for (int i = cells.length - 1; i >= 0; i--) {
            Cell c = cells[i];
            int px = c.x * CELL;
            int py = HUD + c.y * CELL;
            int pad = 2;
            boolean isHead = (i == 0);

            if (dying) {
                float t = (float) i / cells.length;
                if (flashOn) {
                    // Bright red -> dark red along body
                    g.setColor(blend(new Color(255, 60, 60), new Color(120, 20, 20), t));
                } else {
                    // Dark flash — near-black
                    g.setColor(blend(new Color(80, 20, 20), new Color(30, 10, 10), t));
                }
            } else if (isHead) {
                g.setColor(SNAKE_HEAD);
            } else {
                // Gradient from bright to dark along body
                float t = (float) i / cells.length;
                g.setColor(blend(SNAKE_BODY, SNAKE_DARK, t));
            }
            g.fillRoundRect(px + pad, py + pad, CELL - pad * 2, CELL - pad * 2, 8, 8);

            // Head details: eyes
            if (isHead) {
                drawEyes(g, c);
            }
        }
    }

    private void drawEyes(Graphics2D g, Cell head) {
        Direction d = snake.getBody().size() > 1 ? getSnakeDirection() : Direction.RIGHT;
        int px = head.x * CELL;
        int py = HUD + head.y * CELL;

        int ex1, ey1, ex2, ey2;
        int er = 3;

        switch (d) {
            case RIGHT -> { ex1 = px + CELL - 7; ey1 = py + 6;          ex2 = px + CELL - 7; ey2 = py + CELL - 9; }
            case LEFT  -> { ex1 = px + 4;         ey1 = py + 6;          ex2 = px + 4;         ey2 = py + CELL - 9; }
            case UP    -> { ex1 = px + 6;          ey1 = py + 4;          ex2 = px + CELL - 9;  ey2 = py + 4;        }
            default    -> { ex1 = px + 6;          ey1 = py + CELL - 7;  ex2 = px + CELL - 9;  ey2 = py + CELL - 7; }
        }

        g.setColor(new Color(10, 20, 15));
        g.fillOval(ex1 - er, ey1 - er, er * 2, ey2 == ey1 ? er * 2 : er * 2);
        g.fillOval(ex2 - er, ey2 - er, er * 2, er * 2);

        g.setColor(Color.WHITE);
        g.fillOval(ex1 - er + 1, ey1 - er + 1, 2, 2);
        g.fillOval(ex2 - er + 1, ey2 - er + 1, 2, 2);
    }

    private Direction getSnakeDirection() {
        Cell[] cells = snake.getBody().toArray(new Cell[0]);
        if (cells.length < 2) return Direction.RIGHT;
        int dx = cells[0].x - cells[1].x;
        int dy = cells[0].y - cells[1].y;
        for (Direction d : Direction.values()) {
            if (d.dx == dx && d.dy == dy) return d;
        }
        return Direction.RIGHT;
    }

    private void drawReadyOverlay(Graphics2D g) {
        drawOverlayBase(g);
        g.setFont(pixelFont(36f));
        g.setColor(SNAKE_HEAD);
        drawCentered(g, "SNAKE", H / 2 - 90);

        g.setFont(pixelFont(13f));
        g.setColor(new Color(160, 200, 170));
        drawCentered(g, "Use arrow keys to move", H / 2 - 50);
        drawCentered(g, "Eat food. Don't die.", H / 2 - 28);

        drawDifficultyPicker(g, H / 2 + 5);
        drawButton(g, "START", H / 2 + 75, btnStart, hoverStart);
    }

    private void drawGameOverOverlay(Graphics2D g) {
        drawOverlayBase(g);

        g.setFont(pixelFont(34f));
        g.setColor(FOOD_COLOR);
        drawCentered(g, "GAME OVER", H / 2 - 90);

        g.setFont(pixelFont(15f));
        g.setColor(SCORE_COLOR);
        drawCentered(g, "Score: " + score.getScore(), H / 2 - 45);
        g.setColor(HI_COLOR);
        drawCentered(g, "Best:  " + score.getHighScore(), H / 2 - 20);

        drawDifficultyPicker(g, H / 2 + 10);
        drawButton(g, "PLAY AGAIN", H / 2 + 80, btnRestart, hoverRestart);
    }

    private void drawPausedOverlay(Graphics2D g) {
        // Semi-transparent dark tint — lighter than full overlays so the game is still visible
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, W, H);

        // Pill badge
        int bw = 220, bh = 56, bx = (W - bw) / 2, by = H / 2 - 80;
        g.setColor(new Color(30, 30, 60, 220));
        g.fillRoundRect(bx, by, bw, bh, 18, 18);
        g.setColor(new Color(80, 255, 160));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(bx, by, bw, bh, 18, 18);
        g.setStroke(new BasicStroke(1f));

        g.setFont(pixelFont(28f));
        g.setColor(new Color(80, 255, 160));
        drawCentered(g, "PAUSED", H / 2 - 80 + 38);

        g.setFont(pixelFont(12f));
        g.setColor(new Color(140, 180, 155));
        drawCentered(g, "Press P or ESC to resume", H / 2 - 10);
    }

    private void drawDifficultyPicker(Graphics2D g, int centerY) {
        // Label
        g.setFont(pixelFont(11f));
        g.setColor(new Color(140, 170, 150));
        drawCentered(g, "DIFFICULTY", centerY - 16);

        // Three side-by-side buttons
        String[] labels = { "EASY", "NORMAL", "HARD" };
        Difficulty[] diffs = { Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD };
        Color[] colors = { new Color(60, 200, 90), new Color(80, 160, 255), new Color(255, 80, 80) };
        boolean[] hovers = { hoverEasy, hoverNormal, hoverHard };
        Rectangle[] rects = { btnEasy, btnNormal, btnHard };

        int bw = 90, bh = 34, gap = 10;
        int totalW = bw * 3 + gap * 2;
        int bx = (W - totalW) / 2;
        int by = centerY + 2;

        for (int i = 0; i < 3; i++) {
            boolean selected = difficulty == diffs[i];
            boolean hover = hovers[i];
            Color base = colors[i];

            // Fill
            Color fill = selected
                ? new Color(base.getRed(), base.getGreen(), base.getBlue(), 200)
                : hover
                    ? new Color(base.getRed(), base.getGreen(), base.getBlue(), 80)
                    : new Color(20, 20, 40, 180);
            g.setColor(fill);
            g.fillRoundRect(bx, by, bw, bh, 10, 10);

            // Border — bright if selected, dim otherwise
            g.setColor(selected ? base : new Color(base.getRed(), base.getGreen(), base.getBlue(), 100));
            g.setStroke(new BasicStroke(selected ? 2.5f : 1.5f));
            g.drawRoundRect(bx, by, bw, bh, 10, 10);
            g.setStroke(new BasicStroke(1f));

            // Label
            g.setFont(pixelFont(12f));
            g.setColor(selected ? Color.WHITE : new Color(base.getRed(), base.getGreen(), base.getBlue(), 200));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(labels[i], bx + (bw - fm.stringWidth(labels[i])) / 2, by + bh / 2 + fm.getAscent() / 2 - 2);

            rects[i].setBounds(bx, by, bw, bh);
            bx += bw + gap;
        }
    }

    private void drawDyingOverlay(Graphics2D g) {
        // Brief red screen flash that fades with the animation
        float alpha = (float)(DEATH_FRAMES - deathTick) / DEATH_FRAMES * 0.35f;
        g.setColor(new Color(1f, 0f, 0f, alpha));
        g.fillRect(0, 0, W, H);
    }

        private void drawOverlayBase(Graphics2D g) {
        g.setColor(OVERLAY_BG);
        g.fillRect(0, 0, W, H);
        // Vignette
        RadialGradientPaint v = new RadialGradientPaint(
            W / 2f, H / 2f, Math.max(W, H) * 0.75f,
            new float[]{0f, 1f},
            new Color[]{new Color(0,0,0,0), new Color(0,0,0,160)}
        );
        g.setPaint(v);
        g.fillRect(0, 0, W, H);
    }

    private void drawButton(Graphics2D g, String label, int centerY, Rectangle bounds, boolean hover) {
        FontMetrics fm = g.getFontMetrics(pixelFont(14f));
        int bw = fm.stringWidth(label) + 60;
        int bh = 42;
        int bx = (W - bw) / 2;
        int by = centerY - bh / 2;

        bounds.setBounds(bx, by, bw, bh);

        Color fill  = hover ? new Color(60, 200, 120, 230) : new Color(30, 100, 65, 200);
        Color border = hover ? BTN_HOVER : BTN_NORMAL;

        g.setColor(fill);
        g.fillRoundRect(bx, by, bw, bh, 12, 12);
        g.setColor(border);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(bx, by, bw, bh, 12, 12);
        g.setStroke(new BasicStroke(1f));

        g.setFont(pixelFont(14f));
        g.setColor(hover ? Color.WHITE : SCORE_COLOR);
        FontMetrics fm2 = g.getFontMetrics();
        g.drawString(label, bx + (bw - fm2.stringWidth(label)) / 2, by + bh / 2 + fm2.getAscent() / 2 - 2);
    }

    private void drawCentered(Graphics2D g, String text, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (W - fm.stringWidth(text)) / 2, y);
    }

    // ── Utilities ─────────────────────────────
    private Font pixelFont(float size) {
        return new Font("Monospaced", Font.BOLD, (int) size);
    }

    private Color blend(Color a, Color b, float t) {
        int r = (int) (a.getRed()   * (1 - t) + b.getRed()   * t);
        int gr= (int) (a.getGreen() * (1 - t) + b.getGreen() * t);
        int bl= (int) (a.getBlue()  * (1 - t) + b.getBlue()  * t);
        return new Color(clamp(r), clamp(gr), clamp(bl));
    }

    private int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    // ── Key handling ──────────────────────────
    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP,    KeyEvent.VK_W -> { if (state == State.PLAYING) snake.setDirection(Direction.UP); }
            case KeyEvent.VK_DOWN,  KeyEvent.VK_S -> { if (state == State.PLAYING) snake.setDirection(Direction.DOWN); }
            case KeyEvent.VK_LEFT,  KeyEvent.VK_A -> { if (state == State.PLAYING) snake.setDirection(Direction.LEFT); }
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> { if (state == State.PLAYING) snake.setDirection(Direction.RIGHT); }
            case KeyEvent.VK_P, KeyEvent.VK_ESCAPE -> {
                if (state == State.PLAYING || state == State.PAUSED) togglePause();
            }
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> {
                if (state == State.READY || state == State.GAME_OVER) startGame();
                else if (state == State.PAUSED) togglePause();
            }
        }
    }

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e)    {}
}
