import javax.swing.*;

// ─────────────────────────────────────────────
//  SnakeGame — Entry point
//  The only job of this class is to create the window and start the program.
//  All game logic and rendering lives in GamePanel.
// ─────────────────────────────────────────────
public class SnakeGame {
    public static void main(String[] args) {

        // invokeLater schedules the window creation on the Swing Event Dispatch Thread.
        // All Swing UI work must happen on that thread — this is the correct way to start it.
        SwingUtilities.invokeLater(() -> {

            JFrame frame = new JFrame("SNAKE");

            // Closes the whole program when the window's X button is pressed
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // Prevents resizing — the grid has a fixed pixel size and doesn't scale
            frame.setResizable(false);

            // GamePanel does everything: drawing, game loop, input handling
            GamePanel game = new GamePanel();
            frame.add(game);

            // pack() shrinks the window to exactly fit GamePanel's preferred size
            frame.pack();

            // Centre the window on the screen
            frame.setLocationRelativeTo(null);

            frame.setVisible(true);
        });
    }
}
