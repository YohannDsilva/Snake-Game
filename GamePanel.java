import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

// ─────────────────────────────────────────────
//  GamePanel — the heart of the entire program.
//  Responsible for: the game loop, all rendering, keyboard input, mouse input.
//
//  Extends JPanel    → can be added to the JFrame and drawn on with Graphics2D
//  ActionListener    → receives Timer ticks (one tick = one game loop step)
//  KeyListener       → receives keyboard events for movement and pause
// ─────────────────────────────────────────────
class GamePanel extends JPanel implements ActionListener, KeyListener {

    // ── Grid / window sizing ───────────────────
    // Everything is defined in terms of these constants so the layout scales cleanly.
    private static final int COLS = 28;          // number of grid columns
    private static final int ROWS = 22;          // number of grid rows
    private static final int CELL = 26;          // pixel size of each grid square
    private static final int HUD  = 64;          // pixel height of the score bar at the top
    private static final int W    = COLS * CELL; // total window width in pixels
    private static final int H    = ROWS * CELL + HUD; // total window height in pixels

    // ── Colour palette ─────────────────────────
    // All colours defined in one place — change a value here to retheme the game.
    private static final Color BG          = new Color(10,  10,  18);      // near-black background
    private static final Color GRID        = new Color(22,  24,  38);      // subtle grid lines
    private static final Color HUD_BG      = new Color(14,  14,  26);      // score bar background
    private static final Color SNAKE_HEAD  = new Color(80, 255, 160);      // bright green head
    private static final Color SNAKE_BODY  = new Color(40, 200, 110);      // body (slightly darker)
    private static final Color SNAKE_DARK  = new Color(20, 140,  70);      // tail (darkest)
    private static final Color FOOD_COLOR  = new Color(255,  80,  80);     // red apple
    private static final Color GOLD_COLOR  = new Color(255, 215,   0);     // golden apple
    private static final Color GOLD_GLOW   = new Color(255, 200,   0, 70); // gold glow (semi-transparent)
    private static final Color FOOD_GLOW   = new Color(255,  80,  80, 60); // red glow (semi-transparent)
    private static final Color SCORE_COLOR = new Color(200, 255, 220);     // score text colour
    private static final Color HI_COLOR    = new Color(255, 215,  80);     // high score text (gold)
    private static final Color OVERLAY_BG  = new Color(0,   0,   0, 170); // dark menu background
    private static final Color BTN_NORMAL  = new Color(80, 255, 160);      // button border
    private static final Color BTN_HOVER   = new Color(140, 255, 200);     // button border on hover

    // ── Game state machine ─────────────────────
    // The game is always in exactly one of these states.
    // Each state controls what gets drawn and which inputs are accepted.
    //   READY     = start screen, waiting for the player to press START
    //   PLAYING   = active game, timer is running
    //   PAUSED    = game frozen, overlay shown, timer stopped
    //   DYING     = death animation playing, deathTimer running
    //   GAME_OVER = final score shown, waiting for PLAY AGAIN
    enum State { READY, PLAYING, PAUSED, DYING, GAME_OVER }

    private static final int FOOD_COUNT = 5; // red apples visible on screen at once

    // ── Difficulty ─────────────────────────────
    // Each preset stores its tick interval (ms between game loop steps).
    // Lower delay = faster game.
    enum Difficulty {
        EASY(240), NORMAL(180), HARD(100);
        final int delay;
        Difficulty(int delay) { this.delay = delay; }
    }
    private Difficulty difficulty = Difficulty.NORMAL; // selected difficulty (default: Normal)

    // ── Core game objects ───────────────────────
    private State state = State.READY;
    private Snake snake;
    private final java.util.List<Food> foods = new java.util.ArrayList<>(); // the 5 active red apples
    private final ScoreBoard score = new ScoreBoard();
    private final Timer timer;       // fires actionPerformed() every tickDelay ms
    private int tickDelay = Difficulty.NORMAL.delay; // current interval, changes with difficulty and score

    // ── Button hit-boxes ───────────────────────
    // These Rectangle objects are updated every frame to match the drawn button positions.
    // Mouse clicks are tested against them in the MouseListener.
    private final Rectangle btnStart   = new Rectangle();
    private final Rectangle btnRestart = new Rectangle();
    private final Rectangle btnEasy    = new Rectangle();
    private final Rectangle btnNormal  = new Rectangle();
    private final Rectangle btnHard    = new Rectangle();

    // Hover flags — set to true when the mouse is over that button
    private boolean hoverStart = false, hoverRestart = false;
    private boolean hoverEasy  = false, hoverNormal  = false, hoverHard = false;

    // ── Animation state ────────────────────────
    private int flashTick = 0;               // counts down after eating food (brief orange tint on apple)
    private int deathTick = 0;               // counts up each frame during the death animation
    private static final int DEATH_FRAMES = 7; // total frames in the death animation
    private Timer deathTimer;                // separate timer that drives the death animation at 80ms/frame

    // ── Golden apple state ─────────────────────
    private final GoldenApple goldenApple = new GoldenApple();
    private static final int GOLDEN_SPAWN_TICKS   = 120; // game ticks between golden apple appearances
    private static final int GOLDEN_DESPAWN_TICKS = 60;  // game ticks before it vanishes if uneaten
    private int goldenSpawnCountdown  = GOLDEN_SPAWN_TICKS;
    private int goldenDespawnCountdown = 0;

    // ──────────────────────────────────────────
    //  CONSTRUCTOR
    //  Sets up the panel and registers all input listeners and timers.
    // ──────────────────────────────────────────
    GamePanel() {
        setPreferredSize(new Dimension(W, H)); // tells JFrame.pack() how big to make the window
        setBackground(BG);
        setFocusable(true);   // required for the panel to receive KeyEvents
        addKeyListener(this);

        // Main game loop timer — fires actionPerformed() repeatedly while the game runs
        timer = new Timer(Difficulty.NORMAL.delay, this);

        // Death animation timer — runs independently at 80ms/frame for DEATH_FRAMES frames,
        // then stops itself and switches state to GAME_OVER.
        deathTimer = new Timer(80, ev -> {
            deathTick++;
            if (deathTick >= DEATH_FRAMES) {
                deathTimer.stop();
                state = State.GAME_OVER;
            }
            repaint();
        });

        // Mouse click handler — checks each button's hit-box Rectangle
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (state == State.READY     && btnStart.contains(e.getPoint()))    startGame();
                if (state == State.GAME_OVER && btnRestart.contains(e.getPoint()))  startGame();
                // Difficulty buttons are clickable on both the start and game over screens
                if (state == State.READY || state == State.GAME_OVER) {
                    if (btnEasy.contains(e.getPoint()))   { difficulty = Difficulty.EASY;   repaint(); }
                    if (btnNormal.contains(e.getPoint())) { difficulty = Difficulty.NORMAL; repaint(); }
                    if (btnHard.contains(e.getPoint()))   { difficulty = Difficulty.HARD;   repaint(); }
                }
            }
        });

        // Mouse move handler — updates hover flags so buttons highlight when the cursor is over them
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                hoverStart   = btnStart.contains(e.getPoint());
                hoverRestart = btnRestart.contains(e.getPoint());
                hoverEasy    = btnEasy.contains(e.getPoint());
                hoverNormal  = btnNormal.contains(e.getPoint());
                hoverHard    = btnHard.contains(e.getPoint());
                repaint(); // redraw to reflect the new hover state
            }
        });
    }

    // ──────────────────────────────────────────
    //  PAUSE / RESUME
    //  Stops the game loop timer and shows the pause overlay,
    //  or restarts the timer and hides it.
    // ──────────────────────────────────────────
    private void togglePause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
            timer.stop();
        } else if (state == State.PAUSED) {
            state = State.PLAYING;
            timer.start();
            requestFocusInWindow(); // re-grab keyboard focus after any mouse clicks
        }
        repaint();
    }

    // ──────────────────────────────────────────
    //  START / RESTART
    //  Resets all game state and begins a fresh round.
    // ──────────────────────────────────────────
    private void startGame() {
        score.reset();
        tickDelay = difficulty.delay; // speed is determined by the selected difficulty
        timer.setDelay(tickDelay);

        snake = new Snake(COLS / 2, ROWS / 2); // spawn snake in the centre

        // Spawn 5 red apples, passing the growing list into each spawn call
        // so apples can't overlap each other
        foods.clear();
        for (int i = 0; i < FOOD_COUNT; i++) {
            Food f = new Food();
            f.spawn(COLS, ROWS, snake, foods);
            foods.add(f);
        }

        flashTick = 0;
        goldenApple.deactivate();           // golden apple starts off the board
        goldenSpawnCountdown   = GOLDEN_SPAWN_TICKS;
        goldenDespawnCountdown = 0;

        state = State.PLAYING;
        timer.start();
        requestFocusInWindow();
        repaint();
    }

    // ──────────────────────────────────────────
    //  GAME LOOP  (fires every tickDelay ms via the main Timer)
    //  This is the core update function called once per game tick.
    //  Order: move snake → check death → check food → update golden apple → repaint
    // ──────────────────────────────────────────
    @Override
    public void actionPerformed(ActionEvent e) {
        if (state != State.PLAYING) return; // ignore timer ticks when not in play

        if (flashTick > 0) flashTick--; // tick down the post-eat flash counter

        // Move the snake one step — returns false if it hit a wall or itself
        boolean alive = snake.move(COLS, ROWS);
        if (!alive) {
            // Transition to the death animation instead of jumping straight to GAME_OVER
            timer.stop();
            state = State.DYING;
            deathTick = 0;
            deathTimer.start();
            repaint();
            return;
        }

        // Check whether the head landed on any of the 5 red apples
        Cell head = snake.getHead();
        for (Food f : foods) {
            if (head.equals(f.position)) {
                snake.grow();                          // tail stays next tick → snake gets longer
                score.addPoint();                      // +10 points
                f.spawn(COLS, ROWS, snake, foods);     // respawn this apple somewhere new
                flashTick = 4;                         // brief orange tint on the new apple

                // Speed up slightly every 50 points, floored at 80ms
                if (score.getScore() % 50 == 0 && tickDelay > 80) {
                    tickDelay -= 6;
                    timer.setDelay(tickDelay);
                }
                break; // only one apple can be eaten per tick
            }
        }

        // ── Golden apple logic ───────────────────
        if (goldenApple.active) {
            goldenDespawnCountdown--;
            goldenApple.tick(1f / GOLDEN_DESPAWN_TICKS); // advance blink progress (0→1)

            if (goldenDespawnCountdown <= 0) {
                // Lifespan expired — remove without rewarding the player
                goldenApple.deactivate();
                goldenSpawnCountdown = GOLDEN_SPAWN_TICKS; // reset spawn timer
            } else if (snake.getHead().equals(goldenApple.position)) {
                // Snake ate it before it expired
                snake.grow();
                score.addGolden();             // +40 points
                goldenApple.deactivate();
                goldenSpawnCountdown = GOLDEN_SPAWN_TICKS;
            }
        } else {
            // Golden apple is not on the board — count down until next spawn
            goldenSpawnCountdown--;
            if (goldenSpawnCountdown <= 0) {
                java.util.List<Food> allFoods = new java.util.ArrayList<>(foods);
                allFoods.add(goldenApple); // include itself so spawn() avoids other apples
                goldenApple.activate(COLS, ROWS, snake, allFoods);
                goldenDespawnCountdown = GOLDEN_DESPAWN_TICKS;
            }
        }

        repaint(); // tell Swing to call paintComponent() on the next EDT cycle
    }

    // ──────────────────────────────────────────
    //  RENDERING — paintComponent
    //  Called by Swing whenever repaint() is triggered.
    //  Draws the entire frame from scratch every tick.
    //  Layers drawn in order so later ones appear on top.
    // ──────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0); // clears the previous frame
        Graphics2D g = (Graphics2D) g0;

        // Smooth edges on circles and text
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawHUD(g);   // score bar (always visible)
        drawGrid(g);  // background + grid lines (always visible)

        // Game objects only render when there's an active session
        if (state != State.READY) {
            drawFood(g);
            drawGoldenApple(g);
            drawSnake(g);
        }

        // Red screen flash — sits on top of the snake during death animation
        if (state == State.DYING) drawDyingOverlay(g);

        // Full-screen overlays — only one is active at a time
        if (state == State.READY)     drawReadyOverlay(g);
        if (state == State.PAUSED)    drawPausedOverlay(g);
        if (state == State.GAME_OVER) drawGameOverOverlay(g);
    }

    // Draws the score bar at the top of the window
    private void drawHUD(Graphics2D g) {
        g.setColor(HUD_BG);
        g.fillRect(0, 0, W, HUD);

        // Thin separator line at the bottom of the HUD
        g.setColor(SNAKE_DARK);
        g.fillRect(0, HUD - 2, W, 2);

        // Score (left side)
        g.setFont(pixelFont(11f));
        g.setColor(new Color(100, 130, 110));
        g.drawString("SCORE", 28, 24);
        g.setFont(pixelFont(22f));
        g.setColor(SCORE_COLOR);
        g.drawString(String.valueOf(score.getScore()), 28, 50);

        // High score (right side — right-aligned by measuring the text width first)
        g.setFont(pixelFont(11f));
        g.setColor(new Color(130, 120, 60));
        int bx = W - 28 - g.getFontMetrics(pixelFont(22f)).stringWidth(String.valueOf(score.getHighScore()));
        g.drawString("BEST", bx, 24);
        g.setFont(pixelFont(22f));
        g.setColor(HI_COLOR);
        g.drawString(String.valueOf(score.getHighScore()), bx, 50);

        // Game title centred in the HUD
        g.setFont(pixelFont(20f));
        g.setColor(SNAKE_HEAD);
        String title = "SNAKE";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (W - fm.stringWidth(title)) / 2, 42);
    }

    // Fills the board area with the background colour and draws the grid lines
    private void drawGrid(Graphics2D g) {
        int top = HUD;
        g.setColor(BG);
        g.fillRect(0, top, W, ROWS * CELL);

        g.setColor(GRID);
        for (int x = 0; x <= COLS; x++)
            g.drawLine(x * CELL, top, x * CELL, top + ROWS * CELL);   // vertical lines
        for (int y = 0; y <= ROWS; y++)
            g.drawLine(0, top + y * CELL, W, top + y * CELL);         // horizontal lines
    }

    // Draws all 5 red apples with a soft glow behind each one
    private void drawFood(Graphics2D g) {
        for (int i = 0; i < foods.size(); i++) {
            Food food = foods.get(i);
            if (food.position == null) continue;

            Cell f  = food.position;
            int px  = f.x * CELL;       // pixel x of the cell's top-left corner
            int py  = HUD + f.y * CELL; // pixel y, offset below the HUD
            int pad = 4;                // inset from cell edges

            // Radial glow — fades from a translucent red to fully transparent
            RadialGradientPaint glow = new RadialGradientPaint(
                px + CELL / 2f, py + CELL / 2f, CELL,
                new float[]{0f, 1f},
                new Color[]{FOOD_GLOW, new Color(0, 0, 0, 0)}
            );
            g.setPaint(glow);
            g.fillOval(px - CELL / 2, py - CELL / 2, CELL * 2, CELL * 2);

            // Apple circle — briefly turns orange (flashTick) immediately after being eaten/respawned
            g.setColor(flashTick > 0 && i == 0 ? new Color(255, 140, 80) : FOOD_COLOR);
            g.fillOval(px + pad, py + pad, CELL - pad * 2, CELL - pad * 2);

            // Small white shine spot in the top-left area of the apple
            g.setColor(new Color(255, 255, 255, 90));
            g.fillOval(px + pad + 3, py + pad + 3, (CELL - pad * 2) / 3, (CELL - pad * 2) / 3);
        }
    }

    // Draws the golden apple if it's currently active on the board
    private void drawGoldenApple(Graphics2D g) {
        if (!goldenApple.active || goldenApple.position == null) return;

        // In the last 25% of its lifespan, blink on/off every ~120ms as a warning
        float prog = goldenApple.despawnProgress;
        if (prog > 0.75f) {
            long now = System.currentTimeMillis();
            if ((now / 120) % 2 == 0) return; // returning early = invisible on this frame
        }

        Cell f  = goldenApple.position;
        int px  = f.x * CELL;
        int py  = HUD + f.y * CELL;
        int pad = 3;

        // Larger glow than the red apple
        RadialGradientPaint glow = new RadialGradientPaint(
            px + CELL / 2f, py + CELL / 2f, CELL * 1.2f,
            new float[]{0f, 1f},
            new Color[]{GOLD_GLOW, new Color(0, 0, 0, 0)}
        );
        g.setPaint(glow);
        g.fillOval(px - CELL / 2, py - CELL / 2, CELL * 2, CELL * 2);

        // Gold circle
        g.setColor(GOLD_COLOR);
        g.fillOval(px + pad, py + pad, CELL - pad * 2, CELL - pad * 2);

        // Slightly darker outline for visual definition
        g.setColor(new Color(200, 150, 0));
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(px + pad, py + pad, CELL - pad * 2, CELL - pad * 2);
        g.setStroke(new BasicStroke(1f));

        // Shine spot
        g.setColor(new Color(255, 255, 180, 160));
        g.fillOval(px + pad + 3, py + pad + 3, (CELL - pad * 2) / 3, (CELL - pad * 2) / 3);

        // Sparkle star at the top-right corner — a dot with two crossed lines
        int sx = px + CELL - 7, sy = py + 5;
        g.setColor(new Color(255, 255, 120, 200));
        g.fillOval(sx, sy, 4, 4);
        g.setColor(new Color(255, 255, 200, 150));
        g.drawLine(sx + 2, sy - 3, sx + 2, sy + 7); // vertical arm
        g.drawLine(sx - 3, sy + 2, sx + 7, sy + 2); // horizontal arm
    }

    // Draws the snake — rendered tail-to-head so the head segment appears on top
    private void drawSnake(Graphics2D g) {
        Deque<Cell> body  = snake.getBody();
        Cell[] cells      = body.toArray(new Cell[0]); // index 0 = head, last = tail

        boolean dying  = (state == State.DYING);
        boolean flashOn = dying && (deathTick % 2 == 0); // alternates each frame of the animation

        for (int i = cells.length - 1; i >= 0; i--) {
            Cell c  = cells[i];
            int px  = c.x * CELL;
            int py  = HUD + c.y * CELL;
            int pad = 2;
            boolean isHead = (i == 0);

            if (dying) {
                // Death animation: even frames = bright red, odd frames = near-black
                float t = (float) i / cells.length; // gradient along the body (0=head, 1=tail)
                if (flashOn) {
                    g.setColor(blend(new Color(255, 60, 60), new Color(120, 20, 20), t));
                } else {
                    g.setColor(blend(new Color(80, 20, 20), new Color(30, 10, 10), t));
                }
            } else if (isHead) {
                g.setColor(SNAKE_HEAD); // bright green head
            } else {
                // Normal body: gradients from bright green near the head to dark green at the tail
                float t = (float) i / cells.length;
                g.setColor(blend(SNAKE_BODY, SNAKE_DARK, t));
            }

            g.fillRoundRect(px + pad, py + pad, CELL - pad * 2, CELL - pad * 2, 8, 8);

            if (isHead) drawEyes(g, c); // add eyes on the head segment only
        }
    }

    // Draws two small eyes on the head, rotated to face the snake's current direction
    private void drawEyes(Graphics2D g, Cell head) {
        Direction d = snake.getBody().size() > 1 ? getSnakeDirection() : Direction.RIGHT;
        int px = head.x * CELL;
        int py = HUD + head.y * CELL;
        int er = 3; // eye radius

        int ex1, ey1, ex2, ey2;

        // Place the two eyes near the leading edge of the head based on direction
        switch (d) {
            case RIGHT -> { ex1 = px + CELL - 7; ey1 = py + 6;         ex2 = px + CELL - 7; ey2 = py + CELL - 9; }
            case LEFT  -> { ex1 = px + 4;         ey1 = py + 6;         ex2 = px + 4;         ey2 = py + CELL - 9; }
            case UP    -> { ex1 = px + 6;          ey1 = py + 4;         ex2 = px + CELL - 9;  ey2 = py + 4;        }
            default    -> { ex1 = px + 6;          ey1 = py + CELL - 7; ex2 = px + CELL - 9;  ey2 = py + CELL - 7; }
        }

        // Dark pupil
        g.setColor(new Color(10, 20, 15));
        g.fillOval(ex1 - er, ey1 - er, er * 2, er * 2);
        g.fillOval(ex2 - er, ey2 - er, er * 2, er * 2);

        // Small white highlight on each eye
        g.setColor(Color.WHITE);
        g.fillOval(ex1 - er + 1, ey1 - er + 1, 2, 2);
        g.fillOval(ex2 - er + 1, ey2 - er + 1, 2, 2);
    }

    // Determines which way the head is pointing by comparing it to the second body cell
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

    // ── Overlay screens ─────────────────────────

    // Start screen — shown before the first game
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

    // Game over screen — shown after death animation finishes
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
        drawDifficultyPicker(g, H / 2 + 10); // let the player change difficulty before retrying
        drawButton(g, "PLAY AGAIN", H / 2 + 80, btnRestart, hoverRestart);
    }

    // Pause overlay — lighter than full overlays so the board stays partially visible
    private void drawPausedOverlay(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, W, H);

        // Rounded pill badge centred on screen
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

    // Draws the three side-by-side Easy / Normal / Hard difficulty buttons
    private void drawDifficultyPicker(Graphics2D g, int centerY) {
        g.setFont(pixelFont(11f));
        g.setColor(new Color(140, 170, 150));
        drawCentered(g, "DIFFICULTY", centerY - 16);

        String[]     labels = { "EASY",          "NORMAL",            "HARD" };
        Difficulty[] diffs  = { Difficulty.EASY,  Difficulty.NORMAL,   Difficulty.HARD };
        Color[]      colors = { new Color(60,200,90), new Color(80,160,255), new Color(255,80,80) };
        boolean[]    hovers = { hoverEasy, hoverNormal, hoverHard };
        Rectangle[]  rects  = { btnEasy,   btnNormal,   btnHard };

        int bw = 90, bh = 34, gap = 10;
        int bx = (W - (bw * 3 + gap * 2)) / 2; // centre the three buttons as a group
        int by = centerY + 2;

        for (int i = 0; i < 3; i++) {
            boolean selected = difficulty == diffs[i];
            Color base = colors[i];

            // Fill: solid if selected, faint if hovered, nearly transparent otherwise
            Color fill = selected
                ? new Color(base.getRed(), base.getGreen(), base.getBlue(), 200)
                : hovers[i]
                    ? new Color(base.getRed(), base.getGreen(), base.getBlue(), 80)
                    : new Color(20, 20, 40, 180);
            g.setColor(fill);
            g.fillRoundRect(bx, by, bw, bh, 10, 10);

            // Border: bright and thick if selected, dim if not
            g.setColor(selected ? base : new Color(base.getRed(), base.getGreen(), base.getBlue(), 100));
            g.setStroke(new BasicStroke(selected ? 2.5f : 1.5f));
            g.drawRoundRect(bx, by, bw, bh, 10, 10);
            g.setStroke(new BasicStroke(1f));

            g.setFont(pixelFont(12f));
            g.setColor(selected ? Color.WHITE : new Color(base.getRed(), base.getGreen(), base.getBlue(), 200));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(labels[i], bx + (bw - fm.stringWidth(labels[i])) / 2, by + bh / 2 + fm.getAscent() / 2 - 2);

            rects[i].setBounds(bx, by, bw, bh); // update the hit-box to match drawn position
            bx += bw + gap;
        }
    }

    // Red screen flash drawn on top of the snake during the death animation.
    // Alpha fades from ~35% on the first frame down to 0% on the last.
    private void drawDyingOverlay(Graphics2D g) {
        float alpha = (float)(DEATH_FRAMES - deathTick) / DEATH_FRAMES * 0.35f;
        g.setColor(new Color(1f, 0f, 0f, alpha));
        g.fillRect(0, 0, W, H);
    }

    // Semi-transparent dark background shared by the start, pause, and game over overlays.
    // Also adds a vignette (darker at the edges) for polish.
    private void drawOverlayBase(Graphics2D g) {
        g.setColor(OVERLAY_BG);
        g.fillRect(0, 0, W, H);

        // Radial gradient — transparent in the centre, dark at the screen edges
        RadialGradientPaint v = new RadialGradientPaint(
            W / 2f, H / 2f, Math.max(W, H) * 0.75f,
            new float[]{0f, 1f},
            new Color[]{new Color(0,0,0,0), new Color(0,0,0,160)}
        );
        g.setPaint(v);
        g.fillRect(0, 0, W, H);
    }

    // Draws a single rounded button, centred horizontally at the given y position.
    // Updates the Rectangle hit-box so clicks can be detected against it.
    private void drawButton(Graphics2D g, String label, int centerY, Rectangle bounds, boolean hover) {
        FontMetrics fm = g.getFontMetrics(pixelFont(14f));
        int bw = fm.stringWidth(label) + 60; // wide enough to fit text with padding
        int bh = 42;
        int bx = (W - bw) / 2;
        int by = centerY - bh / 2;

        bounds.setBounds(bx, by, bw, bh);

        g.setColor(hover ? new Color(60, 200, 120, 230) : new Color(30, 100, 65, 200));
        g.fillRoundRect(bx, by, bw, bh, 12, 12);
        g.setColor(hover ? BTN_HOVER : BTN_NORMAL);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(bx, by, bw, bh, 12, 12);
        g.setStroke(new BasicStroke(1f));
        g.setFont(pixelFont(14f));
        g.setColor(hover ? Color.WHITE : SCORE_COLOR);
        FontMetrics fm2 = g.getFontMetrics();
        g.drawString(label, bx + (bw - fm2.stringWidth(label)) / 2, by + bh / 2 + fm2.getAscent() / 2 - 2);
    }

    // Draws text horizontally centred in the window at the given y coordinate
    private void drawCentered(Graphics2D g, String text, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (W - fm.stringWidth(text)) / 2, y);
    }

    // ── Utility methods ─────────────────────────

    // Returns a bold monospaced font at the requested size
    // Used for all in-game text to give that retro pixel-font feel
    private Font pixelFont(float size) {
        return new Font("Monospaced", Font.BOLD, (int) size);
    }

    // Linear interpolation between two colours.
    // t=0 → colour a,  t=1 → colour b,  values between → blended.
    // Used for the head-to-tail green gradient and the red death gradient.
    private Color blend(Color a, Color b, float t) {
        int r  = (int) (a.getRed()   * (1 - t) + b.getRed()   * t);
        int gr = (int) (a.getGreen() * (1 - t) + b.getGreen() * t);
        int bl = (int) (a.getBlue()  * (1 - t) + b.getBlue()  * t);
        return new Color(clamp(r), clamp(gr), clamp(bl));
    }

    // Clamps a colour channel value to the valid 0–255 range
    private int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    // ── Keyboard input ────────────────────────────
    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            // Movement — only accepted while the game is actively running
            case KeyEvent.VK_UP,    KeyEvent.VK_W -> { if (state == State.PLAYING) snake.setDirection(Direction.UP); }
            case KeyEvent.VK_DOWN,  KeyEvent.VK_S -> { if (state == State.PLAYING) snake.setDirection(Direction.DOWN); }
            case KeyEvent.VK_LEFT,  KeyEvent.VK_A -> { if (state == State.PLAYING) snake.setDirection(Direction.LEFT); }
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> { if (state == State.PLAYING) snake.setDirection(Direction.RIGHT); }

            // Pause — works when playing or already paused
            case KeyEvent.VK_P, KeyEvent.VK_ESCAPE -> {
                if (state == State.PLAYING || state == State.PAUSED) togglePause();
            }

            // Start / resume / restart
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> {
                if (state == State.READY || state == State.GAME_OVER) startGame();
                else if (state == State.PAUSED) togglePause();
            }
        }
    }

    // Required by KeyListener — not needed since we only act on key-down events
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e)    {}
}
